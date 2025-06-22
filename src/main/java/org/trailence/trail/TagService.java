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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
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
import org.trailence.quotas.QuotaService;
import org.trailence.trail.db.TagEntity;
import org.trailence.trail.db.TagRepository;
import org.trailence.trail.db.TrailCollectionRepository;
import org.trailence.trail.dto.Tag;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuple4;
import reactor.util.function.Tuples;

@Service
@RequiredArgsConstructor
@Slf4j
public class TagService {

	private final TagRepository repo;
	private final TrailCollectionRepository collectionRepo;
	private final R2dbcEntityTemplate r2dbc;
	private final ShareService shareService;
	private final QuotaService quotaService;
	private final TrailTagService trailTagService;
	
	@Autowired @Lazy @SuppressWarnings("java:S6813")
	private TagService self;
	
	@SuppressWarnings("java:S3776")
	public Mono<List<Tag>> bulkCreate(List<Tag> dtos, Authentication auth) {
		List<Tag> valid = new LinkedList<>();
		Set<UUID> collectionsUuids = new HashSet<>();
		List<Throwable> errors = new LinkedList<>();
		dtos.forEach(dto -> {
			try {
				validateCreate(dto);
				valid.add(dto);
				collectionsUuids.add(UUID.fromString(dto.getCollectionUuid()));
			} catch (Exception e) {
				errors.add(e);
			}
		});
		if (valid.isEmpty()) {
			if (errors.isEmpty()) return Mono.just(List.of());
			return Mono.error(errors.getFirst());
		}
		String owner = auth.getPrincipal().toString();
		// check collectionUuid
		return collectionRepo.findExistingUuidsNotPublication(collectionsUuids, owner)
		.collectList()
		.flatMap(existingCollections -> {
			Set<UUID> uuids = new HashSet<>();
			for (var it = valid.iterator(); it.hasNext(); ) {
				var dto = it.next();
				if (existingCollections.stream().noneMatch(uuid -> uuid.toString().equals(dto.getCollectionUuid()))) {
					errors.add(new NotFoundException("collection", dto.getCollectionUuid()));
					it.remove();
				} else {
					uuids.add(UUID.fromString(dto.getUuid()));
					if (dto.getParentUuid() != null) uuids.add(UUID.fromString(dto.getParentUuid()));
				}
			}
			if (uuids.isEmpty()) return Mono.error(errors.getFirst());
			return Mono.just(uuids);
		})
		.flatMap(uuids ->
			repo.findAllByUuidInAndOwner(uuids, owner)
			.collectList()
			.map(known -> {
				List<TagEntity> created = new LinkedList<>();
				List<Tag> toCreate = new LinkedList<>();
				valid.forEach(dto -> {
					var existing = known.stream().filter(entity -> entity.getUuid().toString().equals(dto.getUuid())).findAny();
					if (existing.isPresent()) {
						created.add(existing.get());
					} else {
						toCreate.add(dto);
					}
				});
				return Tuples.of(known, created, toCreate, List.<Throwable>of());
			})
	    )
		// recursively create when parentUuid is created or null
        .expand(tuple -> createTags(tuple, owner))
        .last()
        .flatMap(tuple -> {
        	var created = tuple.getT2().stream().map(this::toDTO).toList();
        	if (!created.isEmpty()) return Mono.just(created);
        	var errors2 = tuple.getT4();
        	if (!errors2.isEmpty()) return Mono.error(errors2.getFirst());
        	if (!errors.isEmpty()) return Mono.error(errors.getFirst());
        	return Mono.just(List.of());
        });
    }
	
	private void validateCreate(Tag dto) {
		validate(dto);
		ValidationUtils.field("collectionUuid", dto.getCollectionUuid()).notNull().isUuid();
	}
	
	private void validate(Tag dto) {
		ValidationUtils.field("uuid", dto.getUuid()).notNull().isUuid();
		ValidationUtils.field("name", dto.getName()).nullable().maxLength(50);
		ValidationUtils.field("parentUuid", dto.getParentUuid()).nullable().isUuid();
	}
	
