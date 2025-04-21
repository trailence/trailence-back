package org.trailence.contact.db;

import java.util.UUID;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ContactMessageRepository extends ReactiveCrudRepository<ContactMessageEntity, UUID> {

	Mono<Long> countByIsRead(boolean isRead);
	
	Mono<Long> countByEmailAndSentAtGreaterThan(String email, long minTimestamp);
	
	Flux<ContactMessageEntity> findAllBySentAtGreaterThanAndIsRead(long minTimestamp, boolean isRead);
	
}
