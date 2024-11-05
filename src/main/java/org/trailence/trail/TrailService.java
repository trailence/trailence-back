package org.trailence.trail;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.sql.AsteriskFromTable;
import org.springframework.data.relational.core.sql.Conditions;
import org.springframework.data.relational.core.sql.SQL;
import org.springframework.data.relational.core.sql.Select;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.trailence.global.db.BulkGetUpdates;
import org.trailence.global.db.DbUtils;
import org.trailence.global.dto.UpdateResponse;
import org.trailence.global.dto.Versioned;
import org.trailence.global.exceptions.NotFoundException;
import org.trailence.global.exceptions.ValidationUtils;
import org.trailence.trail.TrackService.TrackNotFound;
import org.trailence.trail.db.ShareElementEntity;
import org.trailence.trail.db.ShareEntity;
import org.trailence.trail.db.TrackRepository;
import org.trailence.trail.db.TrailCollectionRepository;
import org.trailence.trail.db.TrailEntity;
import org.trailence.trail.db.TrailRepository;
import org.trailence.trail.db.TrailTagEntity;
import org.trailence.trail.db.TrailTagRepository;
import org.trailence.trail.dto.ShareElementType;
import org.trailence.trail.dto.Trail;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.function.Tuples;

@Service
@RequiredArgsConstructor
@Slf4j
public class TrailService {

    private final TrailRepository repo;
    private final TrackRepository trackRepo;
    private final TrailCollectionRepository collectionRepo;
    private final TrailTagRepository trailTagRepo;
    private final R2dbcEntityTemplate r2dbc;
    private final ShareService shareService;
    private final PhotoService photoService;

    private Mono<Trail> createNotExisting(Trail dto, Authentication auth) {
        UUID collectionId = UUID.fromString(dto.getCollectionUuid());
        UUID originalTrackId = UUID.fromString(dto.getOriginalTrackUuid());
        UUID currentTrackId = UUID.fromString(dto.getCurrentTrackUuid());
        String owner = auth.getPrincipal().toString();

        Mono<Boolean> collectionExists = collectionRepo.existsByUuidAndOwner(collectionId, owner)
        	.map(exists -> {
        		if (!exists.booleanValue())
        			throw new NotFoundException("collection", dto.getCollectionUuid());
        		return true;
        	});
        Mono<Boolean> originalTrackExists = trackRepo.existsByUuidAndOwner(originalTrackId, owner)
        	.map(exists -> {
        		if (!exists.booleanValue())
        			throw new TrackNotFound(owner, dto.getOriginalTrackUuid());
        		return true;
        	});
        Mono<Boolean> currentTrackExists = currentTrackId.equals(originalTrackId) ? Mono.just(true) :
            trackRepo.existsByUuidAndOwner(currentTrackId, owner)
            .map(exists -> {
            	if (!exists.booleanValue())
            		throw new TrackNotFound(owner, dto.getCurrentTrackUuid());
            	return true;
            });

        return Mono.zip(
            collectionExists.publishOn(Schedulers.parallel()),
            originalTrackExists.publishOn(Schedulers.parallel()),
            currentTrackExists.publishOn(Schedulers.parallel())
        ).flatMap(ok -> {
            TrailEntity entity = new TrailEntity();
            entity.setUuid(UUID.fromString(dto.getUuid()));
            entity.setOwner(owner);
            entity.setName(dto.getName());
            entity.setDescription(dto.getDescription());
            entity.setLocation(dto.getLocation());
            entity.setLoopType(dto.getLoopType());
            entity.setCollectionUuid(collectionId);
            entity.setOriginalTrackUuid(originalTrackId);
            entity.setCurrentTrackUuid(currentTrackId);
            entity.setCreatedAt(System.currentTimeMillis());
            entity.setUpdatedAt(entity.getCreatedAt());
            return r2dbc.insert(entity);
        }).map(this::toDTO);
    }

