package org.trailence.preferences.db;

import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Table("user_community")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserCommunityEntity {

	@Id
	private String email;
	private UUID publicUuid;
	private int nbPublications;
	private int nbComments;
	private int nbRates;
	
}
