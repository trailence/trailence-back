package org.trailence.trail.db;

import java.util.UUID;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;

import reactor.core.publisher.Mono;

public interface ShareEmailRepository extends ReactiveCrudRepository<ShareEmailEntity, String> {

	Mono<ShareEmailEntity> findByShareUuidAndFromEmailAndToEmail(UUID shareUuid, String fromEmail, String toEmail);
	
}
