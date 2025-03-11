package org.trailence.trail.dto;

import java.util.List;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateShareRequest {

	@NotNull
	@Size(min = 1)
	private String name;
	private boolean includePhotos = false;
	private List<String> recipients;
	private String mailLanguage;
	
}
