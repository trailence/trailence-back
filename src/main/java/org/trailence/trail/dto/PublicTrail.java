package org.trailence.trail.dto;

import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class PublicTrail {

	private String uuid;
	private String slug;
	private long createdAt;
	private long updatedAt;
	private String authorAlias;
	private String myUuid;
	private boolean itsMine;
	
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
	
	private double boundsNorth;
	private double boundsSouth;
	private double boundsWest;
	private double boundsEast;

	private long nbRate0;
	private long nbRate1;
	private long nbRate2;
	private long nbRate3;
	private long nbRate4;
	private long nbRate5;
	
	private List<Double> simplifiedPath;
	private List<Photo> photos;
	
	private String lang;
	private Map<String, String> nameTranslations;
	private Map<String, String> descriptionTranslations;
	
	@Data
	@AllArgsConstructor
	public static class Photo {
		private String uuid;
	    private String description;
	    private Long date;
	    private Long latitude;
	    private Long longitude;
	    private int index;
	}
	
}
