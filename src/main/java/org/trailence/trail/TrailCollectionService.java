package org.trailence.trail;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

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
import org.trailence.global.db.BulkGetUpdates;
import org.trailence.global.db.BulkUtils;
import org.trailence.global.db.DbUtils;
import org.trailence.global.dto.UpdateResponse;
import org.trailence.global.dto.Versioned;
import org.trailence.global.exceptions.ValidationUtils;
import org.trailence.quotas.QuotaService;
import org.trailence.trail.db.TrailCollectionEntity;
import org.trailence.trail.db.TrailCollectionRepository;
import org.trailence.trail.dto.TrailCollection;
import org.trailence.trail.dto.TrailCollectionType;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

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
    
    @Autowired @Lazy @SuppressWarnings("java:S6813")
    private TrailCollectionService self;

    public Mono<List<TrailCollection>> bulkCreate(List<TrailCollection> dtos, Authentication auth) {
    	String owner = auth.getPrincipal().toString();
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
    		entities -> self.createCollectionsWithQuota(entities, owner),
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
    
    private void validateCreate(TrailCollection dto) {
    	validate(dto);
    	ValidationUtils.field("type", dto.getType()).notNull().notEqualTo(TrailCollectionType.MY_TRAILS);
    }
    
    private void validate(TrailCollection dto) {
    	ValidationUtils.field("uuid", dto.getUuid()).notNull().isUuid();
    	ValidationUtils.field("name", dto.getName()).maxLength(50);
    }

    public Mono<UpdateResponse<TrailCollection>> getUpdates(List<Versioned> known, Authentication auth) {
    	return BulkGetUpdates.bulkGetUpdates(r2dbc, buildSelectAccessibleCollections(auth.getPrincipal().toString()), TrailCollectionEntity.class, known, this::toDTO);
    }

    public Flux<TrailCollection> bulkUpdate(List<TrailCollection> collections, Authentication auth) {
    	return BulkUtils.bulkUpdate(
    		collections,
    		auth.getPrincipal().toString(),
    		this::validate,
    		(entity, dto, checksAndActions) -> {
                if (entity.getName().equals(dto.getName())) return false;
                entity.setName(dto.getName());
                return true;
    		},
    		repo,
    		r2dbc
    	).map(this::toDTO);
    }

    public Mono<Void> bulkDelete(List<String> uuids, Authentication auth) {
        Set<UUID> ids = new HashSet<>(uuids.stream().map(UUID::fromString).toList());
        String owner = auth.getPrincipal().toString();
        return deleteCollections(r2dbc.query(DbUtils.select(buildSelectDeletable(ids, owner), null, r2dbc), row -> (UUID) row.get("uuid")).all(), owner);
    }
    
    public Mono<Void> deleteUser(String email) {
    	return deleteCollections(repo.findAllUuidsForUser(email), email);
    }
    
    private Mono<Void> deleteCollections(Flux<UUID> uuids, String owner) {
    	return uuids.collectList()
		.flatMap(deletable ->
	    	trailService.deleteAllFromCollections(deletable, owner)
	    	.then(tagService.deleteAllFromCollections(deletable, owner))
	    	.then(shareService.collectionsDeleted(deletable, owner))
	    	.then(self.deleteCollectionsWithQuota(deletable, owner))
	    );
    }
    
    @Transactional
    public Mono<Void> deleteCollectionsWithQuota(List<UUID> uuids, String owner) {
		log.info("Deleting {} collections for {}", uuids.size(), owner);
    	return repo.deleteAllByUuidInAndOwner(uuids, owner)
		.flatMap(nb -> quotaService.collectionsDeleted(owner, nb));
    }
    
    private Select buildSelectDeletable(Set<UUID> uuids, String owner) {
    	Table table = Table.create("collections");
    	return Select.builder()
		.select(Column.create("uuid", table))
		.from(table)
		.where(
			Conditions.in(Column.create("uuid", table), uuids.stream().map(uuid -> SQL.literalOf(uuid.toString())).toList())
			.and(Conditions.isEqual(Column.create("owner", table), SQL.literalOf(owner)))
			.and(Conditions.isEqual(Column.create("type", table), SQL.literalOf(TrailCollectionType.CUSTOM.name())))
		).build();
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
            entity.getType()
        );
    }
}
