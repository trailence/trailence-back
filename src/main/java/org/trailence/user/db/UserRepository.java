package org.trailence.user.db;

import java.util.List;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface UserRepository extends ReactiveCrudRepository<UserEntity, String> {

	Mono<UserEntity> findByEmail(String email);
	
	Mono<UserEntity> findByEmailAndPassword(String email, String password);

	Flux<UserEntity> findAllByEmailIn(List<String> emails);
	
	Mono<Void> deleteByEmail(String email);

}
