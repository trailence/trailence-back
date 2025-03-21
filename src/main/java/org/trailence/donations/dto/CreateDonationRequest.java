package org.trailence.donations.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateDonationRequest {

	private String platform;
	private String platformId;
	private Long timestamp;
	private String amount;
	private String amountCurrency;
	private String realAmount;
	private String realAmountCurrency;
	private String email;
	private String details;
	
}
