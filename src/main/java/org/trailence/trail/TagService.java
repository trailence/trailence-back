package org.trailence.trail;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.trailence.global.TrailenceUtils;
import org.trailence.global.db.BulkGetUpdates;
import org.trailence.global.db.BulkUtils;
import org.trailence.global.db.DbUtils;
import org.trailence.global.dto.UpdateResponse;
import org.trailence.global.dto.Versioned;
import org.trailence.global.exceptions.BadRequestException;
import org.trailence.global.exceptions.NotFoundException;
import org.trailence.global.exceptions.ValidationUtils;
import org.trailence.global.rest.AuthDetails;
import org.trailence.quotas.QuotaService;
import org.trailence.trail.db.SharedCollectionMemberEntity;
import org.trailence.trail.db.SharedCollectionMemberRepository;
import org.trailence.trail.db.SharedCollectionMemberRepository.SharedCollectionInfo;
import org.trailence.trail.db.SharedCollectionRepository;
import org.trailence.trail.db.TagEntity;
import org.trailence.trail.db.TagRepository;
import org.trailence.trail.db.TrailCollectionRepository;
import org.trailence.trail.dto.Tag;
import org.trailence.trail.exceptions.CollectionNotFound;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuple3;
import reactor.util.function.Tuple4;
import reactor.util.function.Tuples;

@Service
@RequiredArgsConstructor
@Slf4j
public class TagService {

	private final TagRepository repo;
	private final TrailCollectionRepository collectionRepo;
	private final R2dbcEntityTemplate r2dbc;
	private final ShareService shareService;
	private final QuotaService quotaService;
	private final TrailTagService trailTagService;
	private final SharedCollectionMemberRepository sharedCollectionMemberRepo;
	private final SharedCollectionRepository sharedCollectionRepo;
	
	@Autowired @Lazy @SuppressWarnings("java:S6813")
	private TagService self;
	
