package org.trailence.trail.dto;

import java.util.List;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CreateShareRequest {

	@NotNull
	private String id;
	@NotNull
	@Size(min = 1)
	private String name;
	@NotNull
	@Size(min = 1, max = 20)
	private List<@Email String> recipients;
	@NotNull
	private ShareElementType type;
	@NotNull @NotEmpty
	private List<String> elements;
	@NotNull
	@Size(min = 2, max = 2)
	private String mailLanguage;
	private boolean includePhotos = false;
	
}
