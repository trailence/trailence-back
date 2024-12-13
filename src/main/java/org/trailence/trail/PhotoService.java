package org.trailence.trail;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.core.io.buffer.DataBuffer;
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
import org.trailence.storage.FileService;
import org.trailence.trail.db.PhotoEntity;
import org.trailence.trail.db.PhotoRepository;
import org.trailence.trail.db.ShareElementEntity;
import org.trailence.trail.db.ShareEntity;
import org.trailence.trail.db.TrailEntity;
import org.trailence.trail.db.TrailRepository;
import org.trailence.trail.db.TrailTagEntity;
import org.trailence.trail.dto.Photo;
import org.trailence.trail.dto.ShareElementType;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class PhotoService {
	
	private final FileService fileService;
	private final PhotoRepository repo;
	private final TrailRepository trailRepo;
	private final R2dbcEntityTemplate r2dbc;

	@SuppressWarnings("java:S107") // number of parameters
	public Mono<Photo> storePhoto(
		String uuid, String trailUuid,
		String description, Long dateTaken, Long latitude, Long longitude,
		boolean isCover, int index,
		Flux<DataBuffer> content, long size,
		Authentication auth
	) {
		ValidationUtils.field("uuid", uuid).notNull().isUuid();
		ValidationUtils.field("trailUuid", trailUuid).notNull().isUuid();
		ValidationUtils.field("description", description).nullable().maxLength(5000);
		String owner = auth.getPrincipal().toString();
		return Mono.zip(
			repo.findByUuidAndOwner(UUID.fromString(uuid), owner)
				.map(Optional::of)
				.switchIfEmpty(Mono.just(Optional.empty())
			),
			trailRepo.findByUuidAndOwner(UUID.fromString(trailUuid), owner)
				.map(Optional::of)
				.switchIfEmpty(Mono.just(Optional.empty()))
		)
		.flatMap(existing -> {
			if (existing.getT1().isPresent()) {
				return content.then(Mono.just(existing.getT1().get()));
			}
			if (existing.getT2().isEmpty()) {
				return Mono.error(new NotFoundException("trail", trailUuid));
			}
			return fileService.storeFile(size, content).flatMap(fileId -> {
				PhotoEntity entity = new PhotoEntity();
				entity.setUuid(UUID.fromString(uuid));
				entity.setOwner(owner);
				entity.setCreatedAt(System.currentTimeMillis());
				entity.setFileId(fileId);
				entity.setTrailUuid(UUID.fromString(trailUuid));
				entity.setDescription(description);
				entity.setDateTaken(dateTaken);
				entity.setLatitude(latitude);
				entity.setLongitude(longitude);
				entity.setCover(isCover);
				entity.setIndex(index);
				return r2dbc.insert(entity);
			});			
		}).map(this::toDto);
	}
	
    public Flux<Photo> bulkUpdate(List<Photo> dtos, Authentication auth) {
        String owner = auth.getPrincipal().toString();
        List<Throwable> errors = new LinkedList<>();
        List<Photo> valid = new LinkedList<>();
        Set<UUID> uuids = new HashSet<>();
        dtos.forEach(dto -> {
        	try {
	        	ValidationUtils.field("uuid", dto.getUuid()).notNull().isUuid();
	        	ValidationUtils.field("description", dto.getDescription()).nullable().maxLength(5000);
	        	valid.add(dto);
	        	uuids.add(UUID.fromString(dto.getUuid()));
        	} catch (Exception e) {
        		errors.add(e);
        	}
        });
        if (uuids.isEmpty()) {
        	if (errors.isEmpty()) return Flux.empty();
        	return Flux.error(errors.getFirst());
        }
        return repo.findAllByUuidInAndOwner(uuids, owner)
        .flatMap(entity -> {
            var dtoOpt = valid.stream().filter(dto -> dto.getUuid().equals(entity.getUuid().toString())).findAny();
            if (dtoOpt.isEmpty()) return Mono.empty();
            var dto = dtoOpt.get();
            return updateEntity(entity, dto);
        }, 3, 6)
        .collectList()
        .flatMapMany(updatedUuids -> {
        	if (!updatedUuids.isEmpty()) return repo.findAllByUuidInAndOwner(updatedUuids, owner);
        	if (!errors.isEmpty()) return Flux.error(errors.getFirst());
        	return Flux.empty();
        })
        .map(this::toDto);
    }

    private Mono<UUID> updateEntity(PhotoEntity entity, Photo dto) {
        if (dto.getVersion() != entity.getVersion()) return Mono.just(entity.getUuid());
    	boolean changed = false;
        if (!Objects.equals(entity.getDescription(), dto.getDescription())) {
            entity.setDescription(dto.getDescription());
            changed = true;
        }
        if (!Objects.equals(entity.getDateTaken(), dto.getDateTaken())) {
            entity.setDateTaken(dto.getDateTaken());
            changed = true;
        }
        if (!Objects.equals(entity.getLatitude(), dto.getLatitude())) {
            entity.setLatitude(dto.getLatitude());
            changed = true;
        }
        if (!Objects.equals(entity.getLongitude(), dto.getLongitude())) {
            entity.setLongitude(dto.getLongitude());
            changed = true;
        }
        if (!Objects.equals(entity.isCover(), dto.isCover())) {
        	entity.setCover(dto.isCover());
        	changed = true;
        }
        if (!Objects.equals(entity.getIndex(), dto.getIndex())) {
        	entity.setIndex(dto.getIndex());
        	changed = true;
        }
        if (!changed) return Mono.just(entity.getUuid());
        return DbUtils.updateByUuidAndOwner(r2dbc, entity).thenReturn(entity.getUuid());
    }
    
    public Mono<Void> bulkDelete(List<String> uuids, Authentication auth) {
        String owner = auth.getPrincipal().toString();
        return delete(repo.findAllByUuidInAndOwner(new HashSet<>(uuids.stream().map(UUID::fromString).toList()), owner));
    }
    
    public Mono<Void> trailsDeleted(Set<UUID> trailsUuids, String owner) {
    	return delete(repo.findAllByTrailUuidInAndOwner(trailsUuids, owner));
    }
    
    private Mono<Void> delete(Flux<PhotoEntity> toDelete) {
    	return toDelete.flatMap(
    		entity -> repo.deleteByUuidAndOwner(entity.getUuid(), entity.getOwner())
    			.then(fileService.deleteFile(entity.getFileId())),
    		3, 6
    	).then();
    }
	
	private Photo toDto(PhotoEntity entity) {
		return new Photo(
			entity.getUuid().toString(),
			entity.getOwner(),
			entity.getVersion(),
			entity.getCreatedAt(),
			entity.getUpdatedAt(),
			entity.getTrailUuid().toString(),
			entity.getDescription(),
			entity.getDateTaken(),
			entity.getLatitude(),
			entity.getLongitude(),
			entity.isCover(),
			entity.getIndex()
		);
	}
	
    public Mono<UpdateResponse<Photo>> getUpdates(List<Versioned> known, Authentication auth) {
    	return BulkGetUpdates.bulkGetUpdates(r2dbc, buildSelectAccessiblePhotos(auth.getPrincipal().toString()), PhotoEntity.class, photo -> photo.getOwner() + " " + photo.getUuid().toString(), known, this::toDto);
    }

    private List<Select> buildSelectAccessiblePhotos(String email) {
        Select sharedCollections = Select.builder()
    		.select(AsteriskFromTable.create(PhotoEntity.TABLE))
    		.from(ShareEntity.TABLE)
    		.join(ShareElementEntity.TABLE).on(Conditions.isEqual(ShareElementEntity.COL_SHARE_UUID, ShareEntity.COL_UUID).and(Conditions.isEqual(ShareElementEntity.COL_OWNER, ShareEntity.COL_FROM_EMAIL)))
    		.join(TrailEntity.TABLE).on(Conditions.isEqual(TrailEntity.COL_COLLECTION_UUID, ShareElementEntity.COL_ELEMENT_UUID).and(Conditions.isEqual(TrailEntity.COL_OWNER, ShareEntity.COL_FROM_EMAIL)))
    		.join(PhotoEntity.TABLE).on(Conditions.isEqual(PhotoEntity.COL_TRAIL_UUID, TrailEntity.COL_UUID).and(Conditions.isEqual(PhotoEntity.COL_OWNER, TrailEntity.COL_OWNER)))
    		.where(
    			Conditions.isEqual(ShareEntity.COL_TO_EMAIL, SQL.literalOf(email))
    			.and(Conditions.isEqual(ShareEntity.COL_ELEMENT_TYPE, SQL.literalOf(ShareElementType.COLLECTION.name())))
    			.and(Conditions.isEqual(ShareEntity.COL_INCLUDE_PHOTOS, SQL.literalOf(true)))
    		)
    		.build();
    	
    	Select sharedTags = Select.builder()
			.select(AsteriskFromTable.create(PhotoEntity.TABLE))
    		.from(ShareEntity.TABLE)
    		.join(ShareElementEntity.TABLE).on(Conditions.isEqual(ShareElementEntity.COL_SHARE_UUID, ShareEntity.COL_UUID).and(Conditions.isEqual(ShareElementEntity.COL_OWNER, ShareEntity.COL_FROM_EMAIL)))
    		.join(TrailTagEntity.TABLE).on(Conditions.isEqual(TrailTagEntity.COL_TAG_UUID, ShareElementEntity.COL_ELEMENT_UUID).and(Conditions.isEqual(TrailTagEntity.COL_OWNER, ShareEntity.COL_FROM_EMAIL)))
    		.join(PhotoEntity.TABLE).on(Conditions.isEqual(TrailTagEntity.COL_TRAIL_UUID, PhotoEntity.COL_TRAIL_UUID).and(Conditions.isEqual(PhotoEntity.COL_OWNER, ShareEntity.COL_FROM_EMAIL)))
    		.where(
    			Conditions.isEqual(ShareEntity.COL_TO_EMAIL, SQL.literalOf(email))
    			.and(Conditions.isEqual(ShareEntity.COL_ELEMENT_TYPE, SQL.literalOf(ShareElementType.TAG.name())))
    			.and(Conditions.isEqual(ShareEntity.COL_INCLUDE_PHOTOS, SQL.literalOf(true)))
    		)
    		.build();
    	
    	Select sharedTrails = Select.builder()
			.select(AsteriskFromTable.create(PhotoEntity.TABLE))
    		.from(ShareEntity.TABLE)
    		.join(ShareElementEntity.TABLE).on(Conditions.isEqual(ShareElementEntity.COL_SHARE_UUID, ShareEntity.COL_UUID).and(Conditions.isEqual(ShareElementEntity.COL_OWNER, ShareEntity.COL_FROM_EMAIL)))
    		.join(PhotoEntity.TABLE).on(Conditions.isEqual(ShareElementEntity.COL_ELEMENT_UUID, PhotoEntity.COL_TRAIL_UUID).and(Conditions.isEqual(PhotoEntity.COL_OWNER, ShareEntity.COL_FROM_EMAIL)))
    		.where(
    			Conditions.isEqual(ShareEntity.COL_TO_EMAIL, SQL.literalOf(email))
    			.and(Conditions.isEqual(ShareEntity.COL_ELEMENT_TYPE, SQL.literalOf(ShareElementType.TRAIL.name())))
    			.and(Conditions.isEqual(ShareEntity.COL_INCLUDE_PHOTOS, SQL.literalOf(true)))
    		)
    		.build();
    	
    	Select ownedPhotos = Select.builder()
	        .select(AsteriskFromTable.create(PhotoEntity.TABLE))
	        .from(PhotoEntity.TABLE)
	        .where(Conditions.isEqual(PhotoEntity.COL_OWNER, SQL.literalOf(email)))
	        .build();
    	
    	return List.of(ownedPhotos, sharedTrails, sharedTags, sharedCollections);
    }

    public Mono<Flux<DataBuffer>> getFileContent(String owner, String uuid, Authentication auth) {
    	return getPhoto(owner, uuid, auth)
    	.flatMap(entity -> Mono.just(fileService.getFileContent(entity.getFileId())));
    }
    
    private Mono<PhotoEntity> getPhoto(String owner, String uuid, Authentication auth) {
    	String email = owner.toLowerCase();
    	Mono<PhotoEntity> getFromDB = repo.findByUuidAndOwner(UUID.fromString(uuid), email);
		if (email.equals(auth.getPrincipal().toString())) return getFromDB;
		String user = auth.getPrincipal().toString();
		return isFromSharedTrail(uuid, email, user)
		.flatMap(sharedTrail -> {
			if (sharedTrail.booleanValue()) return getFromDB;
			return isFromSharedTag(uuid, email, user)
			.flatMap(sharedTag -> {
				if (sharedTag.booleanValue()) return getFromDB;
				return isFromSharedCollection(uuid, email, user)
				.flatMap(sharedCollection -> {
					if (sharedCollection.booleanValue()) return getFromDB;
					return Mono.error(new NotFoundException("photo", uuid));
				});
			});
		});
    }
	
	private Mono<Boolean> isFromSharedTrail(String photoUuid, String photoOwner, String user) {
		var select = Select.builder()
			.select(PhotoEntity.COL_UUID)
			.from(PhotoEntity.TABLE)
			.join(ShareElementEntity.TABLE).on(Conditions.isEqual(ShareElementEntity.COL_ELEMENT_UUID, PhotoEntity.COL_TRAIL_UUID).and(Conditions.isEqual(PhotoEntity.COL_OWNER, ShareElementEntity.COL_OWNER)))
			.join(ShareEntity.TABLE).on(
				Conditions.isEqual(ShareElementEntity.COL_SHARE_UUID, ShareEntity.COL_UUID)
				.and(Conditions.isEqual(ShareEntity.COL_ELEMENT_TYPE, SQL.literalOf(ShareElementType.TRAIL.name())))
				.and(Conditions.isEqual(ShareEntity.COL_INCLUDE_PHOTOS, SQL.literalOf(true)))
				.and(Conditions.isEqual(ShareEntity.COL_FROM_EMAIL, SQL.literalOf(photoOwner)))
				.and(Conditions.isEqual(ShareEntity.COL_TO_EMAIL, SQL.literalOf(user)))
			)
			.limit(1)
			.where(
				Conditions.isEqual(PhotoEntity.COL_OWNER, SQL.literalOf(photoOwner))
				.and(Conditions.isEqual(PhotoEntity.COL_UUID, SQL.literalOf(photoUuid)))
			)
			.build();
		return r2dbc.query(DbUtils.select(select, null, r2dbc), UUID.class).first().hasElement();
	}
	
	private Mono<Boolean> isFromSharedTag(String photoUuid, String photoOwner, String user) {
		var select = Select.builder()
			.select(PhotoEntity.COL_UUID)
			.from(PhotoEntity.TABLE)
			.join(TrailTagEntity.TABLE).on(Conditions.isEqual(TrailTagEntity.COL_TRAIL_UUID, PhotoEntity.COL_TRAIL_UUID))
			.join(ShareElementEntity.TABLE).on(Conditions.isEqual(ShareElementEntity.COL_ELEMENT_UUID, TrailTagEntity.COL_TAG_UUID).and(Conditions.isEqual(ShareElementEntity.COL_OWNER, PhotoEntity.COL_OWNER)))
			.join(ShareEntity.TABLE).on(
				Conditions.isEqual(ShareElementEntity.COL_SHARE_UUID, ShareEntity.COL_UUID)
				.and(Conditions.isEqual(ShareEntity.COL_ELEMENT_TYPE, SQL.literalOf(ShareElementType.TAG.name())))
				.and(Conditions.isEqual(ShareEntity.COL_INCLUDE_PHOTOS, SQL.literalOf(true)))
				.and(Conditions.isEqual(ShareEntity.COL_FROM_EMAIL, SQL.literalOf(photoOwner)))
				.and(Conditions.isEqual(ShareEntity.COL_TO_EMAIL, SQL.literalOf(user)))
			)
			.limit(1)
			.where(
				Conditions.isEqual(PhotoEntity.COL_OWNER, SQL.literalOf(photoOwner))
				.and(Conditions.isEqual(PhotoEntity.COL_UUID, SQL.literalOf(photoUuid)))
			)
			.build();
		return r2dbc.query(DbUtils.select(select, null, r2dbc), UUID.class).first().hasElement();
	}
	
	private Mono<Boolean> isFromSharedCollection(String photoUuid, String photoOwner, String user) {
		var select = Select.builder()
			.select(PhotoEntity.COL_UUID)
			.from(PhotoEntity.TABLE)
			.join(TrailEntity.TABLE).on(Conditions.isEqual(PhotoEntity.COL_TRAIL_UUID, TrailEntity.COL_UUID).and(Conditions.isEqual(PhotoEntity.COL_OWNER, TrailEntity.COL_OWNER)))
			.join(ShareElementEntity.TABLE).on(Conditions.isEqual(ShareElementEntity.COL_ELEMENT_UUID, TrailEntity.COL_COLLECTION_UUID).and(Conditions.isEqual(ShareElementEntity.COL_OWNER, SQL.literalOf(photoOwner))))
			.join(ShareEntity.TABLE).on(
				Conditions.isEqual(ShareElementEntity.COL_SHARE_UUID, ShareEntity.COL_UUID)
				.and(Conditions.isEqual(ShareEntity.COL_ELEMENT_TYPE, SQL.literalOf(ShareElementType.COLLECTION.name())))
				.and(Conditions.isEqual(ShareEntity.COL_INCLUDE_PHOTOS, SQL.literalOf(true)))
				.and(Conditions.isEqual(ShareEntity.COL_FROM_EMAIL, SQL.literalOf(photoOwner)))
				.and(Conditions.isEqual(ShareEntity.COL_TO_EMAIL, SQL.literalOf(user)))
			)
			.limit(1)
			.where(
				Conditions.isEqual(PhotoEntity.COL_OWNER, SQL.literalOf(photoOwner))
				.and(Conditions.isEqual(PhotoEntity.COL_UUID, SQL.literalOf(photoUuid)))
			)
			.build();
		return r2dbc.query(DbUtils.select(select, null, r2dbc), UUID.class).first().hasElement();
	}

}
