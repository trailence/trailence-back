package org.trailence.auth.db;

import java.util.UUID;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface UserKeyRepository extends ReactiveCrudRepository<UserKeyEntity, UUID> {

	Mono<UserKeyEntity> findByIdAndEmail(UUID id, String email);
	
	Flux<UserKeyEntity> findByEmail(String email);
	
	@Query("SELECT DISTINCT email FROM user_keys WHERE last_usage > :since LIMIT :max")
	Flux<String> findAllByLastUsage(long since, int max);
	
	Mono<Void> deleteAllByEmail(String email);
	
}
