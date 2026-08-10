package org.trailence.trail;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.sql.AliasedExpression;
import org.springframework.data.relational.core.sql.AsteriskFromTable;
import org.springframework.data.relational.core.sql.Conditions;
import org.springframework.data.relational.core.sql.Expression;
import org.springframework.data.relational.core.sql.SQL;
import org.springframework.data.relational.core.sql.Select;
import org.springframework.data.relational.core.sql.SimpleFunction;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.trailence.global.TrailenceUtils;
import org.trailence.global.db.BulkGetUpdates;
import org.trailence.global.db.BulkUtils;
import org.trailence.global.db.DbUtils;
import org.trailence.global.dto.UpdateResponse;
import org.trailence.global.dto.Versioned;
import org.trailence.global.exceptions.NotFoundException;
import org.trailence.global.exceptions.ValidationUtils;
import org.trailence.global.rest.AuthDetails;
import org.trailence.quotas.QuotaService;
import org.trailence.storage.FileService;
import org.trailence.storage.db.FileEntity;
import org.trailence.trail.db.PhotoEntity;
import org.trailence.trail.db.PhotoRepository;
import org.trailence.trail.db.PublicPhotoEntity;
import org.trailence.trail.db.ShareEntity;
import org.trailence.trail.db.ShareRecipientEntity;
import org.trailence.trail.db.SharedCollectionMemberEntity;
import org.trailence.trail.db.SharedCollectionMemberRepository;
import org.trailence.trail.db.SharedCollectionMemberRepository.SharedCollectionInfo;
import org.trailence.trail.db.TrailEntity;
import org.trailence.trail.db.TrailRepository;
import org.trailence.trail.dto.CreatePublicTrailRequest;
import org.trailence.trail.dto.Photo;
import org.trailence.trail.exceptions.TrailNotFound;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuples;

@Service
@RequiredArgsConstructor
@Slf4j
public class PhotoService {
	
	private final FileService fileService;
	private final PhotoRepository repo;
	private final TrailRepository trailRepo;
	private final R2dbcEntityTemplate r2dbc;
	private final QuotaService quotaService;
	private final ShareService shareService;
	private final SharedCollectionMemberRepository sharedCollectionMemberRepo;
	
	@Autowired @Lazy @SuppressWarnings("java:S6813")
	private PhotoService self;

	public Mono<Photo> storePhoto(Photo dto, Flux<DataBuffer> content, long size, Authentication auth) {
		ValidationUtils.field("owner", dto.getOwner()).notNull();
		ValidationUtils.field("uuid", dto.getUuid()).notNull().isUuid();
		ValidationUtils.field("trailUuid", dto.getTrailUuid()).notNull().isUuid();
		ValidationUtils.field("description", dto.getDescription()).nullable().maxLength(5000);
		String caller = TrailenceUtils.email(auth);
		Mono<Optional<SharedCollectionInfo>> shared = SharedCollectionUtils.isSharedCollectionOwner(dto.getOwner()) ?
				sharedCollectionMemberRepo.getInfoByCallerAndUuid(caller, SharedCollectionUtils.getSharedCollectionUuid(dto.getOwner()))
				.switchIfEmpty(Mono.error(new NotFoundException("shared-collection", dto.getOwner())))
				.map(Optional::of)
			: Mono.just(Optional.empty());
		return shared.flatMap(sharedOpt -> {
			var ownerInDb = sharedOpt.map(s -> SharedCollectionUtils.SHARED_OWNER_PREFIX + s.getColUuid()).orElse(caller);
			var quotaOwner = sharedOpt.map(s -> s.getColOwner()).orElse(caller);
			return Mono.zip(
				repo.findByUuidAndOwner(UUID.fromString(dto.getUuid()), ownerInDb)
					.map(Optional::of)
					.switchIfEmpty(Mono.just(Optional.empty())
				),
				trailRepo.findByUuidAndOwner(UUID.fromString(dto.getTrailUuid()), ownerInDb)
					.map(Optional::of)
					.switchIfEmpty(Mono.just(Optional.empty()))
			)
			.flatMap(existing -> {
				if (existing.getT1().isPresent()) {
					return content.then(Mono.just(toDto(existing.getT1().get(), dto.getOwner())));
				}
				if (existing.getT2().isEmpty()) {
					return Mono.error(new TrailNotFound(dto.getTrailUuid(), dto.getOwner()));
				}
				return createPhotoWithQuota(dto, ownerInDb, quotaOwner, content, size)
					.map(entity -> toDto(entity, dto.getOwner()));
			});
		});
	}
	
