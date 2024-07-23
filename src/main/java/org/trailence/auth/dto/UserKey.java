package org.trailence.auth.dto;

import java.util.Map;

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
	
}
