package org.trailence.storage.provider;

import org.trailence.storage.StorageProperties;

import reactor.core.publisher.Mono;

public interface FileStorageProvider {
	
	Mono<? extends FileStorageProvider> init();
	
	Mono<? extends FileStorageLocation> createLocation(StorageProperties.StorageLocationProperties properties);
	
}
