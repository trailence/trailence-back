package org.trailence.trail.db;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.r2dbc.repository.Query;
import org.trailence.global.db.UuidOwnerRepository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface TrailCollectionRepository extends UuidOwnerRepository<TrailCollectionEntity> {
	
    @Query("SELECT uuid FROM collections WHERE uuid IN (:uuids) AND owner = :owner")
    Flux<UUID> findExistingUuids(Collection<UUID> uuids, String owner);
    
    @Query("SELECT uuid FROM collections WHERE uuid IN (:uuids) AND owner = :owner AND type NOT IN ('PUB_DRAFT','PUB_SUBMIT','PUB_REJECT')")
    Flux<UUID> findExistingUuidsNotPublication(Collection<UUID> uuids, String owner);
    
    Flux<TrailCollectionEntity> findAllByOwner(String owner);
    
    @Query("SELECT * FROM collections WHERE type = CAST(:type AS collection_type) AND owner = :owner LIMIT 1")
    Mono<TrailCollectionEntity> findOneByTypeAndOwner(String type, String owner);
    
    @Query("SELECT * FROM collections WHERE type = CAST(:type AS collection_type) AND owner = :owner")
    Flux<TrailCollectionEntity> findAllByTypeAndOwner(String type, String owner);
    
    @Query("SELECT * FROM collections WHERE uuid IN (:uuids) AND owner = :owner AND type <> 'MY_TRAILS'")
    Flux<TrailCollectionEntity> findDeletables(Set<UUID> uuids, String owner);

}
