package org.trailence.trail.dto;

import java.util.List;

import org.trailence.trail.dto.Track.Segment;
import org.trailence.trail.dto.Track.WayPoint;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TrailLinkContent {

	private TrailLinkTrail trail;
	private TrailLinkTrack track;
	private List<TrailLinkPhoto> photos;
	
	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class TrailLinkTrail {
	    private long createdAt;
	    private long updatedAt;

	    private String name;
	    private String description;
	    private String location;
	    private Long date;
	    private String loopType;
	    private String activity;
	}
	
	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class TrailLinkTrack {
		private Segment[] s;
		private WayPoint[] wp;
	}
	
	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class TrailLinkPhoto {
		private String uuid;
	    private long createdAt;
	    private long updatedAt;
	    private String description;
	    private Long dateTaken;
	    private Long latitude;
	    private Long longitude;
	    private boolean isCover;
	    private int index;
	}
	
}
