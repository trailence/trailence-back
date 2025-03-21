package org.trailence.donations.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DonationStatus {

	private long currentDonations;
	private List<DonationGoal> goals;
	
}
