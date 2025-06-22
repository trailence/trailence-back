package org.trailence.trail.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class MyFeedback {

	private Integer rate;
	private Long rateDate;
	private Long latestCommentDate;
	
}
