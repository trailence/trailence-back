package org.trailence.trail.db;

import java.util.UUID;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface PublicTrailFeedbackReplyRepository extends ReactiveCrudRepository<PublicTrailFeedbackReplyEntity, UUID> {

	Mono<Void> deleteAllByReplyTo(UUID replyTo);
	
	Flux<PublicTrailFeedbackReplyEntity> findAllByReplyTo(UUID replyTo);
	
}
