package org.trailence.trail.db;

import java.util.Collection;
import java.util.UUID;

import org.trailence.global.db.UuidOwnerRepository;

import reactor.core.publisher.Flux;

public interface PhotoRepository extends UuidOwnerRepository<PhotoEntity> {

	Flux<PhotoEntity> findAllByTrailUuidInAndOwner(Collection<UUID> trailsUuids, String owner);
	
}
