package org.trailence.auth.db;

import java.util.UUID;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface UserKeyRepository extends ReactiveCrudRepository<UserKeyEntity, UUID> {

	Mono<UserKeyEntity> findByIdAndEmail(UUID id, String email);
	
	Flux<UserKeyEntity> findByEmail(String email);
	
	Mono<Void> deleteAllByEmail(String email);
	
}
