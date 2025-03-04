package org.trailence.quotas.db;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;

import reactor.core.publisher.Mono;

public interface PlanRepository extends ReactiveCrudRepository<PlanEntity, String> {

	Mono<PlanEntity> findByName(String name);
	
}
