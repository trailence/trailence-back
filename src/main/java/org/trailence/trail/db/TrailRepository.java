package org.trailence.trail.db;

import java.util.Collection;
import java.util.UUID;

import org.trailence.global.db.UuidOwnerRepository;

import reactor.core.publisher.Flux;

public interface TrailRepository extends UuidOwnerRepository<TrailEntity> {

	Flux<TrailEntity> findAllByCollectionUuidInAndOwner(Collection<UUID> uuids, String owner);

}
