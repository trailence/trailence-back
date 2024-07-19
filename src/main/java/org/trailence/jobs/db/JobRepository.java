package org.trailence.jobs.db;

import java.util.UUID;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;

import reactor.core.publisher.Mono;

public interface JobRepository extends ReactiveCrudRepository<JobEntity, UUID> {

	Mono<JobEntity> findFirstByNextRetryAtLessThanOrderByNextRetryAtAsc(long now);
	
	Mono<Void> deleteAllByExpiresAtLessThan(long now);
	
}
