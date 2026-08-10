package org.trailence.global.rest;

import org.springframework.security.core.Authentication;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AuthDetails {
	
	public static final String HEADER_VERSION = "X-Trailence-Version";
	public static final int MIN_VERSION = 20200;

	private final int trailenceVersion;
	
	public static int getVersion(Authentication auth) {
		if (auth == null) return MIN_VERSION;
		Object details = auth.getDetails();
		if (details instanceof AuthDetails d) return d.getTrailenceVersion();
		return MIN_VERSION;
	}
	
}
