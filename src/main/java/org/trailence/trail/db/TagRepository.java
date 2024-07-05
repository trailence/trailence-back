package org.trailence.trail.db;

import java.util.Collection;
import java.util.UUID;

import org.trailence.global.db.UuidOwnerRepository;

import reactor.core.publisher.Flux;

public interface TagRepository extends UuidOwnerRepository<TagEntity> {

	Flux<TagEntity> findAllByParentUuidAndOwner(UUID parentUuid, String owner);
	
	Flux<TagEntity> findAllByOwner(String owner);
	
	Flux<TagEntity> findAllByCollectionUuidInAndOwner(Collection<UUID> collectionUuids, String owner);
	
}
