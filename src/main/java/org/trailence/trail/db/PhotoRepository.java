package org.trailence.trail.db;

import java.util.Collection;
import java.util.UUID;

import org.springframework.data.r2dbc.repository.Query;
import org.trailence.global.db.UuidOwnerRepository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface PhotoRepository extends UuidOwnerRepository<PhotoEntity> {

	Flux<PhotoEntity> findAllByTrailUuidInAndOwner(Collection<UUID> trailsUuids, String owner);
	
	@Query("SELECT photos.* FROM photos LEFT JOIN trails ON trails.uuid = photos.trail_uuid AND trails.owner = photos.owner LEFT JOIN collections ON collections.uuid = trails.collection_uuid AND collections.owner = trails.owner WHERE photos.uuid = :uuid AND photos.owner = :owner AND collections.type = 'PUB_SUBMIT'")
	Mono<PhotoEntity> findByUuidAndOwnerFromReview(UUID photoUuid, String owner);
	
}
