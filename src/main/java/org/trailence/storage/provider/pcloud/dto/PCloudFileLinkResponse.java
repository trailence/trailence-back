package org.trailence.storage.provider.pcloud.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PCloudFileLinkResponse {

	private List<String> hosts;
	private String path;
	
}
