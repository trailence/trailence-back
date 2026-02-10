package org.trailence.livegroup.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LiveGroup {
	
	private String slug;
	private String name;
	private long startedAt;
	private long expiresAt;
	private String trailOwner;
	private String trailUuid;
	private boolean trailShared;
	private List<Member> members;
	
	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class Member {
		private String uuid;
		private String name;
		private Position lastPosition;
		private Long lastPositionAt;
		private boolean you;
		private boolean owner;
	}
	
	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class Position {
		private long lat;
		private long lng;
	}

}
