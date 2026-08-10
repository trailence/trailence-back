package org.trailence.trail;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import org.apache.commons.lang3.mutable.MutableObject;
import org.apache.commons.lang3.stream.Streams;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.sql.AsteriskFromTable;
import org.springframework.data.relational.core.sql.Column;
import org.springframework.data.relational.core.sql.Conditions;
import org.springframework.data.relational.core.sql.SQL;
import org.springframework.data.relational.core.sql.Select;
import org.springframework.data.relational.core.sql.Table;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.trailence.global.TrailenceUtils;
import org.trailence.global.db.BulkGetUpdates;
import org.trailence.global.db.BulkUtils;
import org.trailence.global.db.DbUtils;
import org.trailence.global.db.IgnoreException;
import org.trailence.global.dto.UpdateResponse;
import org.trailence.global.dto.Versioned;
import org.trailence.global.exceptions.ValidationUtils;
import org.trailence.global.rest.AuthDetails;
import org.trailence.preferences.UserPreferencesService;
import org.trailence.quotas.QuotaService;
import org.trailence.trail.db.SharedCollectionEntity;
import org.trailence.trail.db.SharedCollectionMemberEntity;
import org.trailence.trail.db.SharedCollectionMemberRepository;
import org.trailence.trail.db.SharedCollectionRepository;
import org.trailence.trail.db.TrailCollectionEntity;
import org.trailence.trail.db.TrailCollectionRepository;
import org.trailence.trail.dto.TrailCollection;
import org.trailence.trail.dto.TrailCollectionType;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

@Service
@RequiredArgsConstructor
@Slf4j
public class TrailCollectionService {

    private final TrailCollectionRepository repo;
    private final R2dbcEntityTemplate r2dbc;
    private final TrailService trailService;
    private final TagService tagService;
    private final ShareService shareService;
    private final QuotaService quotaService;
    private final SharedCollectionRepository sharedCollectionRepo;
    private final SharedCollectionMemberRepository sharedCollectionMemberRepo;
    private final UserPreferencesService preferencesService;
    
    @Autowired @Lazy @SuppressWarnings("java:S6813")
    private TrailCollectionService self;

    public Mono<List<TrailCollection>> bulkCreate(List<TrailCollection> dtos, Authentication auth) {
    	String owner = TrailenceUtils.email(auth);
    	List<TrailCollection> normalCollectionsDtos = new LinkedList<>();
    	List<TrailCollection> sharedCollectionsDtos = new LinkedList<>();
    	for (var dto : dtos) {
    		if (TrailCollectionType.SHARED.equals(dto.getType()))
    			sharedCollectionsDtos.add(dto);
    		else
    			normalCollectionsDtos.add(dto);
    	}
    	return bulkCreateCollections(normalCollectionsDtos, owner)
    	.flatMap(normalDtos -> {
    		if (sharedCollectionsDtos.isEmpty()) return Mono.just(normalDtos);
    		return bulkCreateSharedCollections(sharedCollectionsDtos, owner)
    		.map(sharedDtos -> {
    			if (normalDtos.isEmpty()) return sharedDtos;
    			return TrailenceUtils.merge(normalDtos, sharedDtos);
    		});
    	});
    }
    
    private Mono<List<TrailCollection>> bulkCreateCollections(List<TrailCollection> dtos, String owner) {
    	if (dtos.isEmpty()) return Mono.just(List.of());
    	return BulkUtils.bulkCreate(
    		dtos, owner,
    		this::validateCreate,
    		dto -> {
    			TrailCollectionEntity entity = new TrailCollectionEntity();
	            entity.setUuid(UUID.fromString(dto.getUuid()));
	            entity.setOwner(owner);
	            entity.setName(dto.getName());
	            entity.setType(dto.getType());
	            entity.setCreatedAt(System.currentTimeMillis());
	            entity.setUpdatedAt(entity.getCreatedAt());
	            return entity;
    		},
    		entities -> {
    			var uniques = new LinkedList<TrailCollectionEntity>();
    			var classic = new LinkedList<TrailCollectionEntity>();
    			entities.forEach(entity -> {
    				if (TrailCollectionType.PUBLICATION_TYPES.contains(entity.getType()))
    					uniques.add(entity);
    				else
    					classic.add(entity);
    			});
    			var createUniques = uniques.isEmpty() ? Mono.just(new LinkedList<TrailCollectionEntity>()) :
    				Flux.fromIterable(uniques).flatMap(entity -> self.createUniqueCollectionWithoutQuota(entity), 1, 1).onErrorResume(IgnoreException.class, _ -> Mono.empty()).collectList();
    			var createClassic = classic.isEmpty() ? Mono.just(new LinkedList<TrailCollectionEntity>()) : self.createCollectionsWithQuota(classic, owner);
    			return createUniques.flatMap(uniquesCreated -> createClassic.map(classicCreated -> {
    				var all = new LinkedList<TrailCollectionEntity>();
    				all.addAll(uniquesCreated);
    				all.addAll(classicCreated);
    				return all;
    			}));
    		},
    		repo
    	).map(list -> list.stream().map(this::toDTO).toList());
    }

