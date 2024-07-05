package org.trailence.global.exceptions;

import org.springframework.http.HttpStatus;

public class UnauthorizedException extends TrailenceException {

	private static final long serialVersionUID = 1L;

	public UnauthorizedException() {
		super(HttpStatus.UNAUTHORIZED, "unauthorized", "You must authenticate");
	}
	
}
