package org.trailence.trail.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class CreateFeedbackRequest {

	private String trailUuid;
	private Integer rate;
	private String comment;
	
}