    @Transactional
    public Mono<List<TrailCollectionEntity>> createCollectionsWithQuota(List<TrailCollectionEntity> entities, String owner) {
    	return quotaService.addCollections(owner, entities.size())
    	.flatMap(nb -> {
    		var toCreate = nb == entities.size() ? entities : entities.subList(0, nb);
    		return DbUtils.insertMany(r2dbc, toCreate);
    	});
    }
    
    @Transactional
    public Mono<TrailCollectionEntity> createUniqueCollectionWithoutQuota(TrailCollectionEntity entity) {
    	return repo.findOneByTypeAndOwner(entity.getType().name(), entity.getOwner())
    	.map(Optional::of).switchIfEmpty(Mono.just(Optional.empty()))
    	.flatMap(opt -> {
    		if (opt.isPresent()) return Mono.empty();
    		return r2dbc.insert(entity)
    		.flatMap(created -> repo.findAllByTypeAndOwner(entity.getType().name(), entity.getOwner()).collectList().flatMap(list -> {
    			if (list.size() == 1 && list.get(0).getUuid().equals(created.getUuid())) return Mono.just(created);
    			return Mono.error(new IgnoreException("Collection type already exists for this user"));
    		}));
    	});
    }
    
    private Mono<List<TrailCollection>> bulkCreateSharedCollections(List<TrailCollection> dtos, String owner) {
    	MutableObject<Optional<Throwable>> error = new MutableObject<>(Optional.empty());
    	return Flux.fromIterable(dtos)
    	.flatMap(dto ->
    		self.createSharedCollection(dto, owner)
    		.onErrorResume(e -> {
    			if (error.get().isEmpty()) error.setValue(Optional.of(e));
    			return Mono.empty();
    		}),
    		1, 1
    	)
    	.collectList()
    	.flatMap(list -> {
    		if (list.isEmpty() && error.get().isPresent()) return Mono.error(error.get().get());
    		return Mono.just(list);
    	});
    }
    
