package org.trailence.donations.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DonationGoal {

	private int index;
	
	private String type;
	private long amount;
	
	private long createdAt;
	private long updatedAt;
	
}