	@SuppressWarnings("java:S3776")
	public Mono<List<Tag>> bulkCreate(List<Tag> dtos, Authentication auth) {
		String user = TrailenceUtils.email(auth);
		List<Tag> validOwned = new LinkedList<>();
		Set<UUID> ownedCollectionsUuids = new HashSet<>();
		List<Tag> validSharedCollections = new LinkedList<>();
		Set<UUID> sharedCollectionsUuids = new HashSet<>();
		List<Throwable> errors = new LinkedList<>();
		for (var dto : dtos) {
			try {
				validateCreate(dto);
				if (SharedCollectionUtils.isSharedCollectionOwner(dto.getOwner())) {
					if (!dto.getCollectionUuid().equals(SharedCollectionUtils.getSharedCollectionUuid(dto.getOwner()).toString()))
						throw new BadRequestException("collectionUuid does not match owner");
					validSharedCollections.add(dto);
					sharedCollectionsUuids.add(UUID.fromString(dto.getCollectionUuid()));
				} else {
					validOwned.add(dto);
					ownedCollectionsUuids.add(UUID.fromString(dto.getCollectionUuid()));
				}
			} catch (Exception e) {
				errors.add(e);
			}
		}
		if (validOwned.isEmpty() && validSharedCollections.isEmpty()) {
			if (errors.isEmpty()) return Mono.just(List.of());
			return Mono.error(errors.getFirst());
		}
		Mono<Tuple2<List<Tag>, List<Throwable>>> createdOwned;
		if (!validOwned.isEmpty()) {
			createdOwned = collectionRepo.findExistingUuidsNotPublication(ownedCollectionsUuids, user).collectList()
			.flatMap(existingCollections -> {
				Set<UUID> uuids = new HashSet<>();
				for (var it = validOwned.iterator(); it.hasNext(); ) {
					var dto = it.next();
					if (existingCollections.stream().noneMatch(uuid -> uuid.toString().equals(dto.getCollectionUuid()))) {
						errors.add(new CollectionNotFound(dto.getCollectionUuid()));
						it.remove();
					} else {
						uuids.add(UUID.fromString(dto.getUuid()));
						if (dto.getParentUuid() != null) uuids.add(UUID.fromString(dto.getParentUuid()));
					}
				}
				if (uuids.isEmpty()) return Mono.error(errors.getFirst());
				return Mono.just(uuids);
			})
			.flatMap(uuids -> repo.findAllByUuidInAndOwner(uuids, user).collectList()
				.map(known -> {
					List<TagEntity> created = new LinkedList<>();
					List<Tag> toCreate = new LinkedList<>();
					for (var dto : validOwned) {
						var existing = known.stream().filter(entity -> entity.getUuid().toString().equals(dto.getUuid())).findAny();
						if (existing.isPresent()) {
							created.add(existing.get());
						} else {
							toCreate.add(dto);
						}
					}
					return Tuples.of(known, created, toCreate, List.<Throwable>of());
				})
		    )
			// recursively create when parentUuid is created or null
	        .expand(tuple -> createTags(tuple, user, user)).last()
	        .map(tuple -> Tuples.of(tuple.getT2().stream().map(e -> toDTO(e, null)).toList(), tuple.getT4()));				
		} else {
			createdOwned = Mono.just(Tuples.of(List.of(), List.of()));
		}
		Mono<List<Tuple2<List<Tag>, List<Throwable>>>> createdSharedCollection;
		if (!validSharedCollections.isEmpty()) {
			createdSharedCollection = sharedCollectionMemberRepo.getInfoByCallerAndUuidIn(user, sharedCollectionsUuids).collectList()
			.flatMap(sharedInfos -> {
				Map<UUID, Tuple3<SharedCollectionInfo, List<Tag>, Set<UUID>>> bySharedCollection = new HashMap<>();
				for (var it = validSharedCollections.iterator(); it.hasNext(); ) {
					var dto = it.next();
					var sharedInfoOpt = sharedInfos.stream().filter(s -> s.getMemberUuid().toString().equals(dto.getCollectionUuid())).findAny();
					if (sharedInfoOpt.isEmpty()) {
						errors.add(new CollectionNotFound(dto.getCollectionUuid()));
						it.remove();
					} else {
						var sharedInfo = sharedInfoOpt.get();
						var tuple = bySharedCollection.computeIfAbsent(sharedInfo.getMemberUuid(), _ -> Tuples.of(sharedInfo, new LinkedList<>(), new HashSet<>()));
						tuple.getT2().add(dto);
						tuple.getT3().add(UUID.fromString(dto.getUuid()));
						if (dto.getParentUuid() != null) tuple.getT3().add(UUID.fromString(dto.getParentUuid()));
					}
				}
				if (bySharedCollection.isEmpty()) return Mono.error(errors.getFirst());
				return Flux.fromIterable(bySharedCollection.values())
				.flatMap(colTuple -> {
					var sharedInfo = colTuple.getT1();
					var colDtos = colTuple.getT2();
					var uuids = colTuple.getT3();
					var ownerInDb = SharedCollectionUtils.SHARED_OWNER_PREFIX + sharedInfo.getColUuid();
					return repo.findAllByUuidInAndOwner(uuids, ownerInDb).collectList()
					.map(known -> {
						List<TagEntity> created = new LinkedList<>();
						List<Tag> toCreate = new LinkedList<>();
						for (var dto : colDtos) {
							var existing = known.stream().filter(entity -> entity.getUuid().toString().equals(dto.getUuid())).findAny();
							if (existing.isPresent()) {
								created.add(existing.get());
							} else {
								dto.setOwner(ownerInDb);
								dto.setCollectionUuid(sharedInfo.getColUuid().toString());
								toCreate.add(dto);
							}
						}
						return Tuples.of(known, created, toCreate, List.<Throwable>of());
					})
					// recursively create when parentUuid is created or null
			        .expand(tuple -> createTags(tuple, ownerInDb, sharedInfo.getColOwner())).last()
			        .map(tuple -> Tuples.of(tuple.getT2().stream().map(e -> toDTO(e, sharedInfo.getMemberUuid())).toList(), tuple.getT4()));
				}, 1, 1)
				.collectList();
			});
		} else {
			createdSharedCollection = Mono.just(List.of());
		}
		return createdOwned.zipWith(createdSharedCollection)
        .flatMap(tuple -> {
        	var created = Stream.concat(tuple.getT1().getT1().stream(), tuple.getT2().stream().flatMap(t -> t.getT1().stream())).toList();
        	if (!created.isEmpty()) return Mono.just(created);
        	var errors2 = tuple.getT1().getT2();
        	if (!errors2.isEmpty()) return Mono.error(errors2.getFirst());
        	for (var t : tuple.getT2())
        		if (!t.getT2().isEmpty()) return Mono.error(t.getT2().getFirst());
        	if (!errors.isEmpty()) return Mono.error(errors.getFirst());
        	return Mono.just(List.of());
        });
    }
	
	private void validateCreate(Tag dto) {
		validate(dto);
		ValidationUtils.field("collectionUuid", dto.getCollectionUuid()).notNull().isUuid();
	}
	
