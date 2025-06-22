package org.trailence.trail.db;

import java.util.Collection;
import java.util.UUID;

import org.springframework.data.r2dbc.repository.Query;
import org.trailence.global.db.UuidOwnerRepository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface TrackRepository extends UuidOwnerRepository<TrackEntity> {
    
    @Query("SELECT uuid FROM tracks WHERE uuid IN (:uuids) AND owner = :owner")
    Flux<UUID> findExistingUuids(Collection<UUID> uuids, String owner);
    
    @Query("SELECT tracks.* FROM tracks LEFT JOIN trails ON trails.uuid = :trailUuid AND trails.owner = tracks.owner LEFT JOIN collections ON collections.uuid = trails.collection_uuid AND collections.owner = trails.owner WHERE tracks.uuid = :trackUuid AND tracks.owner = :owner AND collections.type = 'PUB_SUBMIT' AND (trails.original_track_uuid = tracks.uuid OR trails.current_track_uuid = tracks.uuid) LIMIT 1")
    Mono<TrackEntity> findTrackForReview(UUID trailUuid, UUID trackUuid, String owner);

}
