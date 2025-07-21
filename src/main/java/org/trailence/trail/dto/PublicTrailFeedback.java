package org.trailence.trail.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PublicTrailFeedback {

	private String uuid;
	private String alias;
	private boolean you;
	private long date;
	private Integer rate;
	private String comment;
	private boolean reviewed;
	private List<Reply> replies;
	
	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class Reply {
		private String uuid;
		private String alias;
		private boolean you;
		private long date;
		private String comment;
		private boolean reviewed;
	}
	
}
