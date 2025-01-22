package org.trailence.trail.db;

import java.util.Collection;
import java.util.UUID;

import org.springframework.data.r2dbc.repository.Query;
import org.trailence.global.db.UuidOwnerRepository;

import reactor.core.publisher.Flux;

public interface TrackRepository extends UuidOwnerRepository<TrackEntity> {
    
    @Query("SELECT uuid FROM tracks WHERE uuid IN (:uuids) AND owner = :owner")
    Flux<UUID> findExistingUuids(Collection<UUID> uuids, String owner);

}
