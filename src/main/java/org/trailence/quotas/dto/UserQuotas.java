package org.trailence.quotas.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserQuotas {

	private short collectionsUsed;
	private short collectionsMax;
	private int trailsUsed;
	private int trailsMax;
	private int tracksUsed;
	private int tracksMax;
	private int tracksSizeUsed;
	private int tracksSizeMax;
	private int photosUsed;
	private int photosMax;
	private long photosSizeUsed;
	private long photosSizeMax;
	private int tagsUsed;
	private int tagsMax;
	private int trailTagsUsed;
	private int trailTagsMax;
	private short sharesUsed;
	private short sharesMax;
	
}
