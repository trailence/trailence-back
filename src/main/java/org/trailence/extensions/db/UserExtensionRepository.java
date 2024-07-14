package org.trailence.extensions.db;

import java.util.UUID;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;

import reactor.core.publisher.Flux;

public interface UserExtensionRepository extends ReactiveCrudRepository<UserExtensionEntity, UUID> {

	Flux<UserExtensionEntity> findByEmail(String email);
	
}
