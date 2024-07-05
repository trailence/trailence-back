package org.trailence.trail;

import java.util.Collection;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.trailence.trail.db.TrailTagEntity;
import org.trailence.trail.db.TrailTagRepository;
import org.trailence.trail.dto.TrailTag;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class TrailTagService {

	private final TrailTagRepository repo;
	private final R2dbcEntityTemplate r2dbc;
	
	public Flux<TrailTag> getAll(Authentication auth) {
		return repo.findAllByOwner(auth.getPrincipal().toString()).map(this::toDto);
	}
	
	public Flux<TrailTag> bulkCreate(Collection<TrailTag> dtos, Authentication auth) {
		String owner = auth.getPrincipal().toString();
		return Flux.fromIterable(dtos)
		.map(dto -> toEntity(dto, owner))
		.flatMap(toCreate ->
			r2dbc.insert(toCreate)
			.onErrorResume(DuplicateKeyException.class, e -> Mono.just(toCreate)),
			3, 6
		)
		.map(this::toDto);
	}
	
	public Mono<Void> bulkDelete(Collection<TrailTag> dtos, Authentication auth) {
		String owner = auth.getPrincipal().toString();
		return Flux.fromIterable(dtos)
		.flatMap(dto -> repo.deleteByTagUuidAndTrailUuidAndOwner(UUID.fromString(dto.getTagUuid()), UUID.fromString(dto.getTrailUuid()), owner), 3, 6)
		.then();
	}
	
	private TrailTag toDto(TrailTagEntity entity) {
		return new TrailTag(entity.getTagUuid().toString(), entity.getTrailUuid().toString(), entity.getCreatedAt());
	}
	
	private TrailTagEntity toEntity(TrailTag dto, String owner) {
		return new TrailTagEntity(UUID.fromString(dto.getTagUuid()), UUID.fromString(dto.getTrailUuid()), owner, System.currentTimeMillis());
	}
	
}