    @Transactional
    public Mono<TrailCollection> createSharedCollection(TrailCollection dto, String owner) {
    	long now = System.currentTimeMillis();
		validate(dto);
    	UUID ownerUuid = UUID.fromString(dto.getUuid());
		ValidationUtils.field("sharedWith", dto.getSharedWith()).notNull();
		SharedCollectionEntity sharedCollectionEntity = new SharedCollectionEntity();
		sharedCollectionEntity.setUuid(UUID.randomUUID());
		sharedCollectionEntity.setOwner(owner);
		Set<String> members = new HashSet<>();
		List<SharedCollectionMemberEntity> memberEntities = new LinkedList<>();
		members.add(owner);
		SharedCollectionMemberEntity memberEntity = new SharedCollectionMemberEntity();
		memberEntity.setSharedCollectionUuid(sharedCollectionEntity.getUuid());
		memberEntity.setOwner(owner);
		memberEntity.setUuid(ownerUuid);
		memberEntity.setName(dto.getName());
		memberEntity.setVersion(1L);
		memberEntity.setCreatedAt(now);
		memberEntity.setUpdatedAt(now);
		memberEntities.add(memberEntity);
		for (String friend : dto.getSharedWith()) {
			String email = TrailenceUtils.normalizeEmail(friend);
			if (members.add(email)) {
				memberEntity = new SharedCollectionMemberEntity();
	    		memberEntity.setSharedCollectionUuid(sharedCollectionEntity.getUuid());
	    		memberEntity.setOwner(email);
	    		memberEntity.setUuid(UUID.randomUUID());
	    		memberEntity.setName(dto.getName());
	    		memberEntity.setVersion(1L);
	    		memberEntity.setCreatedAt(now);
	    		memberEntity.setUpdatedAt(now);
	    		memberEntities.add(memberEntity);
			}
		}
		return sharedCollectionMemberRepo.findByUuidAndOwner(ownerUuid, owner)
		.flatMap(existing ->
			Mono.zip(
				sharedCollectionMemberRepo.findAllBySharedCollectionUuid(existing.getSharedCollectionUuid()).collectList(),
				sharedCollectionRepo.findById(existing.getSharedCollectionUuid())
			)
			.map(tuple -> toDto(tuple.getT2(), tuple.getT1(), owner))
		)
		.switchIfEmpty(
			quotaService.addSharedCollection(owner)
	    	.then(Mono.defer(() ->
	    		r2dbc.insert(sharedCollectionEntity)
	    		.flatMap(col ->
	    			Flux.fromIterable(memberEntities)
	    			.flatMap(r2dbc::insert, 1, 1)
	    			.collectList()
	    			.flatMap(m ->
	    				preferencesService.getPreferences(owner)
	    				.flatMap(p -> p.getLang() == null ? Mono.empty() : Mono.just(p.getLang()))
	    				.switchIfEmpty(Mono.just("en"))
	    				.flatMap(language ->
	    					Flux.fromIterable(Streams.of(m).filter(e -> !e.getOwner().equals(owner)).toList())
	    					.flatMap(e -> sendInvitationEmail(col, e, language))
	    					.then()
	    				)
	    				.thenReturn(toDto(col, m, owner))
	    			)
	    		)
	    	))
	    );
    }
    
    private Mono<Void> sendInvitationEmail(SharedCollectionEntity col, SharedCollectionMemberEntity member, String language) {
    	return shareService.sendInvitationEmails(member.getUuid().toString(), col.getOwner(), List.of(member.getOwner()), language, member.getName(), SharedCollectionUtils.SHARED_OWNER_PREFIX, "shares.shared_collection")
    	.onErrorComplete();
    }
    
    private void validateCreate(TrailCollection dto) {
    	validate(dto);
    	ValidationUtils.field("type", dto.getType()).notNull()
    		.notIn(List.of(TrailCollectionType.MY_TRAILS, TrailCollectionType.SHARED));
    }
    
    private void validate(TrailCollection dto) {
    	ValidationUtils.field("uuid", dto.getUuid()).notNull().isUuid();
    	ValidationUtils.field("name", dto.getName()).maxLength(50);
    }

    public Mono<UpdateResponse<TrailCollection>> getUpdates(List<Versioned> known, Authentication auth) {
    	String email = TrailenceUtils.email(auth);
    	Flux<TrailCollectionEntity> owned = r2dbc.query(DbUtils.select(buildSelectAccessibleCollections(email), null, r2dbc), TrailCollectionEntity.class).all();
		Map<UUID, SharedCollectionEntity> colById = new HashMap<>();
		List<SharedCollectionMemberEntity> callerMembers = new LinkedList<>();
		Map<UUID, UUID> colIdByCallerId = new HashMap<>();
    	Flux<TrailCollectionEntity> shared = AuthDetails.getVersion(auth) < SharedCollectionUtils.MIN_VERSION_FOR_SHARED ? Flux.empty() :
    		sharedCollectionMemberRepo.findAllByOwner(email).collectList()
    		.flatMap(members -> {
    			if (members.isEmpty()) return Mono.empty();
    			callerMembers.addAll(members);
    			return sharedCollectionRepo.findAllById(Streams.of(members).map(m -> m.getSharedCollectionUuid()).distinct().toList()).collectList()
    			.map(collections -> {
    				for (var col : collections) colById.put(col.getUuid(), col);
    				return Streams.of(members).map(member -> {
    					colIdByCallerId.put(member.getUuid(), member.getSharedCollectionUuid());
    					TrailCollectionEntity entity = new TrailCollectionEntity();
    					entity.setType(TrailCollectionType.SHARED);
   						entity.setOwner(email);
    					entity.setUuid(member.getUuid());
    					entity.setName(member.getName());
    					entity.setCreatedAt(member.getCreatedAt());
    					entity.setUpdatedAt(member.getUpdatedAt());
    					entity.setVersion(member.getVersion());
    					return entity;
    				}).toList();
    			});
    		})
    		.flatMapMany(Flux::fromIterable);
    	return BulkGetUpdates.bulkGetUpdates(
    		Flux.concat(owned, shared),
    		known,
    		this::toDTO
    	).flatMap(response -> {
    		// handle shared collections attributes
    		if (callerMembers.isEmpty()) return Mono.just(response);
    		// make request to add sharedWith on shared collections where owner is caller
    		var ownedColIds = Stream.concat(response.getCreated().stream(), response.getUpdated().stream())
	    		.map(col -> {
	    			if (!TrailCollectionType.SHARED.equals(col.getType())) return null;
	    			var colId = colIdByCallerId.get(UUID.fromString(col.getUuid()));
	    			if (colId == null) return null;
	    			var c = colById.get(colId);
	    			if (c == null) return null;
	    			if (!c.getOwner().equals(email)) return null;
	    			return colId;
	    		})
	    		.filter(Objects::nonNull)
	    		.toList();
    		
    		var getAllMembers = ownedColIds.isEmpty() ? Mono.just(List.<SharedCollectionMemberEntity>of()) : sharedCollectionMemberRepo.findAllBySharedCollectionUuidIn(ownedColIds).collectList();
    		return getAllMembers
    		.map(allMembers -> {
    			for (var col : response.getCreated())
    				setSharedAttributes(col, email, colById, colIdByCallerId, allMembers);
        		for (var col : response.getUpdated())
        			setSharedAttributes(col, email, colById, colIdByCallerId, allMembers);
        		return response;
    		});
    	});
    }
    
