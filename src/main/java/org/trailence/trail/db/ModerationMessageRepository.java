package org.trailence.trail.db;

import java.util.Collection;
import java.util.UUID;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ModerationMessageRepository extends ReactiveCrudRepository<ModerationMessageEntity, String> {

	Mono<Void> deleteAllByUuidInAndOwnerAndMessageType(Collection<UUID> uuids, String owner, String messageType);
	
	Mono<ModerationMessageEntity> findOneByUuidAndOwnerAndMessageType(UUID uuid, String owner, String messageType);
	
	Flux<ModerationMessageEntity> findAllByUuidInAndOwnerAndMessageType(Collection<UUID> uuids, String owner, String messageType);
	
	@Query("SELECT * FROM moderation_messages WHERE message_type = :type LIMIT 25")
	Flux<ModerationMessageEntity> getRemoveRequests(String type);

	@Query("SELECT * FROM moderation_messages WHERE message_type = :type AND author <> :excludeAuthor LIMIT 25")
	Flux<ModerationMessageEntity> getRemoveRequestsNotFrom(String type, String excludeAuthor);
	
	Flux<ModerationMessageEntity> findAllByUuidInAndMessageType(Collection<UUID> uuids, String messageType);
	
	Mono<Void> deleteAllByUuidInAndMessageType(Collection<UUID> uuids, String messageType);

	@Query("SELECT COUNT(*) FROM moderation_messages WHERE message_type = :type")
	Mono<Long> countRemoveRequests(String type);

	@Query("SELECT COUNT(*) FROM moderation_messages WHERE message_type = :type AND owner <> :emailToExclude")
	Mono<Long> countRemoveRequests(String type, String emailToExclude);
	
}
