package org.trailence.trail.db;

import java.util.Collection;
import java.util.UUID;

import org.springframework.data.r2dbc.repository.Query;
import org.trailence.global.db.UuidOwnerRepository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface TrailRepository extends UuidOwnerRepository<TrailEntity> {

	Flux<TrailEntity> findAllByCollectionUuidInAndOwner(Collection<UUID> uuids, String owner);
	
	@Query("SELECT trails.* FROM trails LEFT JOIN collections ON collections.uuid = trails.collection_uuid AND collections.owner = trails.owner WHERE collections.type = 'PUB_SUBMIT' AND trails.owner <> :ownerToExclude ORDER BY trails.updated_at ASC LIMIT 100")
	Flux<TrailEntity> findTrailsToReview(String ownerToExclude);
	
	@Query("SELECT trails.* FROM trails LEFT JOIN collections ON collections.uuid = trails.collection_uuid AND collections.owner = trails.owner WHERE trails.uuid = :uuid AND trails.owner = :owner AND collections.type = 'PUB_SUBMIT' LIMIT 1")
	Mono<TrailEntity> findTrailToReview(UUID uuid, String owner);

}
