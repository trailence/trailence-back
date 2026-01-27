package org.trailence.preferences.db;

import java.util.UUID;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface UserAvatarRepository extends ReactiveCrudRepository<UserAvatarEntity, String> {

	@Query("SELECT COUNT(*) FROM user_avatar WHERE new_file_id IS NOT NULL AND new_public = true")
	Mono<Long> countAvatarToReview();
	
	@Query("SELECT email FROM user_avatar WHERE new_file_id IS NOT NULL AND new_public = true ORDER BY new_file_submitted_at ASC LIMIT 25")
	Flux<String> getAvatarsToReview();

	@Query("SELECT email FROM user_avatar WHERE new_file_id IS NOT NULL AND new_public = true AND email <> :email ORDER BY new_file_submitted_at ASC LIMIT 25")
	Flux<String> getAvatarsToReviewExcept(String email);
	
	Mono<UserAvatarEntity> findByPublicUuidAndCurrentPublicTrue(UUID publicUuid);

}