    private void setSharedAttributes(TrailCollection dto, String caller, Map<UUID, SharedCollectionEntity> colById, Map<UUID, UUID> colIdByCallerId, Collection<SharedCollectionMemberEntity> allMembers) {
    	if (!TrailCollectionType.SHARED.equals(dto.getType())) return;
    	var colId = colIdByCallerId.get(UUID.fromString(dto.getUuid()));
    	if (colId == null) return;
    	var col = colById.get(colId);
    	if (col == null) return;
    	if (col.getOwner().equals(caller)) {
	    	var myMemberOpt = allMembers.stream().filter(m -> m.getOwner().equals(dto.getOwner()) && m.getUuid().toString().equals(dto.getUuid())).findFirst();
	    	if (myMemberOpt.isEmpty()) return;
	    	var myMember = myMemberOpt.get();
	    	dto.setSharedWith(
	    		allMembers.stream()
	    		.filter(m -> m != myMember && m.getSharedCollectionUuid().equals(myMember.getSharedCollectionUuid()))
	    		.map(m -> m.getOwner())
	    		.toList()
	    	);
	    	dto.setSharedBy(caller);
    	} else {
    		dto.setSharedBy(col.getOwner());
    	}
    }

    public Flux<TrailCollection> bulkUpdate(List<TrailCollection> collections, Authentication auth) {
    	List<TrailCollection> normalCollectionsDtos = new LinkedList<>();
    	List<TrailCollection> sharedCollectionsDtos = new LinkedList<>();
    	for (var dto : collections) {
    		if (TrailCollectionType.SHARED.equals(dto.getType()))
    			sharedCollectionsDtos.add(dto);
    		else
    			normalCollectionsDtos.add(dto);
    	}
    	String email = TrailenceUtils.email(auth);
    	Flux<TrailCollection> normalUpdates = normalCollectionsDtos.isEmpty() ? Flux.empty() :
	    	BulkUtils.bulkUpdate(
	    		normalCollectionsDtos,
	    		email,
	    		this::validate,
	    		(entity, dto, _) -> {
	    			if (TrailCollectionType.PUBLICATION_TYPES.contains(entity.getType()))
	    				return false;
	                if (entity.getName().equals(dto.getName()))
	                	return false;
	                entity.setName(dto.getName());
	                return true;
	    		},
	    		repo,
	    		r2dbc
	    	).map(this::toDTO);
    	Flux<TrailCollection> sharedUpdates = sharedCollectionsDtos.isEmpty() ? Flux.empty() :
    		BulkUtils.<TrailCollection, UUID, Throwable, Tuple2<SharedCollectionEntity, List<SharedCollectionMemberEntity>>>bulkUpdate(
    			sharedCollectionsDtos,
    			dto -> {
    				validate(dto);
    				return UUID.fromString(dto.getUuid());
    			},
    			uuids -> sharedCollectionMemberRepo.findAllByUuidInAndOwner(uuids, email)
    				.collectList()
    				.flatMapMany(members -> 
    					sharedCollectionRepo.findAllById(members.stream().map(m -> m.getSharedCollectionUuid()).toList())
    					.flatMap(col -> {
    						var member = members.stream().filter(m -> m.getSharedCollectionUuid().equals(col.getUuid())).findAny();
    						if (member.isEmpty()) return Mono.empty();
    						return Mono.just(Tuples.of(col, List.of(member.get())));
    					})
    				),
    			(tuple, dto) -> tuple.getT2().getFirst().getUuid().toString().equals(dto.getUuid()),
    			(tuple, dto) -> {
    				SharedCollectionMemberEntity member = tuple.getT2().getFirst();
    				SharedCollectionEntity col = tuple.getT1();
    				if (col.getOwner().equals(email) && dto.getSharedWith() != null) {
    					return sharedCollectionMemberRepo.findAllBySharedCollectionUuid(col.getUuid()).collectList()
    						.flatMap(currentMembers -> {
    							List<SharedCollectionMemberEntity> toRemove = new LinkedList<>(currentMembers);
    							List<String> toAdd = new LinkedList<>();
    							toRemove.removeIf(existing -> existing.getOwner().equals(email)); // do not remove itself
    							var otherMembers = new LinkedList<>(toRemove);
    							for (String m : dto.getSharedWith()) {
    								String memberEmail = TrailenceUtils.normalizeEmail(m);
    								if (!toRemove.removeIf(existing -> existing.getOwner().equals(memberEmail)))
    									toAdd.add(memberEmail);
    							}
    							if (toRemove.isEmpty() && toAdd.isEmpty()) {
    								if (!member.getName().equals(dto.getName())) {
    			    					member.setName(dto.getName());
    			    					return DbUtils.updateByUuidAndOwner(r2dbc, member)
    			    						.flatMap(nb -> nb == 0 ? Mono.just(member) : sharedCollectionMemberRepo.findByUuidAndOwner(member.getUuid(), email))
    			    						.map(updated -> Tuples.of(col, TrailenceUtils.merge(otherMembers, List.of(updated))));
    			    				}
    								return Mono.just(Tuples.of(col, currentMembers));
    							}
    							long now = System.currentTimeMillis();
    							return Flux.fromIterable(toRemove)
    							.flatMap(e -> sharedCollectionMemberRepo.deleteByUuidAndOwner(e.getUuid(), e.getOwner()), 2, 1)
    							.then(
    								Flux.fromIterable(toAdd)
    								.map(invitee -> {
    									var memberEntity = new SharedCollectionMemberEntity();
    						    		memberEntity.setSharedCollectionUuid(col.getUuid());
    						    		memberEntity.setOwner(invitee);
    						    		memberEntity.setUuid(UUID.randomUUID());
    						    		memberEntity.setName(dto.getName());
    						    		memberEntity.setVersion(1L);
    						    		memberEntity.setCreatedAt(now);
    						    		memberEntity.setUpdatedAt(now);
    						    		return memberEntity;
    								})
    								.flatMap(r2dbc::insert, 1, 1)
    								.collectList()
					    			.flatMap(m ->
					    				preferencesService.getPreferences(email)
					    				.flatMap(p -> p.getLang() == null ? Mono.empty() : Mono.just(p.getLang()))
					    				.switchIfEmpty(Mono.just("en"))
					    				.flatMap(language ->
					    					Flux.fromIterable(m)
					    					.flatMap(e -> sendInvitationEmail(col, e, language))
					    					.then()
					    				)
					    				.thenReturn(m)
					    			)
					    		)
    							.flatMap(newMembers -> {
    								member.setName(dto.getName());
			    					return DbUtils.updateByUuidAndOwner(r2dbc, member)
			    						.flatMap(nb -> nb == 0 ? Mono.just(member) : sharedCollectionMemberRepo.findByUuidAndOwner(member.getUuid(), email))
			    						.map(updated -> {
			    							List<SharedCollectionMemberEntity> newList = new LinkedList<>();
			    							newList.add(updated);
			    							for (var other : otherMembers)
			    								if (!toRemove.contains(other))
			    									newList.add(other);
			    							newList.addAll(newMembers);
			    							return Tuples.of(col, newList);
			    						});
    							});
    						});
    				}
    				if (!member.getName().equals(dto.getName())) {
    					member.setName(dto.getName());
    					return DbUtils.updateByUuidAndOwner(r2dbc, member)
    						.flatMap(nb -> nb == 0 ? Mono.just(member) : sharedCollectionMemberRepo.findByUuidAndOwner(member.getUuid(), email))
    						.map(updated -> Tuples.of(col, List.of(updated)));
    				}
    				return Mono.just(tuple);
    			}
    		).map(tuple -> toDto(tuple.getT1(), tuple.getT2(), email));

    	return Flux.concat(normalUpdates, sharedUpdates);
    }

