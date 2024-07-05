package org.trailence.preferences.db;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;

public interface UserPreferencesRepository extends ReactiveCrudRepository<UserPreferencesEntity, String> {

}
