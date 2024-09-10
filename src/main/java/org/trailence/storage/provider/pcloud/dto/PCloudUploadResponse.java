package org.trailence.storage.provider.pcloud.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PCloudUploadResponse {

	private int result;
	private List<PCloudMetadata> metadata;
	private List<PCloudChecksums> checksums;
	private List<Long> fileids;
	
}