	private void validate(Tag dto) {
		ValidationUtils.field("uuid", dto.getUuid()).notNull().isUuid();
		ValidationUtils.field("name", dto.getName()).nullable().maxLength(50);
		ValidationUtils.field("parentUuid", dto.getParentUuid()).nullable().isUuid();
	}
	
	private Mono<Tuple4<List<TagEntity>, List<TagEntity>, List<Tag>, List<Throwable>>> createTags(Tuple4<List<TagEntity>, List<TagEntity>, List<Tag>, List<Throwable>> tuple, String owner, String quotaOwner) {
		if (tuple.getT3().isEmpty())
			return Mono.empty();
    	List<Tag> canCreate = new LinkedList<>();
    	List<Tag> remaining = new LinkedList<>();
    	for (var dto : tuple.getT3()) {
    		if (dto.getParentUuid() == null || tuple.getT1().stream().anyMatch(
    				entity -> entity.getUuid().toString().equals(dto.getParentUuid()) && entity.getCollectionUuid().toString().equals(dto.getCollectionUuid())
    		))
    			canCreate.add(dto);
    		else
    			remaining.add(dto);
    	}
    	if (canCreate.isEmpty()) {
    		if (remaining.isEmpty())
    			return Mono.empty();
    		return Mono.just(Tuples.of(tuple.getT1(), tuple.getT2(), List.of(), TrailenceUtils.merge(tuple.getT4(),
    			remaining.stream().map(dto -> (Throwable) new NotFoundException("tag", "parent " + dto.getParentUuid() + " on collection " + dto.getCollectionUuid())).toList()
    		)));
    	}
    	return BulkUtils.parallelSingleOperations(Flux.fromIterable(canCreate), dto -> self.createTagWithQuota(dto, owner, quotaOwner))
    	.collectList()
    	.map(results -> {
    		var created = results.stream().filter(TagEntity.class::isInstance).map(e -> (TagEntity) e).toList();
    		var errors = results.stream().filter(Throwable.class::isInstance).map(e -> (Throwable) e).toList();
    		return Tuples.of(TrailenceUtils.merge(tuple.getT1(), created), TrailenceUtils.merge(tuple.getT2(), created), remaining, TrailenceUtils.merge(tuple.getT4(), errors));
    	});
	}
	
	@Transactional
	public Mono<TagEntity> createTagWithQuota(Tag dto, String owner, String quotaOwner) {
		return Mono.defer(() -> {
			TagEntity entity = new TagEntity();
			entity.setUuid(UUID.fromString(dto.getUuid()));
			entity.setOwner(owner);
			entity.setParentUuid(dto.getParentUuid() != null ? UUID.fromString(dto.getParentUuid()) : null);
			entity.setName(dto.getName());
			entity.setCollectionUuid(UUID.fromString(dto.getCollectionUuid()));
			entity.setCreatedAt(System.currentTimeMillis());
			entity.setUpdatedAt(entity.getCreatedAt());
			return r2dbc.insert(entity);
		})
		.flatMap(entity -> quotaService.addTags(quotaOwner, 1).thenReturn(entity));
    }
	
	public Mono<Void> bulkDelete(List<String> uuids, Authentication auth) {
		String owner = TrailenceUtils.email(auth);
		return delete(
			repo.findAllByUuidInAndOwner(new HashSet<>(uuids.stream().map(UUID::fromString).toList()), owner)
				.expandDeep(entity -> repo.findAllByParentUuidAndOwner(entity.getUuid(), owner)),
			owner, owner
		);
	}
	
	public Mono<Void> bulkDelete(String shareId, List<String> uuids, Authentication auth) {
		String caller = TrailenceUtils.email(auth);
    	UUID sharedCollectionUuidForCaller = SharedCollectionUtils.getSharedCollectionUuid(shareId);
    	return sharedCollectionRepo.getSharedCollectionHavingMember(sharedCollectionUuidForCaller, caller)
    	.flatMap(col -> {
    		String contentOwner = SharedCollectionUtils.SHARED_OWNER_PREFIX + col.getUuid().toString();
    		return delete(repo.findAllByUuidInAndOwner(uuids.stream().map(UUID::fromString).toList(), contentOwner), contentOwner, col.getOwner());
    	});
	}
	
	public Mono<Void> deleteAllFromCollections(Set<UUID> collections, String owner, String quotaOwner) {
		return delete(repo.findAllByCollectionUuidInAndOwner(collections, owner), owner, quotaOwner);
	}
	
