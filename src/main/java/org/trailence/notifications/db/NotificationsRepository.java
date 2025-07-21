package org.trailence.notifications.db;

import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface NotificationsRepository extends ReactiveCrudRepository<NotificationEntity, UUID> {

	Flux<NotificationEntity> findAllByOwner(String owner, Pageable pageable);
	
	Mono<Void> deleteByDateLessThan(long maxDate);
	Mono<Void> deleteByOwner(String owner);
	
}
