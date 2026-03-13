package org.trailence.stats.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StatsValue {

	private Long year;
	private Long month;
	private Long week;
	private Long day;
	private Long value;
	
}
