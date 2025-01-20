package org.trailence.auth.dto;

import java.util.Map;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginShareRequest {

	@NotNull
	private String token;
	@NotNull
	private byte[] publicKey;
	private Long expiresAfter;
	
	private Map<String, Object> deviceInfo;
	
}