    public Mono<Void> bulkDelete(List<String> uuids, Authentication auth) {
        Set<UUID> ids = new HashSet<>(uuids.stream().map(UUID::fromString).toList());
        if (ids.isEmpty()) return Mono.empty();
        String owner = TrailenceUtils.email(auth);
        return repo.findDeletables(ids, owner).collectList()
        .flatMap(collections -> {
        	Mono<Void> deleteCollections = collections.isEmpty() ? Mono.empty() : deleteCollections(collections, owner);
        	ids.removeAll(collections.stream().map(c -> c.getUuid()).toList());
        	Mono<Void> deleteShared = ids.isEmpty() ? Mono.empty() :
        		sharedCollectionMemberRepo.findAllByUuidInAndOwner(ids, owner).collectList().flatMap(shared -> deleteSharedMembers(shared, owner));
        	return deleteCollections.then(deleteShared);
        });
    }
    
    public Mono<Void> deleteUser(String email) {
    	return repo.findAllByOwner(email).collectList().flatMap(collections -> deleteCollections(collections, email))
    	.then(sharedCollectionMemberRepo.findAllByOwner(email).collectList().flatMap(shared -> deleteSharedMembers(shared, email)));
    }
    
    private Mono<Void> deleteCollections(List<TrailCollectionEntity> entities, String owner) {
    	if (entities.isEmpty()) return Mono.empty();
		Set<UUID> classic = new HashSet<>();
		Set<UUID> all = new HashSet<>();
		for (var entity : entities) {
			if (!TrailCollectionType.PUBLICATION_TYPES.contains(entity.getType()))
				classic.add(entity.getUuid());
			all.add(entity.getUuid());
		}
    	return trailService.deleteAllFromCollections(all, owner, owner)
    	.then(classic.isEmpty() ? Mono.empty() : tagService.deleteAllFromCollections(classic, owner, owner))
    	.then(classic.isEmpty() ? Mono.empty() : shareService.collectionsDeleted(classic, owner))
    	.then(self.deleteCollectionsWithQuota(entities, owner));
    }
    
