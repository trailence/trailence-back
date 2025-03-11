package org.trailence.trail.db;

import java.util.List;
import java.util.UUID;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ShareRecipientRepository extends ReactiveCrudRepository<ShareRecipientEntity, Long> {

	Flux<ShareRecipientEntity> findAllByRecipient(String recipient);
	
	Mono<Long> countByUuidAndOwner(UUID uuid, String owner);
	
	Flux<ShareRecipientEntity> findAllByUuidAndOwner(UUID uuid, String owner);
	
	Flux<ShareRecipientEntity> findAllByUuidAndOwnerAndRecipientIn(UUID uuid, String owner, List<String> recipients);
	
}
