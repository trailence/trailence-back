package org.trailence.quotas.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Plan {

	private String name;
	
	private long collections;
	private long trails;
	private long tracks;
	private long tracksSize;
	private long photos;
	private long photosSize;
	private long tags;
	private long trailTags;
	private long shares;
	
	private Long subscriptionsCount;
	private Long activeSubscriptionsCount;
	
}
