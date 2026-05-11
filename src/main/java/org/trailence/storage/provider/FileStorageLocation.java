package org.trailence.storage.provider;

import java.util.function.Supplier;

import org.springframework.core.io.buffer.DataBuffer;
import org.trailence.global.exceptions.NotFoundException;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface FileStorageLocation {
	
	Mono<String> storeFile(String path, Flux<DataBuffer> content, long expectedSize);
	
	Flux<DataBuffer> getFile(String fileId, String path, Supplier<NotFoundException> onNotFound);
	
	Mono<Void> deleteFile(String fileId, String path);
	
}