    @Transactional
    public Mono<Void> deleteCollectionsWithQuota(List<TrailCollectionEntity> entities, String owner) {
		log.info("Deleting {} collections for {}", entities.size(), owner);
		Set<UUID> withQuota = new HashSet<>();
		Set<UUID> withoutQuota = new HashSet<>();
		for (var entity : entities) {
			if (TrailCollectionType.NOT_IN_QUOTA.contains(entity.getType()))
				withoutQuota.add(entity.getUuid());
			else
				withQuota.add(entity.getUuid());
		}
		return (withQuota.isEmpty() ? Mono.empty() : repo.deleteAllByUuidInAndOwner(withQuota, owner).flatMap(nb -> quotaService.collectionsDeleted(owner, nb)))
		.then(withoutQuota.isEmpty() ? Mono.empty() : repo.deleteAllByUuidInAndOwner(withoutQuota, owner))
		.then();
    }
    
    private Mono<Void> deleteSharedMembers(List<SharedCollectionMemberEntity> entities, String email) {
    	if (entities.isEmpty()) return Mono.empty();
    	Set<UUID> collectionsUuids = new HashSet<>();
    	for (var entity : entities) collectionsUuids.add(entity.getSharedCollectionUuid());
    	return sharedCollectionRepo.findAllById(collectionsUuids).collectList()
    	.flatMap(collections -> {
    		Map<UUID, SharedCollectionEntity> collectionById = new HashMap<>();
    		for (var col : collections) collectionById.put(col.getUuid(), col);
    		Set<UUID> notOwner = new HashSet<>();
    		Set<UUID> notOwnerCollections = new HashSet<>();
    		Set<UUID> isOwner = new HashSet<>();
    		for (var entity : entities)
    			if (entity.getOwner().equals(collectionById.get(entity.getSharedCollectionUuid()).getOwner())) {
    				isOwner.add(entity.getSharedCollectionUuid());
    			} else {
    				notOwner.add(entity.getUuid());
    				notOwnerCollections.add(entity.getSharedCollectionUuid());
    			}
    		Mono<Void> deleteMembers = notOwner.isEmpty() ? Mono.empty() : 
    			sharedCollectionMemberRepo.deleteAllByUuidInAndOwner(notOwner, email)
    			.then(updateSharedCollectionsOwnerVersion(notOwnerCollections));
    		Mono<Void> deleteCollections = isOwner.isEmpty() ? Mono.empty() : deleteSharedCollections(isOwner, email);
    		return deleteMembers.then(deleteCollections);
    	});
    }
    
