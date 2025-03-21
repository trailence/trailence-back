package org.trailence.extensions;

import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@ConfigurationProperties(prefix = "trailence.extensions")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ExtensionsConfig {

	private Map<String, ExtensionConfig> allowed;
	
	@Data
	@AllArgsConstructor
	@NoArgsConstructor
	public static class ExtensionConfig {
		private boolean enabled = false;
		private String role = "";
		private Map<String, ContentValidation> content;
	}
	
	@Data
	@AllArgsConstructor
	@NoArgsConstructor
	public static class ContentValidation {
		private String pattern;
	}
	
}
