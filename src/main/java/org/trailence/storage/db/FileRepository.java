package org.trailence.storage.db;

import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

import reactor.core.publisher.Flux;

public interface FileRepository extends ReactiveCrudRepository<FileEntity, Long> {

	Flux<FileEntity> findByTmpAndCreatedAtLessThan(boolean tmp, long maxTimestamp, Pageable pageable);
	
}