	private Mono<Void> delete(Flux<TagEntity> toDelete, String owner, String quotaOwner) {
		return toDelete.collectList()
		.flatMap(entities -> {
			var tagsUuids = entities.stream().map(TagEntity::getUuid).collect(Collectors.toSet());
			return trailTagService.tagsDeleted(tagsUuids, owner, quotaOwner)
			.then(quotaOwner.equals(owner) ? shareService.tagsDeleted(tagsUuids, owner) : Mono.empty())
			.then(self.deleteTagsWithQuota(tagsUuids, owner, quotaOwner));
		});
	}
	
	@Transactional
	public Mono<Void> deleteTagsWithQuota(Set<UUID> uuids, String owner, String quotaOwner) {
		log.info("Deleting {} tags for {}", uuids.size(), owner);
		return repo.deleteAllByUuidInAndOwner(uuids, owner)
		.flatMap(nb -> quotaService.tagsDeleted(quotaOwner, nb))
		.then(Mono.fromRunnable(() -> log.info("Tags deleted ({} for {})", uuids.size(), owner)));
	}
	
	@SuppressWarnings("java:S3776")
	public Flux<Tag> bulkUpdate(Collection<Tag> dtos, Authentication auth) {
		String caller = TrailenceUtils.email(auth);
		Map<String, Tuple2<List<Tag>, Set<UUID>>> byOwner = new HashMap<>();
		List<Throwable> errors = new LinkedList<>();
		for (var dto : dtos) {
			try {
				validate(dto);
				if (SharedCollectionUtils.isSharedCollectionOwner(dto.getOwner())) {
					if (!dto.getCollectionUuid().equals(SharedCollectionUtils.getSharedCollectionUuid(dto.getOwner()).toString()))
						throw new BadRequestException("collectionUuid does not match owner");
				} else {
					dto.setOwner(caller);
				}
				var tuple = byOwner.computeIfAbsent(dto.getOwner(), _ -> Tuples.of(new LinkedList<>(), new HashSet<>()));
				tuple.getT1().add(dto);
				tuple.getT2().add(UUID.fromString(dto.getUuid()));
			} catch (Exception e) {
				errors.add(e);
			}
		}
		if (byOwner.isEmpty()) {
			if (errors.isEmpty()) return Flux.empty();
			return Flux.error(errors.getFirst());
		}
		return Flux.fromIterable(byOwner.entrySet())
		.flatMap(entry -> {
			if (entry.getKey().equals(caller))
				return repo.findAllByUuidInAndOwner(entry.getValue().getT2(), caller)
				.flatMap(entity -> associateEntityWithDto(entity, entry.getValue().getT1()), 2, 4)
				.collectList().map(list -> Tuples.of(list, caller, Optional.<SharedCollectionMemberEntity>empty()));
			UUID sharedUuid = SharedCollectionUtils.getSharedCollectionUuid(entry.getKey());
			return sharedCollectionMemberRepo.findByUuidAndOwner(sharedUuid, caller)
			.switchIfEmpty(Mono.defer(() -> {
				errors.add(new CollectionNotFound(sharedUuid.toString()));
				return Mono.empty();
			}))
			.flatMapMany(member -> {
				String colUuid = member.getSharedCollectionUuid().toString();
				String contentOwner = SharedCollectionUtils.SHARED_OWNER_PREFIX + colUuid;
				for (var dto : entry.getValue().getT1()) {
					dto.setOwner(contentOwner);
					dto.setCollectionUuid(colUuid);
				}
				return repo.findAllByUuidInAndOwner(entry.getValue().getT2(), contentOwner)
				.flatMap(entity -> associateEntityWithDto(entity, entry.getValue().getT1()), 2, 4)
				.collectList().map(list -> Tuples.of(list, contentOwner, Optional.of(member)));
			});
		}, 1, 1)
		.flatMap(tupleByOwner -> {
			var tuples = tupleByOwner.getT1();
			var contentOwner = tupleByOwner.getT2();
			var member = tupleByOwner.getT3().orElse(null);
			Set<UUID> newParents = new HashSet<>();
			for (var tagTuple : tuples) {
				if (tagTuple.getT1().getParentUuid() != null && (tagTuple.getT2().getParentUuid() == null || !tagTuple.getT1().getParentUuid().equals(tagTuple.getT2().getParentUuid().toString()))) {
					newParents.add(UUID.fromString(tagTuple.getT1().getParentUuid()));
				}
			}
			Mono<List<TagEntity>> getExistingParents =
				newParents.isEmpty() ? Mono.just(Collections.<TagEntity>emptyList()) : repo.findAllByUuidInAndOwner(newParents, contentOwner).collectList();
			return getExistingParents.flatMapMany(existingParents ->
				Flux.fromIterable(tuples)
				.flatMap(tuple -> doUpdate(tuple.getT2(), tuple.getT1(), existingParents), 2, 4)
			)
			.collectList()
			.flatMapMany(results -> {
				var updatedUuids = results.stream().filter(UUID.class::isInstance).map(o -> (UUID) o).toList();
				if (!updatedUuids.isEmpty())
					return repo.findAllByUuidInAndOwner(updatedUuids, contentOwner)
					.map(entity -> toDTO(entity, member != null ? member.getUuid() : null));
				if (errors.isEmpty())
					results.stream().filter(Throwable.class::isInstance).map(o -> (Throwable) o).findFirst().ifPresent(errors::add);
				return Flux.empty();
			});
		})
		.switchIfEmpty(Mono.defer(() -> {
			if (errors.isEmpty()) return Mono.empty();
			return Mono.error(errors.getFirst());
		}))
		;
	}
	
