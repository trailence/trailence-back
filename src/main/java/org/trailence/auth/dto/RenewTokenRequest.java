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
public class RenewTokenRequest {

	@NotNull
	@Email
	private String email;
	@NotNull
	@Size(min = 44, max = 44)
	private String random;
	@NotNull
	private String keyId;
	@NotNull
	private byte[] signature;
	
	private Map<String, Object> deviceInfo;
	
}
