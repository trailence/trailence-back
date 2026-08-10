package org.trailence.trail.db;

import java.util.UUID;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

import reactor.core.publisher.Mono;

public interface SharedCollectionRepository extends ReactiveCrudRepository<SharedCollectionEntity, UUID> {
	
	Mono<Long> deleteByUuid(UUID uuid);
	
	@Query("SELECT c.* FROM shared_collection_members m INNER JOIN shared_collections c ON c.uuid = m.shared_collection_uuid WHERE m.uuid = :memberUuid AND m.owner = :memberOwner")
	Mono<SharedCollectionEntity> getSharedCollectionHavingMember(UUID memberUuid, String memberOwner);
	
}
