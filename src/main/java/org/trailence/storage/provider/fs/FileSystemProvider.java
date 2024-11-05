package org.trailence.storage.provider.fs;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.trailence.global.exceptions.BadRequestException;
import org.trailence.storage.FileStorageProvider;
import org.trailence.storage.provider.StorageUtils;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@RequiredArgsConstructor
@SuppressWarnings({"java:S899", "java:S4042"})
public class FileSystemProvider implements FileStorageProvider {

	private final String rootPath;
	
	@Override
	public Mono<String> storeFile(String path, Flux<DataBuffer> content, long expectedSize) {
		return Mono.defer(() -> {
			FileOutputStream out;
			File root = new File(rootPath);
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
	public Flux<DataBuffer> getFile(String fileId, String path) {
		return Flux.defer(() -> {
			File root = new File(rootPath);
			File file = new File(root, path);
			return DataBufferUtils.readInputStream(() -> new FileInputStream(file), new DefaultDataBufferFactory(false, 65536), 65536);
		})
		.subscribeOn(Schedulers.boundedElastic())
		.publishOn(Schedulers.boundedElastic());
	}
	
	@Override
	public Mono<Void> deleteFile(String fileId, String path) {
		return Mono.<Void>fromRunnable(() -> {
			File root = new File(rootPath);
			File file = new File(root, path);
			file.delete();
		})
		.subscribeOn(Schedulers.boundedElastic())
		.publishOn(Schedulers.parallel());
	}
	
}
