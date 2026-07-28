package org.trailence.trail.db;

import java.util.List;
import java.util.Set;
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
	
	@Query("SELECT uuid FROM public_trails WHERE author = :author")
	Flux<UUID> findIdsByAuthor(String author);
	
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
	
	@Query("SELECT pt.slug, MAX(pt.updated_at) as updated_at, MAX(ptf.date) as latestFeedbackAt, MIN(pt.created_at) as created_at, ARRAY_AGG(DISTINCT l.lang ORDER BY l.lang) AS languages FROM public_trails pt LEFT JOIN public_trail_feedback ptf ON ptf.public_trail_uuid = pt.uuid LEFT JOIN LATERAL (SELECT pt.lang UNION SELECT jsonb_object_keys(COALESCE(pt.name_translations, '{}'::jsonb))) AS l(lang) ON TRUE GROUP BY pt.slug ORDER BY created_at ASC LIMIT :nb OFFSET :start")
	Flux<SlugWithDatesAndLanguages> slugsWithDatesAndLanguages(int nb, long offset);
	
	@Data
	@NoArgsConstructor
	public static class SlugWithDatesAndLanguages {
		private String slug;
		private long updatedAt;
		private Long latestFeedbackAt;
		private long createdAt;
		private List<String> languages;
	}
	
	@Query("SELECT uuid, name, description FROM public_trails WHERE uuid IN (:uuids)")
	Flux<TrailNameAndDescription> getTrailsNameAndDescription(Set<UUID> uuids);

	@Data
	@NoArgsConstructor
	public static class TrailNameAndDescription {
		private UUID uuid;
		private String name;
		private String description;
	}
	
	@Query("SELECT uuid FROM public_trails ORDER BY RANDOM() LIMIT :nb")
	Flux<String> searchExamples(int nb);
}
