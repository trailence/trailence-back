package org.trailence.trail.dto;

import org.trailence.trail.dto.Track.Segment;
import org.trailence.trail.dto.Track.WayPoint;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class PublicTrack {

	private Segment[] s;
	private WayPoint[] wp;
	
}
