package org.trailence.storage.provider.pcloud.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PCloudChecksums {

	private String sha1;
	private String sha256;
	
}
