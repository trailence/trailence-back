package org.trailence.trail.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TrailTag {

	private String tagUuid;
	private String trailUuid;
	private long createdAt;
	
}
