package org.trailence.trail.db;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.r2dbc.repository.Query;
import org.trailence.global.db.UuidOwnerRepository;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface SharedCollectionMemberRepository extends UuidOwnerRepository<SharedCollectionMemberEntity> {
	
	public Flux<SharedCollectionMemberEntity> findAllBySharedCollectionUuid(UUID uuid);
	public Flux<SharedCollectionMemberEntity> findAllBySharedCollectionUuidIn(Collection<UUID> uuid);
	
	public Flux<SharedCollectionMemberEntity> findAllByOwner(String owner);
	
	public Mono<SharedCollectionMemberEntity> findOneBySharedCollectionUuidAndOwner(UUID uuid, String owner);
	
	public Mono<Void> deleteBySharedCollectionUuid(UUID uuid);
	
	@Query("SELECT c.uuid as col_uuid, c.owner as col_owner, m.uuid as member_uuid FROM shared_collection_members m JOIN shared_collections c ON c.uuid = m.shared_collection_uuid WHERE m.owner = :caller AND m.uuid = :uuid")
	public Mono<SharedCollectionInfo> getInfoByCallerAndUuid(String caller, UUID uuid);
	
	@Query("SELECT c.uuid as col_uuid, c.owner as col_owner, m.uuid as member_uuid FROM shared_collection_members m JOIN shared_collections c ON c.uuid = m.shared_collection_uuid WHERE m.owner = :caller AND m.uuid IN (:uuids)")
	public Flux<SharedCollectionInfo> getInfoByCallerAndUuidIn(String caller, Set<UUID> uuids);
	
	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class SharedCollectionInfo {
		private UUID colUuid;
		private String colOwner;
		private UUID memberUuid;
	}

}
