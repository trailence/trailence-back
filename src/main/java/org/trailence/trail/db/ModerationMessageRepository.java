package org.trailence.trail.db;

import java.util.Collection;
import java.util.UUID;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ModerationMessageRepository extends ReactiveCrudRepository<ModerationMessageEntity, String> {

	Mono<Void> deleteAllByUuidInAndOwner(Collection<UUID> uuids, String owner);
	
	Mono<ModerationMessageEntity> findOneByUuidAndOwner(UUID uuid, String owner);
	
	Flux<ModerationMessageEntity> findAllByUuidInAndOwner(Collection<UUID> uuids, String owner);
	
}
