package org.trailence.storage.provider.fs;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.function.Supplier;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.trailence.global.exceptions.BadRequestException;
import org.trailence.global.exceptions.NotFoundException;
import org.trailence.storage.StorageProperties.StorageLocationProperties;
import org.trailence.storage.provider.FileStorageLocation;
import org.trailence.storage.provider.FileStorageProvider;
import org.trailence.storage.provider.StorageUtils;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@RequiredArgsConstructor
@SuppressWarnings({"java:S899", "java:S4042"})
public class FileSystemProvider implements FileStorageProvider {
	
	@Override
	public Mono<FileSystemProvider> init() {
		return Mono.just(this);
	}
	
	@Override
	public Mono<? extends FileStorageLocation> createLocation(StorageLocationProperties properties) {
		return Mono.just(properties.getRoot())
		.flatMap(root -> {
			File rootDir = new File(root);
			if (!rootDir.exists()) {
				if (!rootDir.mkdirs())
					return Mono.error(new IOException("Root directory does not exist and cannot be created: " + root));
			} else if (!rootDir.isDirectory()) {
				return Mono.error(new IOException("Root directory is not a directory: " + root));
			}
			return Mono.just(new Location(rootDir));
		});
	}

	@RequiredArgsConstructor
	private static class Location implements FileStorageLocation {
		private final File root;
		
		@Override
		public Mono<String> storeFile(String path, Flux<DataBuffer> content, long expectedSize) {
			return Mono.defer(() -> {
				FileOutputStream out;
				File file = new File(root, path);
				try {
					File dir = file.getParentFile();
					dir.mkdirs();
					if (file.exists()) file.delete();
					out = new FileOutputStream(file);
				} catch (Exception e) {
					return Mono.error(e);
				}
				return StorageUtils.writeAndClose(content, out)
					.then(Mono.fromCallable(() -> {
						if (file.length() != expectedSize) throw new BadRequestException("invalid-size", "Given size is " + expectedSize + " but stored size is " + file.length());
						return "";
					}));
			})
			.subscribeOn(Schedulers.boundedElastic())
			.publishOn(Schedulers.parallel());
		}
		
		@Override
		public Flux<DataBuffer> getFile(String fileId, String path, Supplier<NotFoundException> onNotFound) {
			return Flux.defer(() -> {
				File file = new File(root, path);
				if (!file.exists()) return Flux.error(onNotFound);
				return DataBufferUtils.readInputStream(() -> new FileInputStream(file), new DefaultDataBufferFactory(false, 65536), 65536);
			})
			.subscribeOn(Schedulers.boundedElastic())
			.publishOn(Schedulers.boundedElastic());
		}
		
		@Override
		public Mono<Void> deleteFile(String fileId, String path) {
			return Mono.<Void>fromRunnable(() -> {
				File file = new File(root, path);
				file.delete();
			})
			.subscribeOn(Schedulers.boundedElastic())
			.publishOn(Schedulers.parallel());
		}
	}
	
}