	public Mono<PhotoEntity> createPhotoWithQuota(Photo dto, String owner, String quotaOwner, Flux<DataBuffer> content, long size) {
		return quotaService.addPhoto(quotaOwner, size)
		.then(
			fileService.storeFile(size, content)
			.onErrorResume(error -> quotaService.photoDeleted(quotaOwner, size).then(Mono.error(error)))
		).flatMap(fileId -> {
			PhotoEntity entity = new PhotoEntity();
			entity.setUuid(UUID.fromString(dto.getUuid()));
			entity.setOwner(owner);
			entity.setCreatedAt(System.currentTimeMillis());
			entity.setFileId(fileId);
			entity.setTrailUuid(UUID.fromString(dto.getTrailUuid()));
			entity.setDescription(dto.getDescription());
			entity.setDateTaken(dto.getDateTaken());
			entity.setLatitude(dto.getLatitude());
			entity.setLongitude(dto.getLongitude());
			entity.setCover(dto.isCover());
			entity.setIndex(dto.getIndex());
			return r2dbc.insert(entity)
			.onErrorResume(error ->
				quotaService.photoDeleted(quotaOwner, size)
				.then(fileService.deleteFile(fileId))
				.then(Mono.error(error))
			);
		});
	}
	
    public Flux<Photo> bulkUpdate(List<Photo> dtos, Authentication auth) {
    	String caller = TrailenceUtils.email(auth);
    	Map<String, List<Photo>> byOwner = new HashMap<>();
    	for (var dto : dtos) {
    		String owner = SharedCollectionUtils.isSharedCollectionOwner(dto.getOwner()) ? dto.getOwner() : caller;
    		byOwner.computeIfAbsent(owner, _ -> new LinkedList<>()).add(dto);
    	}
    	return Flux.fromIterable(byOwner.entrySet())
    	.flatMap(entry -> 
    		entry.getKey().equals(caller) ? Mono.just(Tuples.of(caller, caller, entry.getValue())) :
    		sharedCollectionMemberRepo.getInfoByCallerAndUuid(caller, SharedCollectionUtils.getSharedCollectionUuid(entry.getKey()))
    		.map(s -> Tuples.of(SharedCollectionUtils.SHARED_OWNER_PREFIX + s.getColUuid(), SharedCollectionUtils.SHARED_OWNER_PREFIX + s.getMemberUuid(), entry.getValue()))
    	, 1, 1)
    	.flatMap(tuple -> {
    		String ownerInDb = tuple.getT1();
    		String ownerForCaller = tuple.getT2();
    		List<Photo> list = tuple.getT3();
    		if (!ownerInDb.equals(ownerForCaller)) {
    			for (var dto : list) dto.setOwner(ownerInDb);
    		}
        	return BulkUtils.bulkUpdate(
        		list,
        		ownerInDb,
        		dto -> {
        			ValidationUtils.field("uuid", dto.getUuid()).notNull().isUuid();
    	        	ValidationUtils.field("description", dto.getDescription()).nullable().maxLength(5000);
        		},
        		(entity, dto, _) -> updateEntity(entity, dto),
        		repo, r2dbc
        	).map(entity -> toDto(entity, ownerForCaller.equals(ownerInDb) ? null : ownerForCaller));
    	}, 1, 1);
    }
    
    public Mono<Photo> updatePhotoAsSuperUser(PhotoEntity entity, Photo dto) {
		boolean updated = this.updateEntity(entity, dto);
		if (!updated) return Mono.just(dto);
		return DbUtils.updateByUuidAndOwner(r2dbc, entity)
        .flatMap(nb -> nb == 0 ? Mono.empty() : repo.findByUuidAndOwner(entity.getUuid(), entity.getOwner()))
        .map(e -> toDto(e, null));
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
        String owner = TrailenceUtils.email(auth);
        return delete(repo.findAllByUuidInAndOwner(new HashSet<>(uuids.stream().map(UUID::fromString).toList()), owner), owner);
    }
    
