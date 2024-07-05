package org.trailence.trail;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.trailence.global.TrailenceUtils;
import org.trailence.global.db.BulkGetUpdates;
import org.trailence.global.db.DbUtils;
import org.trailence.global.dto.UpdateResponse;
import org.trailence.global.dto.Versioned;
import org.trailence.trail.db.TagEntity;
import org.trailence.trail.db.TagRepository;
import org.trailence.trail.db.TrailCollectionRepository;
import org.trailence.trail.db.TrailTagRepository;
import org.trailence.trail.dto.Tag;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuples;

@Service
@RequiredArgsConstructor
@Slf4j
public class TagService {

	private final TagRepository repo;
	private final TrailCollectionRepository collectionRepo;
	private final TrailTagRepository trailTagRepo;
	private final R2dbcEntityTemplate r2dbc;
	
	public Mono<List<Tag>> bulkCreate(List<Tag> dtos, Authentication auth) {
		String owner = auth.getPrincipal().toString();
		Set<UUID> collectionsUuids = new HashSet<>();
		dtos.forEach(dto -> collectionsUuids.add(UUID.fromString(dto.getCollectionUuid())));
		// check collectionUuid
		return collectionRepo.findAllByUuidInAndOwner(collectionsUuids, owner)
		.map(collection -> collection.getUuid())
		.collectList()
		.map(existingCollections -> {
			Set<UUID> uuids = new HashSet<>();
			dtos.forEach(dto -> {
				if (!existingCollections.stream().anyMatch(uuid -> uuid.toString().equals(dto.getCollectionUuid()))) return;
				uuids.add(UUID.fromString(dto.getUuid()));
				if (dto.getParentUuid() != null) uuids.add(UUID.fromString(dto.getParentUuid()));
			});
			return uuids;
		})
		.flatMapMany(uuids ->
			repo.findAllByUuidInAndOwner(uuids, owner)
			.collectList()
	        .zipWhen(known -> Mono.just(dtos.stream().filter(dto ->
	        	// has existing collection
	        	uuids.stream().anyMatch(uuid -> uuid.toString().equals(dto.getUuid())) &&
	        	// not in known list
	            known.stream().noneMatch(entity -> entity.getUuid().toString().equals(dto.getUuid()))
	        ).toList()))
	    )
		// recursively create when parentUuid is created or null
        .expand(tuple -> {
        	if (tuple.getT2().isEmpty()) return Mono.empty();
        	List<Tag> canCreate = new LinkedList<>();
        	List<Tag> remaining = new LinkedList<>();
        	tuple.getT2().forEach(dto -> {
        		if (dto.getParentUuid() == null || tuple.getT1().stream().anyMatch(entity -> entity.getUuid().toString().equals(dto.getParentUuid())))
        			canCreate.add(dto);
        		else
        			remaining.add(dto);
        	});
        	if (canCreate.isEmpty()) return Mono.empty();
        	return Flux.fromIterable(canCreate).flatMap(dto ->
        		createNotExisting(dto, owner)
        		.doOnError(e -> log.error("Error creating tag", e))
        		.onErrorComplete()
        		, 3, 6
        	)
        	.collectList()
        	.map(created -> Tuples.of(TrailenceUtils.merge(tuple.getT1(), created), remaining));
        })
        .last()
        .map(tuple -> tuple.getT1().stream().map(this::toDTO).toList());
    }
	
	private Mono<TagEntity> createNotExisting(Tag dto, String owner) {
		TagEntity entity = new TagEntity();
		entity.setUuid(UUID.fromString(dto.getUuid()));
		entity.setOwner(owner);
		entity.setParentUuid(dto.getParentUuid() != null ? UUID.fromString(dto.getParentUuid()) : null);
		entity.setName(dto.getName());
		entity.setCollectionUuid(UUID.fromString(dto.getCollectionUuid()));
		entity.setCreatedAt(System.currentTimeMillis());
		entity.setUpdatedAt(entity.getCreatedAt());
		return r2dbc.insert(entity);
    }
	
