package org.trailence.trail.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class FeedbackToReview {

	private String trailUuid;
	private String trailName;
	private String trailDescription;
	
	private List<PublicTrailFeedback> feedbacks;
	
}
