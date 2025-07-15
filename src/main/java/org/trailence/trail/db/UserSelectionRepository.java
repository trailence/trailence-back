package org.trailence.trail.db;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

import reactor.core.publisher.Mono;

public interface UserSelectionRepository extends ReactiveCrudRepository<UserSelectionEntity, String> {

	@Query("SELECT * from user_selection WHERE email = :id FOR UPDATE")
	Mono<UserSelectionEntity> findByIdForUpdate(String id);
	
}
