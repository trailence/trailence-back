package org.trailence.user.dto;

import org.trailence.global.TrailenceUtils;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ResetPasswordRequest {

	private String email;
	@NotNull
	@Size(min =  TrailenceUtils.MIN_PASSWORD_SIZE)
	private String newPassword;
	@NotNull
	private String code;
	
}
