package org.trailence.storage;

import org.springframework.core.io.buffer.DataBuffer;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface FileStorageProvider {

	Mono<String> storeFile(String path, Flux<DataBuffer> content, long expectedSize);
	
	Flux<DataBuffer> getFile(String fileId, String path);
	
	Mono<Void> deleteFile(String fileId, String path);
	
}
