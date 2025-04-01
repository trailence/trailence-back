package org.trailence.user.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegisterNewUserRequest {

	private String email;
	private String lang;
	private String code;
	private String password;
	
}