	private Mono<Tuple4<List<TagEntity>, List<TagEntity>, List<Tag>, List<Throwable>>> createTags(Tuple4<List<TagEntity>, List<TagEntity>, List<Tag>, List<Throwable>> tuple, String owner) {
		if (tuple.getT3().isEmpty())
			return Mono.empty();
    	List<Tag> canCreate = new LinkedList<>();
    	List<Tag> remaining = new LinkedList<>();
    	tuple.getT3().forEach(dto -> {
    		if (dto.getParentUuid() == null || tuple.getT1().stream().anyMatch(
    				entity -> entity.getUuid().toString().equals(dto.getParentUuid()) && entity.getCollectionUuid().toString().equals(dto.getCollectionUuid())
    		))
    			canCreate.add(dto);
    		else
    			remaining.add(dto);
    	});
    	if (canCreate.isEmpty()) {
    		if (remaining.isEmpty())
    			return Mono.empty();
    		return Mono.just(Tuples.of(tuple.getT1(), tuple.getT2(), List.of(), TrailenceUtils.merge(tuple.getT4(),
    			remaining.stream().map(dto -> (Throwable) new NotFoundException("tag", "parent " + dto.getParentUuid() + " on collection " + dto.getCollectionUuid())).toList()
    		)));
    	}
    	return BulkUtils.parallelSingleOperations(Flux.fromIterable(canCreate), dto -> self.createTagWithQuota(dto, owner))
    	.collectList()
    	.map(results -> {
    		var created = results.stream().filter(TagEntity.class::isInstance).map(e -> (TagEntity) e).toList();
    		var errors = results.stream().filter(Throwable.class::isInstance).map(e -> (Throwable) e).toList();
    		return Tuples.of(TrailenceUtils.merge(tuple.getT1(), created), TrailenceUtils.merge(tuple.getT2(), created), remaining, TrailenceUtils.merge(tuple.getT4(), errors));
    	});
	}
	
	@Transactional
	public Mono<TagEntity> createTagWithQuota(Tag dto, String owner) {
		return Mono.defer(() -> {
			TagEntity entity = new TagEntity();
			entity.setUuid(UUID.fromString(dto.getUuid()));
			entity.setOwner(owner);
			entity.setParentUuid(dto.getParentUuid() != null ? UUID.fromString(dto.getParentUuid()) : null);
			entity.setName(dto.getName());
			entity.setCollectionUuid(UUID.fromString(dto.getCollectionUuid()));
			entity.setCreatedAt(System.currentTimeMillis());
			entity.setUpdatedAt(entity.getCreatedAt());
			return r2dbc.insert(entity);
		})
		.flatMap(entity -> quotaService.addTags(owner, 1).thenReturn(entity));
    }
	
	public Mono<Void> bulkDelete(List<String> uuids, Authentication auth) {
		String owner = auth.getPrincipal().toString();
		return delete(
			repo.findAllByUuidInAndOwner(new HashSet<>(uuids.stream().map(UUID::fromString).toList()), owner)
				.expandDeep(entity -> repo.findAllByParentUuidAndOwner(entity.getUuid(), owner)),
			owner
		);
	}
	
	public Mono<Void> deleteAllFromCollections(Set<UUID> collections, String owner) {
		return delete(repo.findAllByCollectionUuidInAndOwner(collections, owner), owner);
	}
	
	private Mono<Void> delete(Flux<TagEntity> toDelete, String owner) {
		return toDelete.collectList()
		.flatMap(entities -> {
			var tagsUuids = entities.stream().map(TagEntity::getUuid).collect(Collectors.toSet());
			return trailTagService.tagsDeleted(tagsUuids, owner)
			.then(shareService.tagsDeleted(tagsUuids, owner))
			.then(self.deleteTagsWithQuota(tagsUuids, owner));
		});
	}
	
