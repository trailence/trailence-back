package org.trailence.quotas.db;

import java.util.UUID;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface UserSubscriptionRepository extends ReactiveCrudRepository<UserSubscriptionEntity, UUID> {

	Flux<UserSubscriptionEntity> findAllByUserEmail(String email);
	
	Mono<UserSubscriptionEntity> findByUserEmailAndUuid(String email, UUID uuid);
	
	Mono<Long> countByPlanName(String planName);
	
}
