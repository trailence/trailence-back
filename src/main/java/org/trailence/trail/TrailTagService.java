package org.trailence.trail;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.trailence.global.exceptions.BadRequestException;
import org.trailence.global.exceptions.ValidationUtils;
import org.trailence.trail.db.TagRepository;
import org.trailence.trail.db.TrailRepository;
import org.trailence.trail.db.TrailTagEntity;
import org.trailence.trail.db.TrailTagRepository;
import org.trailence.trail.dto.TrailTag;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

@Service
@RequiredArgsConstructor
public class TrailTagService {

	private final TrailTagRepository repo;
	private final TrailRepository trailRepo;
	private final TagRepository tagRepo;
	private final R2dbcEntityTemplate r2dbc;
	
	public Flux<TrailTag> getAll(Authentication auth) {
		return repo.findAllByOwner(auth.getPrincipal().toString()).map(this::toDto);
	}
	
	public Flux<TrailTag> bulkCreate(Collection<TrailTag> dtos, Authentication auth) {
		String owner = auth.getPrincipal().toString();
		Set<UUID> tagsUuids = new HashSet<>();
		Set<UUID> trailsUuids = new HashSet<>();
		Set<Tuple2<UUID, UUID>> pairs = new HashSet<>();
		List<Throwable> errors = new LinkedList<>();
		dtos.forEach(dto -> {
			try {
				ValidationUtils.field("tagUuid", dto.getTagUuid()).notNull().isUuid();
				ValidationUtils.field("trailUuid", dto.getTrailUuid()).notNull().isUuid();
				var tagUuid = UUID.fromString(dto.getTagUuid());
				tagsUuids.add(tagUuid);
				var trailUuid = UUID.fromString(dto.getTrailUuid());
				trailsUuids.add(trailUuid);
				pairs.add(Tuples.of(tagUuid, trailUuid));
			} catch (Exception e) {
				errors.add(e);
			}
		});
		if (pairs.isEmpty()) {
			if (errors.isEmpty()) return Flux.empty();
			return Flux.error(errors.getFirst());
		}
		return Mono.zip(
			trailRepo.findAllByUuidInAndOwner(trailsUuids, owner).collectList().publishOn(Schedulers.parallel()),
			tagRepo.findAllByUuidInAndOwner(tagsUuids, owner).collectList().publishOn(Schedulers.parallel())
		).flatMapMany(existing -> {
			var valid = pairs.stream().filter(tuple -> {
				var trail = existing.getT1().stream().filter(t -> t.getUuid().equals(tuple.getT2())).findAny();
				if (trail.isEmpty()) return false;
				var tag = existing.getT2().stream().filter(t -> t.getUuid().equals(tuple.getT1())).findAny();
				if (tag.isEmpty()) return false;
				return trail.get().getCollectionUuid().equals(tag.get().getCollectionUuid());
			}).toList();
			if (valid.isEmpty()) return Flux.error(new BadRequestException("invalid-input", "trailUuid or tagUuid not found, or they do not belong to the same collection"));
			return Flux.fromIterable(valid);
		}).map(tuple -> {
			var dto = dtos.stream().filter(d -> tuple.getT1().toString().equals(d.getTagUuid()) && tuple.getT2().toString().equals(d.getTrailUuid())).findAny();
			return toEntity(dto.get(), owner);
		})
		.flatMap(toCreate ->
			r2dbc.insert(toCreate)
			.onErrorResume(DuplicateKeyException.class, e -> Mono.just(toCreate)),
			3, 6
		)
		.map(this::toDto);
	}
	
	public Mono<Void> bulkDelete(Collection<TrailTag> dtos, Authentication auth) {
		String owner = auth.getPrincipal().toString();
		return Flux.fromIterable(new HashSet<>(dtos.stream().map(dto -> Tuples.of(UUID.fromString(dto.getTagUuid()), UUID.fromString(dto.getTrailUuid()))).toList()))
		.flatMap(tuple -> repo.deleteByTagUuidAndTrailUuidAndOwner(tuple.getT1(), tuple.getT2(), owner), 3, 6)
		.then();
	}
	
	private TrailTag toDto(TrailTagEntity entity) {
		return new TrailTag(entity.getTagUuid().toString(), entity.getTrailUuid().toString(), entity.getCreatedAt());
	}
	
	private TrailTagEntity toEntity(TrailTag dto, String owner) {
		return new TrailTagEntity(UUID.fromString(dto.getTagUuid()), UUID.fromString(dto.getTrailUuid()), owner, System.currentTimeMillis());
	}
	
}
