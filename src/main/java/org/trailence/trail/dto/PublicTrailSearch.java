package org.trailence.trail.dto;

import java.util.List;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class PublicTrailSearch {

	@Data
	public static class SearchByTileRequest {
		private int zoom;
		private List<Integer> tiles;
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
