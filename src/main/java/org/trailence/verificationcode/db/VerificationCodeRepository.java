package org.trailence.verificationcode.db;

import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface VerificationCodeRepository extends ReactiveCrudRepository<VerificationCodeEntity, String> {
	
	Flux<VerificationCodeEntity> findByTypeAndKeyAndExpiresAtGreaterThan(String type, String key, long now);
	
	Mono<Long> deleteAllByExpiresAtLessThan(long now);
	
	Mono<Void> deleteAllByTypeAndKey(String type, String key);
	
	Flux<VerificationCodeEntity> findByTypeAndKey(String type, String key, Pageable pageable);

}
