package org.trailence.user.dto;

import org.trailence.quotas.dto.UserQuotas;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class User {

	private String email;
	private long createdAt;
	
	private boolean isComplete;
	private boolean isAdmin;
	
	private int invalidLoginAttempts;
	private Long lastLogin;
	
	private Long minAppVersion;
	private Long maxAppVersion;
	
	private UserQuotas quotas;
	
}