	public Mono<Void> bulkDelete(List<String> uuids, Authentication auth) {
		String owner = auth.getPrincipal().toString();
		return delete(
			repo.findAllByUuidInAndOwner(uuids.stream().map(UUID::fromString).toList(), owner)
				.expandDeep(entity -> repo.findAllByParentUuidAndOwner(entity.getUuid(), owner)),
			owner
		);
	}
	
	public Mono<Void> deleteAllFromCollections(List<UUID> collections, String owner) {
		return delete(repo.findAllByCollectionUuidInAndOwner(collections, owner), owner);
	}
	
	private Mono<Void> delete(Flux<TagEntity> toDelete, String owner) {
		return toDelete.collectList()
		.flatMap(entities -> {
			var tagsUuids = entities.stream().map(TagEntity::getUuid).collect(Collectors.toSet());
			return trailTagRepo.deleteAllByTagUuidInAndOwner(tagsUuids, owner)
			.then(repo.deleteAllByUuidInAndOwner(tagsUuids, owner));
		});
	}
	
	public Flux<Tag> bulkUpdate(Collection<Tag> dtos, Authentication auth) {
		return repo.findAllByUuidInAndOwner(dtos.stream().map(dto -> UUID.fromString(dto.getUuid())).toList(), auth.getPrincipal().toString())
		.flatMap(entity -> {
			var dtoOpt = dtos.stream().filter(dto -> entity.getUuid().toString().equals(dto.getUuid())).findAny();
			if (dtoOpt.isEmpty()) return Mono.empty();
			return Mono.just(Tuples.of(dtoOpt.get(), entity));
		})
		.collectList()
		.flatMapMany(tuples -> {
			Set<UUID> newParents = new HashSet<>();
			tuples.forEach(tuple -> {
				if (tuple.getT1().getParentUuid() != null && (tuple.getT2().getParentUuid() == null || !tuple.getT1().getParentUuid().equals(tuple.getT2().getParentUuid().toString()))) {
					newParents.add(UUID.fromString(tuple.getT1().getParentUuid()));
				}
			});
			return (newParents.isEmpty() ? Mono.just(Collections.<TagEntity>emptyList()) : repo.findAllByUuidInAndOwner(newParents, auth.getPrincipal().toString()).collectList())
			.flatMapMany(existingParents ->
				Flux.fromIterable(tuples)
				.flatMap(tuple -> {
					Tag dto = tuple.getT1();
					TagEntity entity = tuple.getT2();
					boolean updated = false;
					if (!entity.getName().equals(dto.getName())) {
						entity.setName(dto.getName());
						updated = true;
					}
					UUID newParent = dto.getParentUuid() != null ? UUID.fromString(dto.getParentUuid()) : null;
					if (!Objects.equals(entity.getParentUuid(), newParent)) {
						if (newParent == null || existingParents.stream().anyMatch(e -> e.getUuid().equals(newParent))) {
							entity.setParentUuid(newParent);
							updated = true;
						}
					}
					if (!updated) return Mono.empty();
					return DbUtils.updateByUuidAndOwner(r2dbc, entity).thenReturn(entity.getUuid());
				}, 3, 6)
			)
			.collectList()
			.flatMapMany(uuids -> repo.findAllByUuidInAndOwner(uuids, auth.getPrincipal().toString()))
			.map(this::toDTO);
		});
	}
	
	public Mono<UpdateResponse<Tag>> getUpdates(List<Versioned> known, Authentication auth) {
    	return BulkGetUpdates.bulkGetUpdates(r2dbc, repo.findAllByOwner(auth.getPrincipal().toString()), known, this::toDTO);
    }
	
	private Tag toDTO(TagEntity entity) {
		return new Tag(
			entity.getUuid().toString(),
			entity.getOwner(),
			entity.getVersion(),
			entity.getCreatedAt(),
			entity.getUpdatedAt(),
			entity.getName(),
			entity.getParentUuid() != null ? entity.getParentUuid().toString() : null,
			entity.getCollectionUuid().toString()
		);
	}
	
}
