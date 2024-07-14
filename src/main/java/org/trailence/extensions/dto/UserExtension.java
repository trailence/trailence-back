package org.trailence.extensions.dto;

import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserExtension {

	private long version;
	private String extension;
	private Map<String, String> data;
	
}