    public Mono<Long> bulkDelete(String shareId, List<String> uuids, Authentication auth) {
    	return sharedCollectionMemberRepo.getInfoByCallerAndUuid(TrailenceUtils.email(auth), SharedCollectionUtils.getSharedCollectionUuid(shareId))
    	.flatMap(share -> delete(repo.findAllByUuidInAndOwner(new HashSet<>(uuids.stream().map(UUID::fromString).toList()), SharedCollectionUtils.SHARED_OWNER_PREFIX + share.getColUuid()), share.getColOwner()))
    	.switchIfEmpty(Mono.just(0L));
    }
    
    public Mono<Long> trailsDeleted(Set<UUID> trailsUuids, String owner, String quotaOwner) {
    	return delete(repo.findAllByTrailUuidInAndOwner(trailsUuids, owner), quotaOwner);
    }
    
    private Mono<Long> delete(Flux<PhotoEntity> toDelete, String quotaOwner) {
    	return toDelete.flatMap(entity -> self.deletePhotoWithFileAndQuota(entity, quotaOwner), 1, 4)
    	.reduce(0L, (size, previous) -> size + previous);
    }
    
    @Transactional
    public Mono<Long> deletePhotoWithFileAndQuota(PhotoEntity entity, String quotaOwner) {
		log.info("Deleting photo {}", entity.getOwner());
    	return repo.deleteByUuidAndOwner(entity.getUuid(), entity.getOwner())
		.flatMap(nb -> nb == 0 ? Mono.empty() :
			fileService.deleteFile(entity.getFileId()).map(FileEntity::getSize)
			.onErrorResume(_ -> Mono.just(0L))
			.flatMap(size -> quotaService.photoDeleted(quotaOwner, size).thenReturn(size))
		);
    }
    
    public Mono<UUID> transferToPublic(UUID uuid, String owner, UUID publicTrailUuid, CreatePublicTrailRequest.Photo publicPhoto) {
    	UUID newUuid = UUID.randomUUID();
    	return repo.findByUuidAndOwner(uuid, owner)
    	.switchIfEmpty(Mono.error(new NotFoundException("photo", uuid.toString() + "-" + owner)))
    	.flatMap(privateEntity ->
    		fileService.getFileSize(privateEntity.getFileId())
    		.flatMap(fileSize ->
    			r2dbc.insert(new PublicPhotoEntity(
    				newUuid,
        			publicTrailUuid,
        			privateEntity.getFileId(),
        			publicPhoto.getDescription(),
        			publicPhoto.getDate(),
        			publicPhoto.getLat(),
        			publicPhoto.getLng(),
        			publicPhoto.getIndex()
        		))
    			.then(repo.deleteByUuidAndOwner(privateEntity.getUuid(), privateEntity.getOwner()))
    			.then(quotaService.photoDeleted(owner, fileSize))
    		)
    	).thenReturn(newUuid);
    }
	
