package org.trailence.donations.db;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Table("donation_goals")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class DonationGoalEntity {

	@Id
	private int index;
	
	private String type;
	private long amount;
	
	private long createdAt;
	private long updatedAt;
	
}
