package org.trailence.trail.db;

import java.util.Collection;
import java.util.UUID;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface TrailLinkRepository extends ReactiveCrudRepository<TrailLinkEntity, UUID> {

	Mono<TrailLinkEntity> findOneByAuthorAndAuthorUuid(String author, UUID authorUuid);
	
	Flux<TrailLinkEntity> findAllByAuthor(String author);
	
	Mono<Void> deleteAllByAuthorUuidInAndAuthor(Collection<UUID> uuids, String author);
	
}
