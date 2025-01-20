package org.trailence.auth.dto;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserKey {

	private String id;
	private long createdAt;
	private long lastUsage;
	private Map<String, Object> deviceInfo;
	@JsonInclude(JsonInclude.Include.NON_NULL)
	private Long deletedAt;
	
}
