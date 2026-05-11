package org.trailence.storage.provider;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.trailence.storage.StorageProperties;
import org.trailence.storage.provider.fs.FileSystemProvider;
import org.trailence.storage.provider.pcloud.PCloudProvider;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class FileStorageProviderService {

	private final StorageProperties properties;
	
	private final Map<String, Mono<? extends FileStorageProvider>> providers = new HashMap<>();
	private final Map<String, Mono<? extends FileStorageLocation>> locations = new HashMap<>();

	@PostConstruct
	@SuppressWarnings("java:S112") // RuntimeException
	public void init() {
		for (var providerConfig : properties.getProviders()) {
			var provider = Mono.fromSupplier(() -> {
				if (providerConfig.getType() != null)
					switch (providerConfig.getType()) {
					case "fs": return new FileSystemProvider();
					case "pcloud": return new PCloudProvider(providerConfig);
					default: break;
					}
				throw new RuntimeException("Invalid storage type: " + providerConfig.getType());
			})
			.flatMap(p -> p.init())
			.share();
			providers.put(providerConfig.getId(), provider);
			// force init at startup
			provider.subscribe();
		}
		for (var locationConfig : properties.getLocations().entrySet()) {
			var provider = providers.get(locationConfig.getValue().getProvider());
			if (provider == null) throw new RuntimeException("Invalid storage location provider: " + locationConfig.getValue().getProvider());
			var location = provider.flatMap(p -> p.createLocation(locationConfig.getValue())).share();
			locations.put(locationConfig.getKey(), location);
			// force init at startup
			location.subscribe();
		}
	}
	
	public Mono<? extends FileStorageLocation> getLocation(String name) {
		var loc = locations.get(name);
		if (loc == null) return Mono.error(new IOException("Unknown sotrage location: " + name));
		return loc;
	}

}
