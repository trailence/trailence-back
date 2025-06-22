package org.trailence.trail.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TrailAndPhotos {

	private Trail trail;
	private List<Photo> photos;
	
}