	private Mono<Tuple2<Tag, TagEntity>> associateEntityWithDto(TagEntity entity, Collection<Tag> dtos) {
		var dtoOpt = dtos.stream().filter(dto -> entity.getUuid().toString().equals(dto.getUuid())).findAny();
		if (dtoOpt.isEmpty()) return Mono.empty();
		return Mono.just(Tuples.of(dtoOpt.get(), entity));
	}
	
	private Mono<Object> doUpdate(TagEntity entity, Tag dto, List<TagEntity> existingParents) {
		if (dto.getVersion() != entity.getVersion()) return Mono.just(entity.getUuid());
		boolean updated = false;
		if (!entity.getName().equals(dto.getName())) {
			entity.setName(dto.getName());
			updated = true;
		}
		UUID newParent = dto.getParentUuid() != null ? UUID.fromString(dto.getParentUuid()) : null;
		if (!Objects.equals(entity.getParentUuid(), newParent)) {
			if (newParent != null && existingParents.stream().noneMatch(e -> e.getUuid().equals(newParent))) {
				return Mono.just(new NotFoundException("tag", newParent.toString()));
			}
			entity.setParentUuid(newParent);
			updated = true;
		}
		if (!updated) return Mono.just(entity.getUuid());
		return DbUtils.updateByUuidAndOwner(r2dbc, entity).thenReturn(entity.getUuid());
	}
	
	public Mono<UpdateResponse<Tag>> getUpdates(List<Versioned> known, Authentication auth) {
		String caller = TrailenceUtils.email(auth);
		Flux<TagEntity> owned = repo.findAllByOwner(caller);
		Map<String, SharedCollectionMemberEntity> memberByOwner = new ConcurrentHashMap<>();
		Flux<TagEntity> fromSharedCollections = AuthDetails.getVersion(auth) < SharedCollectionUtils.MIN_VERSION_FOR_SHARED ? Flux.empty() :
			sharedCollectionMemberRepo.findAllByOwner(caller)
			.flatMap(member -> {
				String dbOwner = SharedCollectionUtils.SHARED_OWNER_PREFIX + member.getSharedCollectionUuid();
				memberByOwner.put(dbOwner, member);
				String ownerForCaller = SharedCollectionUtils.SHARED_OWNER_PREFIX + member.getUuid();
				for (var k : known) if (k.getOwner().equals(ownerForCaller)) k.setOwner(dbOwner);
				return repo.findAllByOwner(dbOwner);
			}, 1, 1);
    	return BulkGetUpdates.bulkGetUpdates(
    		Flux.concat(owned, fromSharedCollections),
    		known,
    		entity -> toDTO(entity, Optional.ofNullable(memberByOwner.get(entity.getOwner())).map(m -> m.getUuid()).orElse(null))
    	).map(response -> {
    		for (var deleted : response.getDeleted()) {
    			if (deleted.getOwner().startsWith(SharedCollectionUtils.SHARED_OWNER_PREFIX)) {
    				var member = memberByOwner.get(deleted.getOwner());
    				if (member != null) deleted.setOwner(SharedCollectionUtils.SHARED_OWNER_PREFIX + member.getUuid());
    			}
    		}
    		return response;
    	});
    }
	
	private Tag toDTO(TagEntity entity, UUID sharedMemberUuid) {
		return new Tag(
			entity.getUuid().toString(),
			sharedMemberUuid != null ? SharedCollectionUtils.SHARED_OWNER_PREFIX + sharedMemberUuid : entity.getOwner(),
			entity.getVersion(),
			entity.getCreatedAt(),
			entity.getUpdatedAt(),
			entity.getName(),
			entity.getParentUuid() != null ? entity.getParentUuid().toString() : null,
					sharedMemberUuid != null ? sharedMemberUuid.toString() : entity.getCollectionUuid().toString()
		);
	}
	
}
