package org.trailence.trail;

import java.util.List;
import java.util.UUID;

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
import org.trailence.trail.db.TrailCollectionEntity;
import org.trailence.trail.db.TrailCollectionRepository;
import org.trailence.trail.dto.TrailCollection;
import org.trailence.trail.dto.TrailCollectionType;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
@RequiredArgsConstructor
public class TrailCollectionService {

    private final TrailCollectionRepository repo;
    private final R2dbcEntityTemplate r2dbc;
    private final TrailService trailService;
    private final TagService tagService;

    public Mono<TrailCollection> create(TrailCollection dto, Authentication auth) {
        UUID id = UUID.fromString(dto.getUuid());
        String owner = auth.getPrincipal().toString();
        return repo.findByUuidAndOwner(id, owner)
            .switchIfEmpty(Mono.defer(() -> {
                TrailCollectionEntity entity = new TrailCollectionEntity();
                entity.setUuid(id);
                entity.setOwner(owner);
                entity.setName(dto.getName());
                entity.setType(dto.getType());
                entity.setCreatedAt(System.currentTimeMillis());
                entity.setUpdatedAt(entity.getCreatedAt());
                return r2dbc.insert(entity);
            }))
            .map(this::toDTO);
    }

    public Flux<TrailCollection> bulkCreate(List<TrailCollection> dtos, Authentication auth) {
        return Flux.fromIterable(dtos)
                .publishOn(Schedulers.parallel())
                .flatMap(dto -> create(dto, auth), 3, 6);
    }

    public Mono<UpdateResponse<TrailCollection>> getUpdates(List<Versioned> known, Authentication auth) {
    	return BulkGetUpdates.bulkGetUpdates(r2dbc, buildSelectAccessibleCollections(auth.getPrincipal().toString()), TrailCollectionEntity.class, known, this::toDTO);
    }

    public Flux<TrailCollection> bulkUpdate(List<TrailCollection> collections, Authentication auth) {
        return repo.findAllByUuidInAndOwner(collections.stream().map(c -> UUID.fromString(c.getUuid())).toList(), auth.getPrincipal().toString())
            .flatMap(entity -> {
                var dtoOpt = collections.stream().filter(c -> c.getUuid().equals(entity.getUuid().toString())).findAny();
                if (dtoOpt.isEmpty()) return Mono.empty();
                var dto = dtoOpt.get();
                entity.setName(dto.getName());
                return DbUtils.updateByUuidAndOwner(r2dbc, entity)
                        .flatMap(nb -> nb == 0 ? Mono.empty() : repo.findByUuidAndOwner(entity.getUuid(), entity.getOwner()));
            }, 3, 6)
            .map(this::toDTO);
    }

    public Mono<Void> bulkDelete(List<String> uuids, Authentication auth) {
        List<UUID> ids = uuids.stream().map(UUID::fromString).toList();
        String owner = auth.getPrincipal().toString();
        return r2dbc.query(DbUtils.select(buildSelectDeletable(ids, owner), null, r2dbc), row -> (UUID) row.get("uuid"))
        .all().collectList()
        .flatMap(deletable ->
        	Mono.zip(
        		trailService.deleteAllFromCollections(deletable, owner).publishOn(Schedulers.parallel()),
        		tagService.deleteAllFromCollections(deletable, owner).publishOn(Schedulers.parallel())
        	)
        	.then(repo.deleteAllByUuidInAndOwner(deletable, owner))
        );
    }
    
    private Select buildSelectDeletable(List<UUID> uuids, String owner) {
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
        // TODO shares
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
