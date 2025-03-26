package org.trailence.jobs;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
	
	@Value("${trailence.jobs.delay:60}")
	private long delay = 60;
	
	@SuppressWarnings("java:S6813") // autowired
	@Lazy @Autowired
	private List<Job> jobs;
	
	private AtomicBoolean running = new AtomicBoolean(false);
	private long lastCleaning = 0;
	
	@Scheduled(initialDelayString = "${trailence.jobs.initialDelay:60}", fixedDelayString = "${trailence.jobs.delay:60}", timeUnit = TimeUnit.SECONDS)
	public void launch() {
		if (!running.compareAndSet(false, true)) return;
		execute(System.currentTimeMillis() - lastCleaning > 5L * 60000, System.currentTimeMillis())
		.doFinally(s -> running.set(false))
		.checkpoint("Job processing")
		.subscribe();
	}
	
	private Mono<Void> execute(boolean cleanExpired, long startTime) {
		Mono<Void> clean = cleanExpired ? repo.deleteAllByExpiresAtLessThan(System.currentTimeMillis()) : Mono.empty();
		return clean.then(Mono.defer(() -> executeLoop(startTime)));
	}
	
	private Mono<Void> executeLoop(long startTime) {
		return executeNext()
		.flatMap(processed -> {
			if (!processed.booleanValue() || System.currentTimeMillis() - startTime >= delay * 1000) return Mono.empty();
			return execute(false, startTime);
		});
	}
	
	private Mono<Boolean> executeNext() {
		return repo.findFirstByNextRetryAtLessThanOrderByPriorityAscNextRetryAtAsc(System.currentTimeMillis())
		.flatMap(entity ->
			executeJob(entity)
			.flatMap(result -> {
				log.info("Job {} {} - id {}", entity.getType(), result.success ? "succeed" : "failed", entity.getId());
				if (result.retryAt == null) {
					return repo.delete(entity).thenReturn(true);
				} else {
					entity.setRetry(entity.getRetry() + 1);
					entity.setNextRetryAt(result.retryAt);
					return repo.save(entity).thenReturn(true);
				}
			})
			.switchIfEmpty(Mono.just(true))
		)
		.switchIfEmpty(Mono.just(false));
	}
	
	private Mono<Job.Result> executeJob(JobEntity entity) {
		Optional<Job> job = jobs.stream().filter(j -> j.getType().equals(entity.getType())).findAny();
		if (job.isEmpty()) {
			log.error("Unknown job type {}, retry in 15 minutes", entity.getType());
			return Mono.just(new Job.Result(false, entity.getRetry() < 200 ? System.currentTimeMillis() + 15 * 60 * 1000 : null));
		}
		Long later = job.get().acceptNewJob(entity);
		if (later != null) {
			log.info("Job {} delayed by {}", entity.getType(), later);
			entity.setNextRetryAt(System.currentTimeMillis() + later);
			return repo.save(entity).then(Mono.empty());
		}
		log.info("Executing job {} - {}", entity.getType(), entity.getId());
		return job.get().execute(entity.getData(), entity.getRetry()).checkpoint("Job " + entity.getType());
	}
	
	public Mono<Void> createJob(String type, int priority, Object data) {
		return Mono.defer(() -> {
			Optional<Job> optJob = jobs.stream().filter(j -> j.getType().equals(type)).findAny();
			if (optJob.isEmpty()) return Mono.error(new RuntimeException("Unknown job " + type));
			Job job = optJob.get();
			try {
				JobEntity entity = new JobEntity(
					UUID.randomUUID(),
					type,
					priority,
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
