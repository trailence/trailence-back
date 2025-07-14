package org.trailence.trail.db;

import java.util.UUID;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.trailence.trail.dto.MyPublicTrail;

import lombok.Data;
import lombok.NoArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface PublicTrailRepository  extends ReactiveCrudRepository<PublicTrailEntity, UUID> {

	Mono<Boolean> existsBySlug(String slug);
	
	@Query("SELECT slug FROM public_trails WHERE slug LIKE :slug")
	Flux<String> findAllSlugsStartingWith(String slug);
	
	Mono<PublicTrailEntity> findOneBySlug(String slug);
	
	@Query("SELECT uuid as public_uuid, author_uuid as private_uuid FROM public_trails WHERE author = :author")
	Flux<MyPublicTrail> findMyPublicTrails(String author);
	
	@Query("SELECT author, name FROM public_trails WHERE uuid = :uuid LIMIT 1")
	Mono<AuthorAndName> getAuthorAndName(UUID uuid);
	
	@Data
	@NoArgsConstructor
	public static class AuthorAndName {
		private String author;
		private String name;
	}
	
	@Query("SELECT uuid FROM public_trails WHERE author_uuid = :uuid AND author = :author")
	Mono<UUID> getPublicUuidFromPrivate(UUID uuid, String author);
	
	Mono<PublicTrailEntity> findFirst1ByAuthorAndAuthorUuid(String auther, UUID authorUuid);
	
	@Query("SELECT * FROM public_trails ORDER BY RANDOM() LIMIT 200")
	Flux<PublicTrailEntity> random();
	
	@Query("SELECT slug, updated_at FROM public_trails")
	Flux<SlugAndDate> allSlugs();
	
	@Data
	@NoArgsConstructor
	public static class SlugAndDate {
		private String slug;
		private long updatedAt;
	}
	
}