	public Photo toDto(PhotoEntity entity, String sharedOwnerForCaller) {
		return new Photo(
			entity.getUuid().toString(),
			sharedOwnerForCaller != null ? sharedOwnerForCaller : entity.getOwner(),
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
	
    @Data
    @EqualsAndHashCode(callSuper = true)
    @NoArgsConstructor
    public static class PhotoWithSharedCollectionInfo extends PhotoEntity {
    	private static final String COL_NAME = "shared_collection_uuid_for_caller";
    	private UUID sharedCollectionUuidForCaller;
    }

	
    public Mono<UpdateResponse<Photo>> getUpdates(List<Versioned> known, Authentication auth) {
    	return BulkGetUpdates.bulkGetUpdates(
    		r2dbc,
    		buildSelectAccessiblePhotos(auth),
    		PhotoWithSharedCollectionInfo.class,
    		photo -> {
    			String key = photo.getOwner() + " " + photo.getUuid().toString();
    			// to match known, we need to set the owner to the caller view
    			if (photo.getSharedCollectionUuidForCaller() != null)
    				photo.setOwner(SharedCollectionUtils.SHARED_OWNER_PREFIX + photo.getSharedCollectionUuidForCaller());
    			return key;
    		},
    		known,
    		entity -> toDto(entity, entity.getSharedCollectionUuidForCaller() != null ? SharedCollectionUtils.SHARED_OWNER_PREFIX + entity.getSharedCollectionUuidForCaller() : null)
    	);
    }

    private List<Select> buildSelectAccessiblePhotos(Authentication auth) {
    	String caller = TrailenceUtils.email(auth);
    	Select sharedWithMe = shareService.selectSharedElementsWithMe(
    		caller,
    		new Expression[] { AsteriskFromTable.create(PhotoEntity.TABLE), new AliasedExpression(SQL.nullLiteral(), PhotoWithSharedCollectionInfo.COL_NAME) },
    		PhotoEntity.TABLE,
    		Conditions.isEqual(PhotoEntity.COL_TRAIL_UUID, TrailEntity.COL_UUID).and(Conditions.isEqual(PhotoEntity.COL_OWNER, TrailEntity.COL_OWNER)),
    		Conditions.isEqual(ShareEntity.COL_INCLUDE_PHOTOS, SQL.literalOf(true))
    	);
    			
    	Select ownedPhotos = Select.builder()
	        .select(AsteriskFromTable.create(PhotoEntity.TABLE), new AliasedExpression(SQL.nullLiteral(), PhotoWithSharedCollectionInfo.COL_NAME))
	        .from(PhotoEntity.TABLE)
	        .where(Conditions.isEqual(PhotoEntity.COL_OWNER, SQL.literalOf(caller)))
	        .build();
    	
    	if (AuthDetails.getVersion(auth) < SharedCollectionUtils.MIN_VERSION_FOR_SHARED)
    		return List.of(ownedPhotos, sharedWithMe);
    	
    	Select fromSharedCollections = Select.builder()
    		.select(AsteriskFromTable.create(PhotoEntity.TABLE), SharedCollectionMemberEntity.COL_UUID.as(PhotoWithSharedCollectionInfo.COL_NAME))
    		.from(SharedCollectionMemberEntity.TABLE)
    		.join(PhotoEntity.TABLE)
    			.on(Conditions.isEqual(
    				PhotoEntity.COL_OWNER,
	        		SimpleFunction.create("CONCAT", List.of(SQL.literalOf(SharedCollectionUtils.SHARED_OWNER_PREFIX), SharedCollectionMemberEntity.COL_SHARED_COLLECTION_UUID))
	        	))
    		.where(Conditions.isEqual(SharedCollectionMemberEntity.COL_OWNER, SQL.literalOf(caller)))
    		.build();
    	
    	return List.of(ownedPhotos, sharedWithMe, fromSharedCollections);
    }

    public Mono<Flux<DataBuffer>> getFileContent(String owner, String uuid, Authentication auth) {
    	return getPhoto(owner, uuid, auth)
    	.flatMap(entity -> Mono.just(fileService.getFileContent(entity.getFileId())));
    }
    
    private Mono<PhotoEntity> getPhoto(String owner, String uuid, Authentication auth) {
    	String email = owner.toLowerCase();
		String user = TrailenceUtils.email(auth);
    	
    	Mono<PhotoEntity> getFromDB = repo.findByUuidAndOwner(UUID.fromString(uuid), email);
		if (email.equals(user)) return getFromDB;
		
		if (SharedCollectionUtils.isSharedCollectionOwner(email)) {
			return sharedCollectionMemberRepo.findByUuidAndOwner(SharedCollectionUtils.getSharedCollectionUuid(email), user)
			.flatMap(shared -> repo.findByUuidAndOwner(UUID.fromString(uuid), SharedCollectionUtils.SHARED_OWNER_PREFIX + shared.getSharedCollectionUuid()));
		}
		
		Select sharedWithMe = shareService.selectSharedElementsWithMe(
    		user,
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
