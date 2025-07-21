package org.trailence.trail.db;

import java.util.UUID;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

import lombok.Data;
import lombok.NoArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface PublicTrailFeedbackRepository extends ReactiveCrudRepository<PublicTrailFeedbackEntity, UUID> {

	Mono<PublicTrailFeedbackEntity> findOneByPublicTrailUuidAndEmailAndRateIsNotNull(UUID publicTrailUuid, String email);
	
	Mono<PublicTrailFeedbackEntity> findFirst1ByPublicTrailUuidAndEmailAndCommentIsNotNullOrderByDateDesc(UUID publicTrailUuid, String email);
	
	@Query("SELECT uuid, public_trail_uuid FROM public_trail_feedback WHERE reviewed = FALSE ORDER BY date ASC LIMIT 25")
	Flux<UuidAndTrailUuid> getToReview();
	
	@Data
	@NoArgsConstructor
	public static class UuidAndTrailUuid {
		private UUID uuid;
		private UUID publicTrailUuid;
	}
	
}
