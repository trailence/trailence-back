package org.trailence.jobs;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.trailence.global.TrailenceUtils;
import org.trailence.jobs.db.JobEntity;
import org.trailence.jobs.db.JobRepository;

import io.r2dbc.postgresql.codec.Json;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class JobService {
	
	private final JobRepository repo;
	private final R2dbcEntityTemplate r2dbc;
	
	@SuppressWarnings("java:S6813") // autowired
	@Lazy @Autowired
	private List<Job> jobs;
	
	private AtomicBoolean running = new AtomicBoolean(false);
	
	@Scheduled(initialDelayString = "${trailence.jobs.initialDelay:60}", fixedDelayString = "${trailence.jobs.delay:60}", timeUnit = TimeUnit.SECONDS)
	public void launch() {
		if (!running.compareAndSet(false, true)) return;
		execute()
		.doFinally(s -> running.set(false))
		.checkpoint("Job processing")
		.subscribe();
	}
	
	private Mono<Void> execute() {
		return repo.deleteAllByExpiresAtLessThan(System.currentTimeMillis())
			.then(executeLoop());
	}
	
	private Mono<Void> executeLoop() {
		return executeNext()
			.flatMap(job -> execute());
	}
	
	private Mono<JobEntity> executeNext() {
		return repo.findFirstByNextRetryAtLessThanOrderByNextRetryAtAsc(System.currentTimeMillis())
		.flatMap(entity -> {
			Optional<Job> job = jobs.stream().filter(j -> j.getType().equals(entity.getType())).findAny();
			Mono<Job.Result> result;
			if (job.isEmpty()) {
				log.error("Unknown job type {}, retry in 15 minutes", entity.getType());
				result = Mono.just(new Job.Result(entity.getRetry() < 100 ? System.currentTimeMillis() + 15 * 60 * 1000 : null));
			} else {
				log.info("Executing job {}", entity.getType());
				result = job.get().execute(entity.getData(), entity.getRetry()).checkpoint("Job " + entity.getType());
			}
			return result.flatMap(r -> {
				if (r.retryAt == null) {
					log.info("Job {} succeed", entity.getType());
					return repo.delete(entity).thenReturn(entity);
				} else {
					log.info("Job {} failed", entity.getType());
					entity.setRetry(entity.getRetry() + 1);
					entity.setNextRetryAt(r.retryAt);
					return repo.save(entity);
				}
			});
		});
	}
	
	public Mono<Void> createJob(String type, Object data) {
		return Mono.defer(() -> {
			Optional<Job> optJob = jobs.stream().filter(j -> j.getType().equals(type)).findAny();
			if (optJob.isEmpty()) return Mono.error(new RuntimeException("Unknown job " + type));
			Job job = optJob.get();
			try {
				JobEntity entity = new JobEntity(
					UUID.randomUUID(),
					type,
					System.currentTimeMillis(),
					System.currentTimeMillis() + job.getInitialDelayMillis(),
					1,
					System.currentTimeMillis() + job.getExpirationDelayMillis(),
					Json.of(TrailenceUtils.mapper.writeValueAsString(data))
				);
				return r2dbc.insert(entity).then();
			} catch (Exception e) {
				return Mono.error(e);
			}
		});
	}

}
