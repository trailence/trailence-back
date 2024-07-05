package org.trailence.auth.db;

import java.util.UUID;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;

import reactor.core.publisher.Mono;

public interface UserKeyRepository extends ReactiveCrudRepository<UserKeyEntity, UUID> {

	Mono<UserKeyEntity> findByIdAndEmail(UUID id, String email);
	
}
