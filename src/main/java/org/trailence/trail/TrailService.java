package org.trailence.trail;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

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
import org.trailence.trail.db.TrackRepository;
import org.trailence.trail.db.TrailCollectionRepository;
import org.trailence.trail.db.TrailEntity;
import org.trailence.trail.db.TrailRepository;
import org.trailence.trail.db.TrailTagRepository;
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
    private final TrailTagRepository trailTagRepo;
    private final R2dbcEntityTemplate r2dbc;

    private Mono<Trail> createNotExisting(Trail dto, Authentication auth) {
        UUID collectionId = UUID.fromString(dto.getCollectionUuid());
        UUID originalTrackId = UUID.fromString(dto.getOriginalTrackUuid());
        UUID currentTrackId = UUID.fromString(dto.getCurrentTrackUuid());
        String owner = auth.getPrincipal().toString();

        Mono<Boolean> collectionExists = collectionRepo.existsByUuidAndOwner(collectionId, owner).map(exists -> { if (!exists) throw new NotFoundException("collection", dto.getCollectionUuid()); return true; });
        Mono<Boolean> originalTrackExists = trackRepo.existsByUuidAndOwner(originalTrackId, owner).map(exists -> { if (!exists) throw new NotFoundException("track", dto.getOriginalTrackUuid()); return true; });
        Mono<Boolean> currentTrackExists = currentTrackId.equals(originalTrackId) ? Mono.just(true) :
                trackRepo.existsByUuidAndOwner(currentTrackId, owner).map(exists -> { if (!exists) throw new NotFoundException("track", dto.getCurrentTrackUuid()); return true; });

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
            entity.setCollectionUuid(collectionId);
            entity.setOriginalTrackUuid(originalTrackId);
            entity.setCurrentTrackUuid(currentTrackId);
            entity.setCreatedAt(System.currentTimeMillis());
            entity.setUpdatedAt(entity.getCreatedAt());
            return r2dbc.insert(entity);
        }).map(this::toDTO);
    }

    public Mono<List<Trail>> bulkCreate(List<Trail> dtos, Authentication auth) {
        return repo.findAllByUuidInAndOwner(dtos.stream().map(dto -> UUID.fromString(dto.getUuid())).toList(), auth.getPrincipal().toString())
                .collectList()
                .zipWhen(known -> {
                    List<Trail> toCreate = dtos.stream().filter(
                            dto -> known.stream().noneMatch(entity -> entity.getUuid().toString().equals(dto.getUuid()) && entity.getOwner().equals(dto.getOwner()))
                    ).toList();
                    if (toCreate.isEmpty()) return Mono.just(Collections.<Trail>emptyList());
                    return Flux.fromIterable(toCreate)
                    	.flatMap(dto -> createNotExisting(dto, auth)
                    		.doOnError(e -> log.error("Error creating trail", e))
                    		.onErrorComplete(),
                    		3, 6
                    	).collectList();
                })
                .map(tuple -> Stream.concat(tuple.getT1().stream().map(this::toDTO), tuple.getT2().stream()).toList());
    }

    public Flux<Trail> bulkUpdate(List<Trail> dtos, Authentication auth) {
        String owner = auth.getPrincipal().toString();
        return repo.findAllByUuidInAndOwner(dtos.stream().map(dto -> UUID.fromString(dto.getUuid())).toList(), owner)
                .flatMap(entity -> {
                    var dtoOpt = dtos.stream().filter(dto -> dto.getUuid().equals(entity.getUuid().toString()) && owner.equals(dto.getOwner())).findAny();
                    if (dtoOpt.isEmpty()) return Mono.empty();
                    var dto = dtoOpt.get();
                    Mono<Boolean> checks = Mono.just(true);
                    Mono<?> actions = Mono.just(true);
                    var changed = false;
                    if (dto.getCollectionUuid() != null && !dto.getCollectionUuid().equals(entity.getCollectionUuid().toString())) {
                    	// change of collection: it must exist, and we should remove all associated tags
                    	var newUuid = UUID.fromString(dto.getCollectionUuid());
                    	checks = checks.flatMap(ok -> {
                    		if (!ok) return Mono.just(false);
                    		return collectionRepo.existsByUuidAndOwner(newUuid, owner);
                    	});
                    	actions = actions.then(trailTagRepo.deleteAllByTrailUuidInAndOwner(Set.of(entity.getUuid()), owner).then(Mono.just(true)));
                    	entity.setCollectionUuid(newUuid);
                    	changed = true;
                    }
                    if (dto.getCurrentTrackUuid() != null && !dto.getCurrentTrackUuid().equals(entity.getCurrentTrackUuid().toString())) {
                    	var newUuid = UUID.fromString(dto.getCurrentTrackUuid());
                    	checks = checks.flatMap(ok -> {
                    		if (!ok) return Mono.just(false);
                    		return trackRepo.existsByUuidAndOwner(newUuid, owner);
                    	});
                    	if (!entity.getCurrentTrackUuid().equals(entity.getOriginalTrackUuid()))
                    		actions = actions.then(trackRepo.deleteByUuidAndOwner(entity.getCurrentTrackUuid(), owner));
                        entity.setCurrentTrackUuid(newUuid);
                        changed = true;
                    }
                    var a = actions;
                    var c = changed;
                    return checks.flatMap(ok -> {
                    	if (!ok) return Mono.empty();
                    	return a.then(updateEntity(entity, dto, c));
                    });
                }, 3, 6)
                .collectList()
                .flatMapMany(uuids -> repo.findAllByUuidInAndOwner(uuids, owner))
                .map(this::toDTO);
    }

    private Mono<UUID> updateEntity(TrailEntity entity, Trail dto, boolean changed) {
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
        if (!changed) return Mono.empty();
        return DbUtils.updateByUuidAndOwner(r2dbc, entity)
                .flatMap(nb -> nb == 0 ? Mono.empty() : Mono.just(entity.getUuid()));
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
				trailTagRepo.deleteAllByTrailUuidInAndOwner(trailsUuids, owner).publishOn(Schedulers.parallel()),
				trackRepo.deleteAllByUuidInAndOwner(tracksUuids, owner).publishOn(Schedulers.parallel())
			).then(repo.deleteAllByUuidInAndOwner(trailsUuids, owner));
		});
    }

    public Mono<UpdateResponse<Trail>> getUpdates(List<Versioned> known, Authentication auth) {
    	return BulkGetUpdates.bulkGetUpdates(r2dbc, buildSelectAccessibleTrails(auth.getPrincipal().toString()), TrailEntity.class, known, this::toDTO);
    }

    private Select buildSelectAccessibleTrails(String email) {
        // TODO shares
        Table table = Table.create("trails");
        return Select.builder()
                .select(AsteriskFromTable.create(table))
                .from(table)
                .where(Conditions.isEqual(Column.create("owner", table), SQL.literalOf(email)))
                .build();
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
                entity.getOriginalTrackUuid().toString(),
                entity.getCurrentTrackUuid().toString(),
                entity.getCollectionUuid().toString()
        );
    }

}