	@Transactional
	public Mono<Void> deleteTagsWithQuota(Set<UUID> uuids, String owner) {
		log.info("Deleting {} tags for {}", uuids.size(), owner);
		return repo.deleteAllByUuidInAndOwner(uuids, owner)
		.flatMap(nb -> quotaService.tagsDeleted(owner, nb))
		.then(Mono.fromRunnable(() -> log.info("Tags deleted ({} for {})", uuids.size(), owner)));
	}
	
	@SuppressWarnings("java:S3776")
	public Flux<Tag> bulkUpdate(Collection<Tag> dtos, Authentication auth) {
		String owner = auth.getPrincipal().toString();
		List<Tag> valid = new LinkedList<>();
		List<Throwable> errors = new LinkedList<>();
		Set<UUID> uuids = new HashSet<>();
		dtos.forEach(dto -> {
			try {
				validate(dto);
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
		.flatMap(entity -> associateEntityWithDto(entity, valid))
		.collectList()
		.flatMapMany(tuples -> {
			Set<UUID> newParents = new HashSet<>();
			tuples.forEach(tuple -> {
				if (tuple.getT1().getParentUuid() != null && (tuple.getT2().getParentUuid() == null || !tuple.getT1().getParentUuid().equals(tuple.getT2().getParentUuid().toString()))) {
					newParents.add(UUID.fromString(tuple.getT1().getParentUuid()));
				}
			});
			Mono<List<TagEntity>> getExistingParents =
				newParents.isEmpty() ? Mono.just(Collections.<TagEntity>emptyList()) : repo.findAllByUuidInAndOwner(newParents, owner).collectList();
			return getExistingParents.flatMapMany(existingParents ->
				Flux.fromIterable(tuples)
				.flatMap(tuple -> doUpdate(tuple.getT2(), tuple.getT1(), existingParents), 3, 6)
			)
			.collectList()
			.flatMapMany(results -> {
				var updatedUuids = results.stream().filter(UUID.class::isInstance).map(o -> (UUID) o).toList();
				if (!updatedUuids.isEmpty()) return repo.findAllByUuidInAndOwner(updatedUuids, owner);
				var errors2 = results.stream().filter(Throwable.class::isInstance).map(o -> (Throwable) o).toList();
				if (!errors2.isEmpty()) return Flux.error(errors2.getFirst());
				if (!errors.isEmpty()) return Flux.error(errors.getFirst());
				return Flux.empty();
			})
			.map(this::toDTO);
		});
	}
	
	private Mono<Tuple2<Tag, TagEntity>> associateEntityWithDto(TagEntity entity, Collection<Tag> dtos) {
		var dtoOpt = dtos.stream().filter(dto -> entity.getUuid().toString().equals(dto.getUuid())).findAny();
		if (dtoOpt.isEmpty()) return Mono.empty();
		return Mono.just(Tuples.of(dtoOpt.get(), entity));
	}
	
	private Mono<Object> doUpdate(TagEntity entity, Tag dto, List<TagEntity> existingParents) {
		if (dto.getVersion() != entity.getVersion()) return Mono.just(entity.getUuid());
		boolean updated = false;
		if (!entity.getName().equals(dto.getName())) {
			entity.setName(dto.getName());
			updated = true;
		}
		UUID newParent = dto.getParentUuid() != null ? UUID.fromString(dto.getParentUuid()) : null;
		if (!Objects.equals(entity.getParentUuid(), newParent)) {
			if (newParent != null && existingParents.stream().noneMatch(e -> e.getUuid().equals(newParent))) {
				return Mono.just(new NotFoundException("tag", newParent.toString()));
			}
			entity.setParentUuid(newParent);
			updated = true;
		}
		if (!updated) return Mono.just(entity.getUuid());
		return DbUtils.updateByUuidAndOwner(r2dbc, entity).thenReturn(entity.getUuid());
	}
	
	public Mono<UpdateResponse<Tag>> getUpdates(List<Versioned> known, Authentication auth) {
    	return BulkGetUpdates.bulkGetUpdates(repo.findAllByOwner(auth.getPrincipal().toString()), known, this::toDTO);
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