    public Mono<List<Trail>> bulkCreate(List<Trail> dtos, Authentication auth) {
    	List<Trail> valid = new LinkedList<>();
    	Set<UUID> uuids = new HashSet<>();
    	List<Throwable> errors = new LinkedList<>();
    	dtos.forEach(dto -> {
    		try {
    			validateCreate(dto);
    			valid.add(dto);
    			uuids.add(UUID.fromString(dto.getUuid()));
    		} catch (Exception e) {
    			errors.add(e);
    		}
    	});
    	if (valid.isEmpty()) {
    		if (errors.isEmpty()) return Mono.just(List.of());
    		return Mono.error(errors.getFirst());
    	}
        return repo.findAllByUuidInAndOwner(uuids, auth.getPrincipal().toString())
        .collectList()
        .zipWhen(known -> {
            List<Trail> toCreate = valid.stream().filter(dto -> known.stream().noneMatch(entity -> entity.getUuid().toString().equals(dto.getUuid()))).toList();
            if (toCreate.isEmpty()) return Mono.just(Collections.<Trail>emptyList());
            return Flux.fromIterable(toCreate)
            	.flatMap(dto -> createNotExisting(dto, auth)
            		.map(Object.class::cast)
            		.onErrorResume(e -> Mono.just((Object) e)),
            		3, 6
            	).collectList();
        })
        .flatMap(tuple -> {
        	@SuppressWarnings("unchecked")
			var created = (Stream<Trail>) tuple.getT2().stream().filter(Trail.class::isInstance);
        	var existing = tuple.getT1().stream().map(this::toDTO);
        	var result = Stream.concat(created, existing).toList();
        	if (!result.isEmpty()) return Mono.just(result);
        	var errors2 = tuple.getT2().stream().filter(Throwable.class::isInstance).map(Throwable.class::cast).toList();
        	if (!errors2.isEmpty()) return Mono.error(errors2.getFirst());
        	if (!errors.isEmpty()) return Mono.error(errors.getFirst());
        	return Mono.just(result);
        });
    }
    
    private void validateCreate(Trail dto) {
    	validate(dto);
    	ValidationUtils.field("originalTrackUuid", dto.getOriginalTrackUuid()).notNull().isUuid();
    }
    
    private void validate(Trail dto) {
    	ValidationUtils.field("uuid", dto.getUuid()).notNull().isUuid();
    	ValidationUtils.field("name", dto.getName()).nullable().maxLength(200);
    	ValidationUtils.field("description", dto.getDescription()).nullable().maxLength(50000);
    	ValidationUtils.field("location", dto.getLocation()).nullable().maxLength(100);
    	ValidationUtils.field("loopType", dto.getLoopType()).nullable().maxLength(2);
    	ValidationUtils.field("currentTrackUuid", dto.getCurrentTrackUuid()).notNull().isUuid();
    	ValidationUtils.field("collectionUuid", dto.getCollectionUuid()).notNull().isUuid();
    }

    @SuppressWarnings("java:S3776")
    public Flux<Trail> bulkUpdate(List<Trail> dtos, Authentication auth) {
    	List<Trail> valid = new LinkedList<>();
    	Set<UUID> uuids = new HashSet<>();
    	List<Throwable> errors = new LinkedList<>();
    	dtos.forEach(dto -> {
    		try {
    			validate(dto);
    			valid.add(dto);
    			uuids.add(UUID.fromString(dto.getUuid()));
    		} catch (Exception e) {
    			errors.add(e);
    		}
    	});
    	if (valid.isEmpty()) {
    		if (errors.isEmpty()) return Flux.empty();
    		return Flux.error(errors.getFirst());
    	}
        String owner = auth.getPrincipal().toString();
        return repo.findAllByUuidInAndOwner(uuids, owner)
        .flatMap(entity -> {
            var dtoOpt = valid.stream().filter(dto -> dto.getUuid().equals(entity.getUuid().toString())).findAny();
            if (dtoOpt.isEmpty()) return Mono.just(Tuples.of(entity.getUuid(), false));
            var dto = dtoOpt.get();
            if (dto.getVersion() != entity.getVersion()) return Mono.just(Tuples.of(entity.getUuid(), true));
            return updateEntity(entity, dto, owner).zipWith(Mono.just(true));
        }, 3, 6)
        .collectList()
        .flatMapMany(results -> {
        	var updatedUuids = results.stream().filter(r -> r.getT1() instanceof UUID && r.getT2().booleanValue()).map(r -> (UUID)r.getT1()).toList();
        	if (!updatedUuids.isEmpty()) return repo.findAllByUuidInAndOwner(updatedUuids, owner);
        	var errors2 = results.stream().filter(r -> r.getT1() instanceof Throwable).map(r -> (Throwable)r.getT1()).toList();
        	if (!errors2.isEmpty()) return Flux.<TrailEntity>error(errors2.getFirst());
        	if (!errors.isEmpty()) return Flux.<TrailEntity>error(errors.getFirst());
        	return Flux.<TrailEntity>empty();
        })
        .map(this::toDTO);
    }
    
