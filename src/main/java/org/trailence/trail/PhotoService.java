package org.trailence.trail;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.sql.AsteriskFromTable;
import org.springframework.data.relational.core.sql.Conditions;
import org.springframework.data.relational.core.sql.Expression;
import org.springframework.data.relational.core.sql.SQL;
import org.springframework.data.relational.core.sql.Select;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.trailence.global.db.BulkGetUpdates;
import org.trailence.global.db.BulkUtils;
import org.trailence.global.db.DbUtils;
import org.trailence.global.dto.UpdateResponse;
import org.trailence.global.dto.Versioned;
import org.trailence.global.exceptions.NotFoundException;
import org.trailence.global.exceptions.ValidationUtils;
import org.trailence.quotas.QuotaService;
import org.trailence.storage.FileService;
import org.trailence.storage.db.FileEntity;
import org.trailence.trail.db.PhotoEntity;
import org.trailence.trail.db.PhotoRepository;
import org.trailence.trail.db.ShareEntity;
import org.trailence.trail.db.ShareRecipientEntity;
import org.trailence.trail.db.TrailEntity;
import org.trailence.trail.db.TrailRepository;
import org.trailence.trail.dto.Photo;

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
	private final QuotaService quotaService;
	private final ShareService shareService;
	
	@Autowired @Lazy @SuppressWarnings("java:S6813")
	private PhotoService self;

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
			return quotaService.addPhoto(owner, size)
			.then(
				fileService.storeFile(size, content)
				.onErrorResume(error -> quotaService.photoDeleted(owner, size).then(Mono.error(error)))
			).flatMap(fileId -> {
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
				return r2dbc.insert(entity)
				.onErrorResume(error ->
					quotaService.photoDeleted(owner, size)
					.then(fileService.deleteFile(fileId))
					.then(Mono.error(error))
				);
			});			
		}).map(this::toDto);
	}
	
    public Flux<Photo> bulkUpdate(List<Photo> dtos, Authentication auth) {
    	return BulkUtils.bulkUpdate(
    		dtos, auth.getPrincipal().toString(),
    		dto -> {
    			ValidationUtils.field("uuid", dto.getUuid()).notNull().isUuid();
	        	ValidationUtils.field("description", dto.getDescription()).nullable().maxLength(5000);
    		},
    		(entity, dto, checksAndActions) -> updateEntity(entity, dto),
    		repo, r2dbc
    	).map(this::toDto);
    }

    private boolean updateEntity(PhotoEntity entity, Photo dto) {
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
        return changed;
    }
    
    public Mono<Long> bulkDelete(List<String> uuids, Authentication auth) {
        String owner = auth.getPrincipal().toString();
        return delete(repo.findAllByUuidInAndOwner(new HashSet<>(uuids.stream().map(UUID::fromString).toList()), owner));
    }
    
    public Mono<Long> trailsDeleted(Set<UUID> trailsUuids, String owner) {
    	return delete(repo.findAllByTrailUuidInAndOwner(trailsUuids, owner));
    }
    
    private Mono<Long> delete(Flux<PhotoEntity> toDelete) {
    	return toDelete.flatMap(self::deletePhotoWithFileAndQuota, 3, 6)
    	.reduce(0L, (size, previous) -> size + previous);
    }
    
    @Transactional
    public Mono<Long> deletePhotoWithFileAndQuota(PhotoEntity entity) {
    	return repo.deleteByUuidAndOwner(entity.getUuid(), entity.getOwner())
		.flatMap(nb -> nb == 0 ? Mono.empty() :
			fileService.deleteFile(entity.getFileId()).map(FileEntity::getSize)
			.onErrorResume(e -> Mono.just(0L))
			.flatMap(size -> quotaService.photoDeleted(entity.getOwner(), size).thenReturn(size))
		);
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
    	Select sharedWithMe = shareService.selectSharedElementsWithMe(
    		email,
    		new Expression[] { AsteriskFromTable.create(PhotoEntity.TABLE) },
    		PhotoEntity.TABLE,
    		Conditions.isEqual(PhotoEntity.COL_TRAIL_UUID, TrailEntity.COL_UUID).and(Conditions.isEqual(PhotoEntity.COL_OWNER, TrailEntity.COL_OWNER)),
    		Conditions.isEqual(ShareEntity.COL_INCLUDE_PHOTOS, SQL.literalOf(true))
    	);
    			
    	Select ownedPhotos = Select.builder()
	        .select(AsteriskFromTable.create(PhotoEntity.TABLE))
	        .from(PhotoEntity.TABLE)
	        .where(Conditions.isEqual(PhotoEntity.COL_OWNER, SQL.literalOf(email)))
	        .build();
    	
    	return List.of(ownedPhotos, sharedWithMe);
    }

    public Mono<Flux<DataBuffer>> getFileContent(String owner, String uuid, Authentication auth) {
    	return getPhoto(owner, uuid, auth)
    	.flatMap(entity -> Mono.just(fileService.getFileContent(entity.getFileId())));
    }
    
    private Mono<PhotoEntity> getPhoto(String owner, String uuid, Authentication auth) {
    	String email = owner.toLowerCase();
    	Mono<PhotoEntity> getFromDB = repo.findByUuidAndOwner(UUID.fromString(uuid), email);
		String user = auth.getPrincipal().toString();
		if (email.equals(user)) return getFromDB;
		
		Select sharedWithMe = shareService.selectSharedElementsWithMe(
    		email,
    		new Expression[] { PhotoEntity.COL_UUID },
    		PhotoEntity.TABLE,
    		Conditions.isEqual(PhotoEntity.COL_TRAIL_UUID, TrailEntity.COL_UUID).and(Conditions.isEqual(PhotoEntity.COL_OWNER, TrailEntity.COL_OWNER)),
    		Conditions.isEqual(PhotoEntity.COL_UUID, SQL.literalOf(uuid))
			.and(Conditions.isEqual(ShareRecipientEntity.COL_OWNER, SQL.literalOf(owner)))
    		.and(Conditions.isEqual(ShareEntity.COL_INCLUDE_PHOTOS, SQL.literalOf(true)))
    	);
		
		return r2dbc.query(DbUtils.select(sharedWithMe, null, r2dbc), UUID.class).first().hasElement()
		.flatMap(isSharedWithMe -> {
			if (!isSharedWithMe.booleanValue()) return Mono.error(new NotFoundException("photo", uuid));
			return getFromDB;
		});
    }
	
}