    private Mono<Void> updateSharedCollectionsOwnerVersion(Collection<UUID> uuids) {
    	if (uuids.isEmpty()) return Mono.empty();
    	return sharedCollectionRepo.findAllById(uuids)
    	.flatMap(col ->
    		sharedCollectionMemberRepo.findOneBySharedCollectionUuidAndOwner(col.getUuid(), col.getOwner())
    		.flatMap(ownerMember -> DbUtils.updateByUuidAndOwner(r2dbc, ownerMember))
    		, 2, 1
    	)
    	.then();
    }
    
    private Mono<Void> deleteSharedCollections(Collection<UUID> uuids, String owner) {
    	if (uuids.isEmpty()) return Mono.empty();
    	return Flux.fromIterable(uuids)
    	.flatMap(uuid -> {
        	String contentOwner = SharedCollectionUtils.SHARED_OWNER_PREFIX + uuid.toString();
    		return trailService.deleteAllFromCollections(Set.of(uuid), contentOwner, owner)
    		.then(tagService.deleteAllFromCollections(Set.of(uuid), contentOwner, owner))
    		.then(self.deleteSharedCollectionWithQuota(uuid, owner));
    	}, 2, 1)
    	.then();
    }
    
    @Transactional
    public Mono<Void> deleteSharedCollectionWithQuota(UUID uuid, String owner) {
		log.info("Deleting shared collection from {}", owner);
		return sharedCollectionMemberRepo.deleteBySharedCollectionUuid(uuid)
		.then(sharedCollectionRepo.deleteByUuid(uuid))
		.flatMap(nb -> nb > 0 ? quotaService.sharedCollectionDeleted(owner) : Mono.empty());
    }
    
    private Select buildSelectAccessibleCollections(String email) {
        Table table = Table.create("collections");
        return Select.builder()
        .select(AsteriskFromTable.create(table))
        .from(table)
        .where(Conditions.isEqual(Column.create("owner", table), SQL.literalOf(email)))
        .build();
    }

    private TrailCollection toDTO(TrailCollectionEntity entity) {
        return new TrailCollection(
            entity.getUuid().toString(),
            entity.getOwner(),
            entity.getVersion(),
            entity.getCreatedAt(),
            entity.getUpdatedAt(),
            entity.getName(),
            entity.getType(),
            null, // sharedWith
            null // sharedBy
        );
    }
    
    private TrailCollection toDto(SharedCollectionEntity col, List<SharedCollectionMemberEntity> members, String caller) {
    	SharedCollectionMemberEntity callerEntity = null;
    	List<String> sharedWith = null;
    	if (col.getOwner().equals(caller)) sharedWith = new LinkedList<>();
    	for (var member : members) {
    		if (member.getOwner().equals(caller)) {
    			callerEntity = member;
    			if (sharedWith == null) break;
    		}
    		if (sharedWith != null) {
    			if (callerEntity != member) sharedWith.add(member.getOwner());
    		}
    	}
    	if (callerEntity == null) throw new IllegalStateException();
    	return new TrailCollection(
    		callerEntity.getUuid().toString(),
    		caller,
    		callerEntity.getVersion(),
    		callerEntity.getCreatedAt(),
    		callerEntity.getUpdatedAt(),
    		callerEntity.getName(),
    		TrailCollectionType.SHARED,
    		sharedWith,
    		col.getOwner()
    	);
    }
}
