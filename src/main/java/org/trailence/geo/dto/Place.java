package org.trailence.geo.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Place {

	private List<String> names;
	private Double lat;
	private Double lng;
	private Double north;
	private Double south;
	private Double east;
	private Double west;
	
}
