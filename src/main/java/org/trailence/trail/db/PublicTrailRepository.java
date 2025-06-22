package org.trailence.trail.db;

import java.util.UUID;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface PublicTrailRepository  extends ReactiveCrudRepository<PublicTrailEntity, UUID> {

	Mono<Boolean> existsBySlug(String slug);
	
	@Query("SELECT slug FROM public_trails WHERE slug LIKE :slug")
	Flux<String> findAllSlugsStartingWith(String slug);
	
	Mono<PublicTrailEntity> findOneBySlug(String slug);
}
