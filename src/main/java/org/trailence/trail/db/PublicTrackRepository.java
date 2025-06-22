package org.trailence.trail.db;

import java.util.UUID;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;

public interface PublicTrackRepository extends ReactiveCrudRepository<PublicTrackEntity, UUID> {

}
