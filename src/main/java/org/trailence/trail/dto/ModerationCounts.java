package org.trailence.trail.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ModerationCounts {

	private long trails;
	private long comments;
	private long commentReplies;
	private long removeRequests;
	private long avatars;
	
}
