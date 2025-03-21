package org.trailence.donations.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Donation {

	private String uuid;
	private String platform;
	private String platformId;
	
	private long timestamp;
	private long amount;
	private long realAmount;
	private String email;
	
	private String details;
	
}
