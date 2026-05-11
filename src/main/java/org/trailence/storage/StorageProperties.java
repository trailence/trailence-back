package org.trailence.storage;

import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

@Data
@ConfigurationProperties(prefix = "trailence.storage")
public class StorageProperties {
	
	private List<StorageProviderProperties> providers;
	private Map<String, StorageLocationProperties> locations;

	@Data
	public static class StorageProviderProperties {
		private String id;
		private String type;
		private String username;
		private String password;
		private String authkey;
		private String url;
	}
	
	@Data
	public static class StorageLocationProperties {
		private String provider;
		private String root;
	}
	
}
