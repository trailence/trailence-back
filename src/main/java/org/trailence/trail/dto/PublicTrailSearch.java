package org.trailence.trail.dto;

import java.util.List;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class PublicTrailSearch {
	
	@Data
	public static class Filters {
		private FilterNumeric duration;
		private FilterNumeric estimatedDuration;
		private FilterNumeric distance;
		private FilterNumeric positiveElevation;
		private FilterNumeric negativeElevation;
		private List<String> loopTypes;
		private List<String> activities;
		private FilterNumeric rate;
	}
	
	@Data
	public static class FilterNumeric {
		private Number from;
		private Number to;
	}

	@Data
	public static class SearchByTileRequest {
		private int zoom;
		private List<Integer> tiles;
		private Filters filters;
	}
	
	@Data
	@AllArgsConstructor
	public static class SearchByTileResponse {
		private List<NbTrailsByTile> trailsByTile;
	}
	
	@Data
	@AllArgsConstructor
	public static class NbTrailsByTile {
		private int tile;
		private long nbTrails;
	}
	
	@Data
	public static class SearchByBoundsRequest {
		private double north;
		private double south;
		private double west;
		private double east;
		private Integer maxResults;
	}
	
	@Data
	@AllArgsConstructor
	public static class SearchByBoundsResponse {
		private List<String> uuids;
		private boolean hasMoreResults;
	}
	
}
