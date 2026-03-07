package org.trailence.preferences.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserCommunity {
	private String email;
	private String publicId;
	private String alias;
	private String avatar;
	private Integer nbPublications;
	private Integer nbComments;
	private Integer nbRates;
}