package org.trailence.trail.db;

import java.util.Collection;
import java.util.UUID;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;

import reactor.core.publisher.Flux;

public interface PublicPhotoRepository  extends ReactiveCrudRepository<PublicPhotoEntity, UUID> {

	Flux<PublicPhotoEntity> findAllByTrailUuid(UUID trailUuid);
	Flux<PublicPhotoEntity> findAllByTrailUuidIn(Collection<UUID> trailUuid);

}
