package org.trailence.contact.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CreateMessageRequest {

	@NotNull
	@Email
	private String email;
	@NotNull
	@Size(min = 1)
	private String type;
	@NotNull
	@Size(min = 1)
	private String message;
	private String captcha;
	
}
