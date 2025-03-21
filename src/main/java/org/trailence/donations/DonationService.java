package org.trailence.donations;

import java.util.UUID;

import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.trailence.donations.db.DonationEntity;
import org.trailence.donations.db.DonationRepository;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class DonationService {
	
	private final R2dbcEntityTemplate r2dbc;
	private final DonationRepository repo;

	@Transactional
	public Mono<Void> createDonation(
		String platform,
		String platformId,
		String type,
		long timestamp,
		long amount,
		String details
	) {
		return repo.findByPlatformAndPlatformId(platform, platformId)
		.switchIfEmpty(Mono.defer(() -> r2dbc.insert(new DonationEntity(UUID.randomUUID(), platform, platformId, type, timestamp, amount, details))))
		.then();
	}
	
}
