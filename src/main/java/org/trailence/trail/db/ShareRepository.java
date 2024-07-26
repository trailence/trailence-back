package org.trailence.trail.db;

import java.util.Collection;
import java.util.UUID;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ShareRepository extends ReactiveCrudRepository<ShareEntity, Long> {

	Flux<ShareEntity> findAllByFromEmailOrToEmail(String fromEmail, String toEmail);
	
	Mono<ShareEntity> findOneByUuidAndFromEmail(UUID uuid, String fromEmail);
	
	Mono<Void> deleteAllByUuidInAndFromEmail(Collection<UUID> uuids, String fromEmail);
	
}
