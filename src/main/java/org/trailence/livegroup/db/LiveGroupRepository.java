package org.trailence.livegroup.db;

import java.util.UUID;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface LiveGroupRepository extends ReactiveCrudRepository<LiveGroupEntity, UUID> {
	
	@Query("SELECT * FROM live_groups WHERE uuid IN (SELECT DISTINCT group_uuid FROM live_group_members WHERE member_id = :memberId)")
	Flux<LiveGroupEntity> findAllForMemberId(String memberId);
	
	Mono<LiveGroupEntity> findOneBySlugAndOwner(String slug, String owner);
	
	Mono<LiveGroupEntity> findOneBySlug(String slug);
	
	@Query("SELECT uuid FROM live_groups WHERE started_at < :maxStartedAt ORDER BY started_at ASC LIMIT 25")
	Flux<UUID> findExpired(long maxStartedAt);
	
	Mono<Long> countByOwner(String owner);
	
}
