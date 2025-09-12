package org.trailence.trail.db;

import java.util.UUID;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;

import reactor.core.publisher.Mono;

public interface PublicTrackRepository extends ReactiveCrudRepository<PublicTrackEntity, UUID> {

	Mono<Void> deleteByTrailUuid(UUID trailUuid);
	
}
