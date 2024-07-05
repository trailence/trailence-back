package org.trailence.global.exceptions;

import org.springframework.http.HttpStatus;

public class ForbiddenException extends TrailenceException {

	private static final long serialVersionUID = 1L;

	public ForbiddenException() {
		super(HttpStatus.FORBIDDEN, "forbidden", "Access denied");
	}
	
}
