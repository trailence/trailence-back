package org.trailence.user.db;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;

import reactor.core.publisher.Mono;

public interface UserRepository extends ReactiveCrudRepository<UserEntity, String> {

	Mono<UserEntity> findByEmailAndPassword(String email, String password);
	
}
