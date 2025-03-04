package org.trailence.quotas.dto;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserSubscription {

	private UUID uuid;
	
	private String planName;
	private long startsAt;
	private Long endsAt;
	
}