    @SuppressWarnings("java:S3776")
    private Mono<Object> updateEntity(TrailEntity entity, Trail dto, String owner) {
        Mono<Optional<Throwable>> checks = Mono.just(Optional.empty());
        Mono<?> actions = Mono.just(true);
        var changed = false;
        if (dto.getCollectionUuid() != null && !dto.getCollectionUuid().equals(entity.getCollectionUuid().toString())) {
        	// change of collection: it must exist, and we should remove all associated tags
        	var newUuid = UUID.fromString(dto.getCollectionUuid());
        	checks = checks.flatMap(error -> {
        		if (error.isPresent()) return Mono.just(error);
        		return collectionRepo.existsByUuidAndOwner(newUuid, owner)
        			.map(exists -> exists.booleanValue() ? Optional.empty() : Optional.of(new NotFoundException("collection", newUuid.toString())));
        	});
        	actions = actions.then(trailTagRepo.deleteAllByTrailUuidInAndOwner(Set.of(entity.getUuid()), owner).then(Mono.just(true)));
        	entity.setCollectionUuid(newUuid);
        	changed = true;
        }
        if (dto.getCurrentTrackUuid() != null && !dto.getCurrentTrackUuid().equals(entity.getCurrentTrackUuid().toString())) {
        	var newUuid = UUID.fromString(dto.getCurrentTrackUuid());
        	checks = checks.flatMap(error -> {
        		if (error.isPresent()) return Mono.just(error);
        		return trackRepo.existsByUuidAndOwner(newUuid, owner)
        			.map(exists -> exists.booleanValue() ? Optional.empty() : Optional.of(new TrackNotFound(owner, newUuid.toString())));
        	});
        	if (!entity.getCurrentTrackUuid().equals(entity.getOriginalTrackUuid()))
        		actions = actions.then(trackRepo.deleteByUuidAndOwner(entity.getCurrentTrackUuid(), owner));
            entity.setCurrentTrackUuid(newUuid);
            changed = true;
        }
        var a = actions;
        var c = changed;
        return checks.flatMap(error -> {
        	if (error.isPresent()) return Mono.just(error.get());
        	return a.then(updateEntityFields(entity, dto, c));
        });
    }

    private Mono<UUID> updateEntityFields(TrailEntity entity, Trail dto, boolean changed) {
        if (!Objects.equals(entity.getName(), dto.getName())) {
            entity.setName(dto.getName());
            changed = true;
        }
        if (!Objects.equals(entity.getDescription(), dto.getDescription())) {
            entity.setDescription(dto.getDescription());
            changed = true;
        }
        if (!Objects.equals(entity.getLocation(), dto.getLocation())) {
        	entity.setLocation(dto.getLocation());
        	changed = true;
        }
        if (!Objects.equals(entity.getLoopType(), dto.getLoopType())) {
        	entity.setLoopType(dto.getLoopType());
        	changed = true;
        }
        if (!changed) return Mono.just(entity.getUuid());
        return DbUtils.updateByUuidAndOwner(r2dbc, entity).thenReturn(entity.getUuid());
    }

    public Mono<Void> bulkDelete(List<String> uuids, Authentication auth) {
        String owner = auth.getPrincipal().toString();
        return delete(repo.findAllByUuidInAndOwner(uuids.stream().map(UUID::fromString).toList(), owner), owner);
    }

    public Mono<Void> deleteAllFromCollections(List<UUID> collections, String owner) {
    	return delete(repo.findAllByCollectionUuidInAndOwner(collections, owner), owner);
    }
    
    private Mono<Void> delete(Flux<TrailEntity> toDelete, String owner) {
    	return toDelete.collectList()
		.flatMap(entities -> {
			Set<UUID> trailsUuids = new HashSet<>();
			Set<UUID> tracksUuids = new HashSet<>();
			entities.forEach(entity -> {
				trailsUuids.add(entity.getUuid());
				tracksUuids.add(entity.getOriginalTrackUuid());
				tracksUuids.add(entity.getCurrentTrackUuid());
			});
			return Mono.zip(
				trailTagRepo.deleteAllByTrailUuidInAndOwner(trailsUuids, owner).thenReturn(1).publishOn(Schedulers.parallel()),
				trackRepo.deleteAllByUuidInAndOwner(tracksUuids, owner).thenReturn(1).publishOn(Schedulers.parallel()),
				shareService.trailsDeleted(trailsUuids, owner).thenReturn(1).publishOn(Schedulers.parallel()),
				photoService.trailsDeleted(trailsUuids, owner).thenReturn(1).publishOn(Schedulers.parallel())
			).then(repo.deleteAllByUuidInAndOwner(trailsUuids, owner));
		});
    }

    public Mono<UpdateResponse<Trail>> getUpdates(List<Versioned> known, Authentication auth) {
    	return BulkGetUpdates.bulkGetUpdates(r2dbc, buildSelectAccessibleTrails(auth.getPrincipal().toString()), TrailEntity.class, trail -> trail.getOwner() + " " + trail.getUuid().toString(), known, this::toDTO);
    }

