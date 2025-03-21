package org.trailence.donations.db;

import java.util.UUID;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;

import reactor.core.publisher.Mono;

public interface DonationRepository extends ReactiveCrudRepository<DonationEntity, UUID> {

	Mono<DonationEntity> findByPlatformAndPlatformId(String platform, String platformId);
	
}
