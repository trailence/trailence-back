package org.trailence.trail;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.sql.AsteriskFromTable;
import org.springframework.data.relational.core.sql.Conditions;
import org.springframework.data.relational.core.sql.SQL;
import org.springframework.data.relational.core.sql.Select;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.trailence.global.db.BulkGetUpdates;
import org.trailence.global.db.BulkUtils;
import org.trailence.global.db.BulkUtils.ChecksAndActions;
import org.trailence.global.db.DbUtils;
import org.trailence.global.dto.UpdateResponse;
import org.trailence.global.dto.Versioned;
import org.trailence.global.exceptions.NotFoundException;
import org.trailence.global.exceptions.ValidationUtils;
import org.trailence.quotas.QuotaService;
import org.trailence.trail.TrackService.TrackNotFound;
import org.trailence.trail.db.ShareElementEntity;
import org.trailence.trail.db.ShareEntity;
import org.trailence.trail.db.TrackRepository;
import org.trailence.trail.db.TrailCollectionRepository;
import org.trailence.trail.db.TrailEntity;
import org.trailence.trail.db.TrailRepository;
import org.trailence.trail.db.TrailTagEntity;
import org.trailence.trail.dto.ShareElementType;
import org.trailence.trail.dto.Trail;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
@RequiredArgsConstructor
@Slf4j
public class TrailService {

    private final TrailRepository repo;
    private final TrackRepository trackRepo;
    private final TrailCollectionRepository collectionRepo;
    private final R2dbcEntityTemplate r2dbc;
    private final ShareService shareService;
    private final PhotoService photoService;
    private final QuotaService quotaService;
    private final TrailTagService trailTagService;
    private final TrackService trackService;
    
    @Autowired @Lazy @SuppressWarnings("java:S6813")
    private TrailService self;

    public Mono<List<Trail>> bulkCreate(List<Trail> dtos, Authentication auth) {
    	String owner = auth.getPrincipal().toString();
    	return BulkUtils.bulkCreate(
    		dtos, owner,
    		this::validateCreate,
    		dto -> {
    			TrailEntity entity = new TrailEntity();
                entity.setUuid(UUID.fromString(dto.getUuid()));
                entity.setOwner(owner);
                entity.setName(dto.getName());
                entity.setDescription(dto.getDescription());
                entity.setLocation(dto.getLocation());
                entity.setLoopType(dto.getLoopType());
                entity.setCollectionUuid(UUID.fromString(dto.getCollectionUuid()));
                entity.setOriginalTrackUuid(UUID.fromString(dto.getOriginalTrackUuid()));
                entity.setCurrentTrackUuid(UUID.fromString(dto.getCurrentTrackUuid()));
                entity.setCreatedAt(System.currentTimeMillis());
                entity.setUpdatedAt(entity.getCreatedAt());
                return entity;
    		},
    		entities -> self.createTrailsWithQuota(entities, owner),
    		repo
    	)
    	.map(list -> list.stream().map(this::toDTO).toList());
    }

