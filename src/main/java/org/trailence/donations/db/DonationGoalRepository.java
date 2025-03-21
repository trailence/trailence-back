package org.trailence.donations.db;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;

public interface DonationGoalRepository extends ReactiveCrudRepository<DonationGoalEntity, Integer> {

}
