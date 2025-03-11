package org.trailence.trail.db;

import java.util.Collection;
import java.util.UUID;

import org.trailence.global.db.UuidOwnerRepository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ShareRepository extends UuidOwnerRepository<ShareEntity> {

	Flux<ShareEntity> findAllByOwner(String owner);
	
	Mono<ShareEntity> findOneByUuidAndOwner(UUID uuid, String owner);
	
	Mono<Long> deleteAllByUuidInAndOwner(Collection<UUID> uuids, String owner);
	
}
