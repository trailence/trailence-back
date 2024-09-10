package org.trailence.storage.provider.pcloud.dto;

import lombok.Data;

@Data
public class PCloudFolderResponse {

	private int result;
	private PCloudFolder metadata;
	
}