    @Transactional
    public Mono<List<TrailEntity>> createTrailsWithQuota(List<TrailEntity> entities, String owner) {
    	Set<UUID> collectionsUuids = new HashSet<>();
    	Set<UUID> tracksUuids = new HashSet<>();
    	entities.forEach(entity -> {
    		collectionsUuids.add(entity.getCollectionUuid());
    		tracksUuids.add(entity.getOriginalTrackUuid());
    		tracksUuids.add(entity.getCurrentTrackUuid());
    	});
    	
    	return Mono.zip(
    		collectionRepo.findExistingUuids(collectionsUuids, owner).collectList().publishOn(Schedulers.parallel()),
    		trackRepo.findExistingUuids(tracksUuids, owner).collectList().publishOn(Schedulers.parallel())
    	).flatMap(tuple -> {
    		List<UUID> existingCollectionsUuids = tuple.getT1();
    		List<UUID> existingTracksUuids = tuple.getT2();
    		List<Throwable> errors = new LinkedList<>();
    		List<TrailEntity> toCreate = new LinkedList<>();
    		for (var entity : entities) {
    			if (!existingCollectionsUuids.contains(entity.getCollectionUuid()))
    				errors.add(new NotFoundException("collection", entity.getCollectionUuid().toString()));
    			else if (!existingTracksUuids.contains(entity.getOriginalTrackUuid()))
    				errors.add(new TrackNotFound(owner, entity.getOriginalTrackUuid().toString()));
    			else if (!existingTracksUuids.contains(entity.getCurrentTrackUuid()))
    				errors.add(new TrackNotFound(owner, entity.getCurrentTrackUuid().toString()));
    			else
    				toCreate.add(entity);
    		}
    		if (toCreate.isEmpty()) return Mono.error(errors.getFirst());
    		return quotaService.addTrails(owner, toCreate.size())
	    	.flatMap(nb -> {
	    		var toCreate2 = nb == toCreate.size() ? toCreate : toCreate.subList(0, nb);
	    		return DbUtils.insertMany(r2dbc, toCreate2);
	    	});
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

    public Flux<Trail> bulkUpdate(List<Trail> dtos, Authentication auth) {
    	String owner = auth.getPrincipal().toString();
    	return BulkUtils.bulkUpdate(
    		dtos, owner,
    		this::validate,
    		(entity, dto, checksAndActions) -> this.updateEntity(entity, dto, checksAndActions, owner),
    		repo, r2dbc
    	).map(this::toDTO);
    }
    
    @SuppressWarnings("java:S3776")
    private boolean updateEntity(TrailEntity entity, Trail dto, ChecksAndActions checksAndActions, String owner) {
        var changed = false;
        if (dto.getCollectionUuid() != null && !dto.getCollectionUuid().equals(entity.getCollectionUuid().toString())) {
        	// change of collection: it must exist, and we should remove all associated tags
        	var newUuid = UUID.fromString(dto.getCollectionUuid());
        	checksAndActions.addCheck(
        		collectionRepo.existsByUuidAndOwner(newUuid, owner)
        		.map(exists -> exists.booleanValue() ? Optional.empty() : Optional.of(new NotFoundException("collection", newUuid.toString())))
        	).addAction(
        		trailTagService.trailsDeleted(Set.of(entity.getUuid()), owner)
        	);
        	entity.setCollectionUuid(newUuid);
        	changed = true;
        }
        if (dto.getCurrentTrackUuid() != null && !dto.getCurrentTrackUuid().equals(entity.getCurrentTrackUuid().toString())) {
        	var newUuid = UUID.fromString(dto.getCurrentTrackUuid());
        	checksAndActions.addCheck(
        		trackRepo.existsByUuidAndOwner(newUuid, owner)
        		.map(exists -> exists.booleanValue() ? Optional.empty() : Optional.of(new TrackNotFound(owner, newUuid.toString())))
        	);
        	if (!entity.getCurrentTrackUuid().equals(entity.getOriginalTrackUuid()))
        		checksAndActions.addAction(trackService.deleteTracksWithQuota(Set.of(entity.getCurrentTrackUuid()), owner));
            entity.setCurrentTrackUuid(newUuid);
            changed = true;
        }
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
        return changed;
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
				trailTagService.trailsDeleted(trailsUuids, owner).thenReturn(1).publishOn(Schedulers.parallel()),
				trackService.deleteTracksWithQuota(tracksUuids, owner).thenReturn(1).publishOn(Schedulers.parallel()),
				shareService.trailsDeleted(trailsUuids, owner).thenReturn(1).publishOn(Schedulers.parallel()),
				photoService.trailsDeleted(trailsUuids, owner).thenReturn(1).publishOn(Schedulers.parallel())
			).then(self.deleteTrailsWithQuota(trailsUuids, owner));
		});
    }
    
    @Transactional
    public Mono<Void> deleteTrailsWithQuota(Set<UUID> uuids, String owner) {
    	return repo.deleteAllByUuidInAndOwner(uuids, owner)
    	.flatMap(nb -> quotaService.trailsDeleted(owner, nb));
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
