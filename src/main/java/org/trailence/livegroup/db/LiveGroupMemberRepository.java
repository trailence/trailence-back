package org.trailence.livegroup.db;

import java.util.Collection;
import java.util.UUID;

import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface LiveGroupMemberRepository extends ReactiveCrudRepository<LiveGroupMemberEntity, UUID> {
	
	Flux<LiveGroupMemberEntity> findAllByGroupUuidIn(Collection<UUID> groupUuids);
	
	@Query("UPDATE live_group_members SET last_position_lat = :lat, last_position_lon = :lon, last_position_at = :at WHERE member_id = :memberId")
	@Modifying
	Mono<Void> updatePosition(Long lat, Long lon, Long at, String memberId);
	
	Mono<LiveGroupMemberEntity> findOneByGroupUuidAndMemberId(UUID groupUuid, String memberId);
	
	Mono<Void> deleteAllByGroupUuid(UUID groupUuid);
	Mono<Void> deleteAllByGroupUuidIn(Collection<UUID> groupUuids);
}
