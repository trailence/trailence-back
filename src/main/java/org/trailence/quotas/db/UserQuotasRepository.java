package org.trailence.quotas.db;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;

public interface UserQuotasRepository extends ReactiveCrudRepository<UserQuotasEntity, String> {

}
