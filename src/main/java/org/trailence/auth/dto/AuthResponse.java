package org.trailence.auth.dto;

import org.trailence.preferences.dto.UserPreferences;
import org.trailence.quotas.dto.UserQuotas;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AuthResponse {

	private String accessToken;
	private long expires;
	private String email;
	private String keyId;
	private long keyCreatedAt;
	private long keyExpiresAt;
	private UserPreferences preferences;
	private boolean isComplete;
	private boolean isAdmin;
	private UserQuotas quotas;
	
}
