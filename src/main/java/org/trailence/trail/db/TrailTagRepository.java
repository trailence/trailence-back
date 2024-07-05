package org.trailence.trail.db;

import java.util.Set;
import java.util.UUID;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface TrailTagRepository extends ReactiveCrudRepository<TrailTagEntity, UUID> {

	Flux<TrailTagEntity> findAllByOwner(String owner);
	
	Mono<Void> deleteAllByTrailUuidInAndOwner(Set<UUID> uuids, String owner);
	
	Mono<Void> deleteAllByTagUuidInAndOwner(Set<UUID> uuids, String owner);
	
	Mono<Void> deleteByTagUuidAndTrailUuidAndOwner(UUID tagUuid, UUID trailUuid, String owner);
	
}
