package org.trailence.donations.db;

import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Table("donations")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class DonationEntity {

	@Id
	private UUID uuid;
	
	private String platform;
	private String platformId;
	
	private String type;
	
	private long timestamp;
	private long amount;
	
	private String details;
}
