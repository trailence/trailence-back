package org.trailence.trail;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.sql.AsteriskFromTable;
import org.springframework.data.relational.core.sql.Column;
import org.springframework.data.relational.core.sql.Conditions;
import org.springframework.data.relational.core.sql.SQL;
import org.springframework.data.relational.core.sql.Select;
import org.springframework.data.relational.core.sql.Table;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.trailence.global.db.BulkGetUpdates;
import org.trailence.global.db.DbUtils;
import org.trailence.global.dto.UpdateResponse;
import org.trailence.global.dto.Versioned;
import org.trailence.global.exceptions.NotFoundException;
import org.trailence.storage.FileService;
import org.trailence.trail.db.PhotoEntity;
import org.trailence.trail.db.PhotoRepository;
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
	private final R2dbcEntityTemplate r2dbc;

	public Mono<Photo> storePhoto(
		String uuid, String trailUuid,
		String description, Long dateTaken, Long latitude, Long longitude,
		boolean isCover, int index,
		Flux<DataBuffer> content, long size,
		Authentication auth
	) {
		String owner = auth.getPrincipal().toString();
		return repo.findByUuidAndOwner(UUID.fromString(uuid), owner)
		.map(Optional::of)
		.switchIfEmpty(Mono.just(Optional.empty()))
		.flatMap(existing -> {
			if (existing.isPresent()) {
				return content.then(Mono.just(existing.get()));
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
        return repo.findAllByUuidInAndOwner(dtos.stream().map(dto -> UUID.fromString(dto.getUuid())).toList(), owner)
        .flatMap(entity -> {
            var dtoOpt = dtos.stream().filter(dto -> dto.getUuid().equals(entity.getUuid().toString()) && owner.equals(dto.getOwner())).findAny();
            if (dtoOpt.isEmpty()) return Mono.empty();
            var dto = dtoOpt.get();
            return updateEntity(entity, dto);
        }, 3, 6)
        .collectList()
        .flatMapMany(uuids -> repo.findAllByUuidInAndOwner(uuids, owner))
        .map(this::toDto);
    }

    private Mono<UUID> updateEntity(PhotoEntity entity, Photo dto) {
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
        if (!changed) return Mono.empty();
        return DbUtils.updateByUuidAndOwner(r2dbc, entity)
        .flatMap(nb -> nb == 0 ? Mono.empty() : Mono.just(entity.getUuid()));
    }
    
    public Mono<Void> bulkDelete(List<String> uuids, Authentication auth) {
        String owner = auth.getPrincipal().toString();
        return delete(repo.findAllByUuidInAndOwner(uuids.stream().map(UUID::fromString).toList(), owner));
    }
    
    public Mono<Void> trailsDeleted(Set<UUID> trailsUuids, String owner) {
    	return delete(repo.findAllByTrailUuidInAndOwner(trailsUuids, owner));
    }
    
    private Mono<Void> delete(Flux<PhotoEntity> toDelete) {
    	return toDelete.flatMap(
    		entity -> repo.deleteByUuidAndOwner(entity.getUuid(), entity.getOwner())
    			.then(fileService.deleteFile(entity.getFileId()))
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
    	Table shares = Table.create("shares");
    	Table share_elements = Table.create("share_elements");
        Table trails = Table.create("trails");
        Table trails_tags = Table.create("trails_tags");
        Table photos = Table.create("photos");
        
        Select sharedCollections = Select.builder()
    		.select(AsteriskFromTable.create(photos))
    		.from(shares)
    		.join(share_elements).on(Conditions.isEqual(Column.create("share_uuid", share_elements), Column.create("uuid", shares)).and(Conditions.isEqual(Column.create("owner", share_elements), Column.create("from_email", shares))))
    		.join(trails).on(Conditions.isEqual(Column.create("collection_uuid", trails), Column.create("element_uuid", share_elements)).and(Conditions.isEqual(Column.create("owner", trails), Column.create("from_email", shares))))
    		.join(photos).on(Conditions.isEqual(Column.create("trail_uuid", photos), Column.create("uuid", trails)).and(Conditions.isEqual(Column.create("owner", photos), Column.create("owner", trails))))
    		.where(
    			Conditions.isEqual(Column.create("to_email", shares), SQL.literalOf(email))
    			.and(Conditions.isEqual(Column.create("element_type", shares), SQL.literalOf(ShareElementType.COLLECTION.name())))
    			.and(Conditions.isEqual(Column.create("include_photos", shares), SQL.literalOf(true)))
    		)
    		.build();
    	
    	Select sharedTags = Select.builder()
			.select(AsteriskFromTable.create(photos))
    		.from(shares)
    		.join(share_elements).on(Conditions.isEqual(Column.create("share_uuid", share_elements), Column.create("uuid", shares)).and(Conditions.isEqual(Column.create("owner", share_elements), Column.create("from_email", shares))))
    		.join(trails_tags).on(Conditions.isEqual(Column.create("tag_uuid", trails_tags), Column.create("element_uuid", share_elements)).and(Conditions.isEqual(Column.create("owner", trails_tags), Column.create("from_email", shares))))
    		.join(photos).on(Conditions.isEqual(Column.create("trail_uuid", trails_tags), Column.create("trail_uuid", photos)).and(Conditions.isEqual(Column.create("owner", photos), Column.create("from_email", shares))))
    		.where(
    			Conditions.isEqual(Column.create("to_email", shares), SQL.literalOf(email))
    			.and(Conditions.isEqual(Column.create("element_type", shares), SQL.literalOf(ShareElementType.TAG.name())))
    			.and(Conditions.isEqual(Column.create("include_photos", shares), SQL.literalOf(true)))
    		)
    		.build();
    	
    	Select sharedTrails = Select.builder()
			.select(AsteriskFromTable.create(photos))
    		.from(shares)
    		.join(share_elements).on(Conditions.isEqual(Column.create("share_uuid", share_elements), Column.create("uuid", shares)).and(Conditions.isEqual(Column.create("owner", share_elements), Column.create("from_email", shares))))
    		.join(photos).on(Conditions.isEqual(Column.create("element_uuid", share_elements), Column.create("trail_uuid", photos)).and(Conditions.isEqual(Column.create("owner", photos), Column.create("from_email", shares))))
    		.where(
    			Conditions.isEqual(Column.create("to_email", shares), SQL.literalOf(email))
    			.and(Conditions.isEqual(Column.create("element_type", shares), SQL.literalOf(ShareElementType.TRAIL.name())))
    			.and(Conditions.isEqual(Column.create("include_photos", shares), SQL.literalOf(true)))
    		)
    		.build();
    	
    	Select ownedPhotos = Select.builder()
	        .select(AsteriskFromTable.create(photos))
	        .from(photos)
	        .where(Conditions.isEqual(Column.create("owner", photos), SQL.literalOf(email)))
	        .build();
    	
    	return List.of(ownedPhotos, sharedTrails, sharedTags, sharedCollections);
    }

    public Mono<Flux<DataBuffer>> getFileContent(String owner, String uuid, Authentication auth) {
    	return getPhoto(owner, uuid, auth)
    	.flatMap(entity -> Mono.just(fileService.getFileContent(entity.getFileId())));
    }
    
    private Mono<PhotoEntity> getPhoto(String owner, String uuid, Authentication auth) {
    	Mono<PhotoEntity> getFromDB = repo.findByUuidAndOwner(UUID.fromString(uuid), owner);
		if (owner.equals(auth.getPrincipal().toString())) return getFromDB;
		String user = auth.getPrincipal().toString();
		return isFromSharedTrail(uuid, owner, user)
		.flatMap(sharedTrail -> {
			if (sharedTrail.booleanValue()) return getFromDB;
			return isFromSharedTag(uuid, owner, user)
			.flatMap(sharedTag -> {
				if (sharedTag.booleanValue()) return getFromDB;
				return isFromSharedCollection(uuid, owner, user)
				.flatMap(sharedCollection -> {
					if (sharedCollection.booleanValue()) return getFromDB;
					return Mono.error(new NotFoundException("track", uuid));
				});
			});
		});
    }
	
	private Mono<Boolean> isFromSharedTrail(String photoUuid, String photoOwner, String user) {
		Table share_elements = Table.create("share_elements");
		Table shares = Table.create("shares");
		Table photos = Table.create("photos");
		var select = Select.builder()
			.select(Column.create("uuid", photos))
			.from(photos)
			.join(share_elements).on(Conditions.isEqual(Column.create("element_uuid", share_elements), Column.create("trail_uuid", photos)).and(Conditions.isEqual(Column.create("owner", photos), Column.create("owner", share_elements))))
			.join(shares).on(
				Conditions.isEqual(Column.create("share_uuid", share_elements), Column.create("uuid", shares))
				.and(Conditions.isEqual(Column.create("element_type", shares), SQL.literalOf(ShareElementType.TRAIL.name())))
				.and(Conditions.isEqual(Column.create("include_photos", shares), SQL.literalOf(true)))
				.and(Conditions.isEqual(Column.create("from_email", shares), SQL.literalOf(photoOwner)))
				.and(Conditions.isEqual(Column.create("to_email", shares), SQL.literalOf(user)))
			)
			.limit(1)
			.where(
				Conditions.isEqual(Column.create("owner", photos), SQL.literalOf(photoOwner))
				.and(Conditions.isEqual(Column.create("uuid", photos), SQL.literalOf(photoUuid)))
			)
			.build();
		return r2dbc.query(DbUtils.select(select, null, r2dbc), UUID.class).first().hasElement();
	}
	
	private Mono<Boolean> isFromSharedTag(String photoUuid, String photoOwner, String user) {
		Table share_elements = Table.create("share_elements");
		Table shares = Table.create("shares");
		Table trails_tags = Table.create("trails_tags");
		Table photos = Table.create("photos");
		var select = Select.builder()
			.select(Column.create("uuid", photos))
			.from(photos)
			.join(trails_tags).on(Conditions.isEqual(Column.create("trail_uuid", trails_tags), Column.create("trail_uuid", photos)))
			.join(share_elements).on(Conditions.isEqual(Column.create("element_uuid", share_elements), Column.create("tag_uuid", trails_tags)).and(Conditions.isEqual(Column.create("owner", share_elements), Column.create("owner", photos))))
			.join(shares).on(
				Conditions.isEqual(Column.create("share_uuid", share_elements), Column.create("uuid", shares))
				.and(Conditions.isEqual(Column.create("element_type", shares), SQL.literalOf(ShareElementType.TAG.name())))
				.and(Conditions.isEqual(Column.create("include_photos", shares), SQL.literalOf(true)))
				.and(Conditions.isEqual(Column.create("from_email", shares), SQL.literalOf(photoOwner)))
				.and(Conditions.isEqual(Column.create("to_email", shares), SQL.literalOf(user)))
			)
			.limit(1)
			.where(
				Conditions.isEqual(Column.create("owner", photos), SQL.literalOf(photoOwner))
				.and(Conditions.isEqual(Column.create("uuid", photos), SQL.literalOf(photoUuid)))
			)
			.build();
		return r2dbc.query(DbUtils.select(select, null, r2dbc), UUID.class).first().hasElement();
	}
	
	private Mono<Boolean> isFromSharedCollection(String photoUuid, String photoOwner, String user) {
		Table trails = Table.create("trails");
		Table share_elements = Table.create("share_elements");
		Table shares = Table.create("shares");
		Table photos = Table.create("photos");
		var select = Select.builder()
			.select(Column.create("uuid", photos))
			.from(photos)
			.join(trails).on(Conditions.isEqual(Column.create("trail_uuid", photos), Column.create("uuid", trails)).and(Conditions.isEqual(Column.create("owner", photos), Column.create("owner", trails))))
			.join(share_elements).on(Conditions.isEqual(Column.create("element_uuid", share_elements), Column.create("collection_uuid", trails)).and(Conditions.isEqual(Column.create("owner", share_elements), SQL.literalOf(photoOwner))))
			.join(shares).on(
				Conditions.isEqual(Column.create("share_uuid", share_elements), Column.create("uuid", shares))
				.and(Conditions.isEqual(Column.create("element_type", shares), SQL.literalOf(ShareElementType.COLLECTION.name())))
				.and(Conditions.isEqual(Column.create("include_photos", shares), SQL.literalOf(true)))
				.and(Conditions.isEqual(Column.create("from_email", shares), SQL.literalOf(photoOwner)))
				.and(Conditions.isEqual(Column.create("to_email", shares), SQL.literalOf(user)))
			)
			.limit(1)
			.where(
				Conditions.isEqual(Column.create("owner", photos), SQL.literalOf(photoOwner))
				.and(Conditions.isEqual(Column.create("uuid", photos), SQL.literalOf(photoUuid)))
			)
			.build();
		return r2dbc.query(DbUtils.select(select, null, r2dbc), UUID.class).first().hasElement();
	}

}
