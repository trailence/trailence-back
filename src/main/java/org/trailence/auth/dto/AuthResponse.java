package org.trailence.auth.dto;

import org.trailence.preferences.dto.UserPreferences;

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
	private UserPreferences preferences;
	
}
