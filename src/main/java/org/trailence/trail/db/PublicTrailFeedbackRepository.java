package org.trailence.trail.db;

import java.util.UUID;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;

import reactor.core.publisher.Mono;

public interface PublicTrailFeedbackRepository extends ReactiveCrudRepository<PublicTrailFeedbackEntity, UUID> {

	Mono<PublicTrailFeedbackEntity> findOneByPublicTrailUuidAndEmailAndRateIsNotNull(UUID publicTrailUuid, String email);
	
	Mono<PublicTrailFeedbackEntity> findFirst1ByPublicTrailUuidAndEmailAndCommentIsNotNullOrderByDateDesc(UUID publicTrailUuid, String email);
	
}
