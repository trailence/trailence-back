package org.trailence.storage;

import java.util.concurrent.TimeUnit;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.trailence.global.exceptions.BadRequestException;
import org.trailence.storage.db.FileEntity;
import org.trailence.storage.db.FileRepository;
import org.trailence.storage.provider.fs.FileSystemProvider;
import org.trailence.storage.provider.pcloud.PCloudProvider;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class FileService {
	
	private final StorageProperties properties;
	private final FileRepository repo;
	
	private Mono<FileStorageProvider> provider;
	
	private static final long MAX_FILE_SIZE = 25L * 1024 * 1024;
	
	@PostConstruct
	@SuppressWarnings("java:S112") // RuntimeException
	public void init() {
		provider = Mono.fromSupplier(() -> {
			if (properties.getType() != null)
				switch (properties.getType()) {
				case "fs": return new FileSystemProvider(properties.getRoot());
				case "pcloud": return new PCloudProvider(properties.getUrl(), properties.getUsername(), properties.getPassword(), properties.getRoot().isBlank() ? 0 : Long.parseLong(properties.getRoot()));
				default: break;
				}
			throw new RuntimeException("Invalid storage type: " + properties.getType());
		}).share();
	}

	public Mono<Long> storeFile(long size, Flux<DataBuffer> content) {
		if (size > MAX_FILE_SIZE) return Mono.error(new BadRequestException("maximum-size-exceeded", "File is too large"));
		if (size < 0) return Mono.error(new BadRequestException("invalid-size", "Size cannot be negative"));
		return provider.flatMap(storage -> {
			FileEntity entity = new FileEntity();
			entity.setCreatedAt(System.currentTimeMillis());
			entity.setSize(size);
			entity.setTmp(true);
			return repo.save(entity).flatMap(entityTmp -> {
				long id = entityTmp.getId();
				return storage.storeFile(getPath(id), content, size)
				.flatMap(storageId -> {
					entityTmp.setStorageId(storageId);
					entityTmp.setTmp(false);
					return repo.save(entityTmp).thenReturn(id);
				});
			});
		});
	}
	
	public Mono<Void> deleteFile(long fileId) {
		return repo.findById(fileId).flatMap(entity ->
			provider.flatMap(storage -> storage.deleteFile(entity.getStorageId(), getPath(fileId)))
			.then(repo.deleteById(fileId))
		);
	}
	
	public Flux<DataBuffer> getFileContent(long fileId) {
		return repo.findById(fileId).flatMapMany(entity ->
			provider.flatMapMany(storage -> storage.getFile(entity.getStorageId(), getPath(fileId)))
		);
	}
	
	private String getPath(long id) {
		return
			toHex((id >> 56) & 0xFF) + '/' +
			toHex((id >> 48) & 0xFF) + '/' +
			toHex((id >> 40) & 0xFF) + '/' +
			toHex((id >> 32) & 0xFF) + '/' +
			toHex((id >> 24) & 0xFF) + '/' +
			toHex((id >> 16) & 0xFF) + '/' +
			toHex((id >> 8) & 0xFF) + '/' +
			toHex(id & 0xFF);
	}
	
	private String toHex(long value) {
		int i = (int) value & 0xFF;
		return new StringBuilder(2).append(HEX[i / 16]).append(HEX[i % 16]).toString();
	}
	private static final char[] HEX = {'0','1','2','3','4','5','6','7','8','9','a','b','c','d','e','f'};
	
	
	@Scheduled(fixedRate = 15, timeUnit = TimeUnit.MINUTES, initialDelay = 5)
	public void clean() {
		repo.findByTmpAndCreatedAtLessThan(true, System.currentTimeMillis() - (60 * 60 * 1000), PageRequest.of(0, 100, Sort.by(Direction.ASC, "createdAt")))
		.flatMap(entity -> {
			if (entity.getStorageId() == null) return repo.delete(entity);
			return provider.flatMap(storage ->
				storage.deleteFile(entity.getStorageId(), getPath(entity.getId()))
				.onErrorComplete()
			)
			.then(repo.delete(entity));
		}, 3, 6)
		.checkpoint("Clean tmp files")
		.subscribe();
	}

	
}
