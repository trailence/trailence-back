package org.trailence.trail.db;

import java.util.Set;
import java.util.UUID;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.trailence.trail.db.PublicTrailFeedbackRepository.UuidAndTrailUuid;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface PublicTrailFeedbackReplyRepository extends ReactiveCrudRepository<PublicTrailFeedbackReplyEntity, UUID> {

	Mono<Void> deleteAllByReplyTo(UUID replyTo);
	
	Flux<PublicTrailFeedbackReplyEntity> findAllByReplyTo(UUID replyTo);
	
	@Query("SELECT public_trail_feedback.uuid, public_trail_feedback.public_trail_uuid FROM public_trail_feedback_reply LEFT JOIN public_trail_feedback ON public_trail_feedback.uuid = public_trail_feedback_reply.reply_to WHERE public_trail_feedback_reply.reviewed = FALSE AND public_trail_feedback_reply.reply_to NOT IN (:excludeReplyTo) ORDER BY public_trail_feedback_reply.date ASC LIMIT 25")
	Flux<UuidAndTrailUuid> getToReview(Set<UUID> excludeReplyTo);
	
}
