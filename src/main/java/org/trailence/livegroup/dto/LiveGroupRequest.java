package org.trailence.livegroup.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LiveGroupRequest {

	private String groupName;
	private String myName;
	private String trailOwner;
	private String trailUuid;
	private Boolean trailShared;
	
}
