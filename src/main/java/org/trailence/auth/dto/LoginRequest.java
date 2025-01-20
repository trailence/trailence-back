package org.trailence.auth.dto;

import java.util.Map;

import org.trailence.global.TrailenceUtils;

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
	@Size(min = TrailenceUtils.MIN_PASSWORD_SIZE)
	private String password;
	@NotNull
	private byte[] publicKey;
	private Long expiresAfter;
	
	private Map<String, Object> deviceInfo;
	
	private String captchaToken;
	
}