    private List<Select> buildSelectAccessibleTrails(String email) {
        Select sharedCollections = Select.builder()
    		.select(AsteriskFromTable.create(TrailEntity.TABLE))
    		.from(ShareEntity.TABLE)
    		.join(ShareElementEntity.TABLE).on(Conditions.isEqual(ShareElementEntity.COL_SHARE_UUID, ShareEntity.COL_UUID).and(Conditions.isEqual(ShareElementEntity.COL_OWNER, ShareEntity.COL_FROM_EMAIL)))
    		.join(TrailEntity.TABLE).on(Conditions.isEqual(TrailEntity.COL_COLLECTION_UUID, ShareElementEntity.COL_ELEMENT_UUID).and(Conditions.isEqual(TrailEntity.COL_OWNER, ShareEntity.COL_FROM_EMAIL)))
    		.where(
    			Conditions.isEqual(ShareEntity.COL_TO_EMAIL, SQL.literalOf(email))
    			.and(Conditions.isEqual(ShareEntity.COL_ELEMENT_TYPE, SQL.literalOf(ShareElementType.COLLECTION.name())))
    		)
    		.build();
    	
    	Select sharedTags = Select.builder()
			.select(AsteriskFromTable.create(TrailEntity.TABLE))
    		.from(ShareEntity.TABLE)
    		.join(ShareElementEntity.TABLE).on(Conditions.isEqual(ShareElementEntity.COL_SHARE_UUID, ShareEntity.COL_UUID).and(Conditions.isEqual(ShareElementEntity.COL_OWNER, ShareEntity.COL_FROM_EMAIL)))
    		.join(TrailTagEntity.TABLE).on(Conditions.isEqual(TrailTagEntity.COL_TAG_UUID, ShareElementEntity.COL_ELEMENT_UUID).and(Conditions.isEqual(TrailTagEntity.COL_OWNER, ShareEntity.COL_FROM_EMAIL)))
    		.join(TrailEntity.TABLE).on(Conditions.isEqual(TrailTagEntity.COL_TRAIL_UUID, TrailEntity.COL_UUID).and(Conditions.isEqual(TrailEntity.COL_OWNER, ShareEntity.COL_FROM_EMAIL)))
    		.where(
    			Conditions.isEqual(ShareEntity.COL_TO_EMAIL, SQL.literalOf(email))
    			.and(Conditions.isEqual(ShareEntity.COL_ELEMENT_TYPE, SQL.literalOf(ShareElementType.TAG.name())))
    		)
    		.build();
    	
    	Select sharedTrails = Select.builder()
			.select(AsteriskFromTable.create(TrailEntity.TABLE))
    		.from(ShareEntity.TABLE)
    		.join(ShareElementEntity.TABLE).on(Conditions.isEqual(ShareElementEntity.COL_SHARE_UUID, ShareEntity.COL_UUID).and(Conditions.isEqual(ShareElementEntity.COL_OWNER, ShareEntity.COL_FROM_EMAIL)))
    		.join(TrailEntity.TABLE).on(Conditions.isEqual(ShareElementEntity.COL_ELEMENT_UUID, TrailEntity.COL_UUID).and(Conditions.isEqual(TrailEntity.COL_OWNER, ShareEntity.COL_FROM_EMAIL)))
    		.where(
    			Conditions.isEqual(ShareEntity.COL_TO_EMAIL, SQL.literalOf(email))
    			.and(Conditions.isEqual(ShareEntity.COL_ELEMENT_TYPE, SQL.literalOf(ShareElementType.TRAIL.name())))
    		)
    		.build();
    	
    	Select ownedTrails = Select.builder()
	        .select(AsteriskFromTable.create(TrailEntity.TABLE))
	        .from(TrailEntity.TABLE)
	        .where(Conditions.isEqual(TrailEntity.COL_OWNER, SQL.literalOf(email)))
	        .build();
    	
    	return List.of(ownedTrails, sharedTrails, sharedTags, sharedCollections);
    }

    private Trail toDTO(TrailEntity entity) {
        return new Trail(
            entity.getUuid().toString(),
            entity.getOwner(),
            entity.getVersion(),
            entity.getCreatedAt(),
            entity.getUpdatedAt(),
            entity.getName(),
            entity.getDescription(),
            entity.getLocation(),
            entity.getLoopType(),
            entity.getOriginalTrackUuid().toString(),
            entity.getCurrentTrackUuid().toString(),
            entity.getCollectionUuid().toString()
        );
    }

}
