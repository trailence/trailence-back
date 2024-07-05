package org.trailence.auth.dto;

import java.util.Map;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {

	@NotNull
	@Email
	private String email;
	@NotNull
	@Size(min = 6)
	private String password;
	@NotNull
	private byte[] publicKey;
	
	private Map<String, Object> deviceInfo;
	
}
