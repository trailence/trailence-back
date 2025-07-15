package org.trailence.trail.dto;

import java.util.List;
import java.util.Map;

import lombok.Data;

@Data
public class CreatePublicTrailRequest {

	private String trailUuid;
	private String author;
	private String authorUuid;
	
	private String name;
	private String description;
	private String location;
	private long date;

	private long distance;
	private Long positiveElevation;
	private Long negativeElevation;
	private Long highestAltitude;
	private Long lowestAltitude;
	private Long duration;
	private long breaksDuration;
	private long estimatedDuration;
	
	private String loopType;
	private String activity;
	
	private Double boundsNorth;
	private Double boundsSouth;
	private Double boundsWest;
	private Double boundsEast;
	
	private List<Integer> tile128ByZoom;
	private List<Double> simplifiedPath;
	private Track.Segment[] fullTrack;
	private Track.WayPoint[] wayPoints;
	
	private List<Photo> photos;
	
	private String lang;
	private Map<String, String> nameTranslations;
	private Map<String, String> descriptionTranslations;
	
	@Data
	public static class Photo {
		private String uuid;
		private int index;
		private Long lat;
		private Long lng;
		private Long date;
		private String description;
	}

	
}
