package org.trailence.storage.provider.pcloud.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PCloudMetadata {

	private boolean isfolder;
	private String name;
	private Long folderid;
	private Long fileid;
	private Long size;
	
}
