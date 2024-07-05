package org.trailence.trail.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Track {

	private String uuid;
	private String owner;
	private long version;
	
	private long createdAt;
    private long updatedAt;

	private Segment[] s;
	private WayPoint[] wp;
	
	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class Segment {
		private Point[] p;
	}
	
	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	@JsonInclude(Include.NON_NULL)
	public static class Point {
		private Long l;
		private Long n;
		private Long e;
		private Long t;
		private Long pa;
		private Long ea;
		private Long h;
		private Long s;
	}
	
	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	@JsonInclude(Include.NON_NULL)
	public static class WayPoint {
		private Long l;
		private Long n;
		private Long e;
		private Long t;
		private String na;
		private String de;
	}
	
}
