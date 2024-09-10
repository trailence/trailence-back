package org.trailence.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

@Data
@ConfigurationProperties(prefix = "trailence.storage")
public class StorageProperties {

	private String type;
	private String username;
	private String password;
	private String root;
	private String url;
	
}
