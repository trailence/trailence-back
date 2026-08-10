package org.trailence.global.exceptions;

import org.springframework.http.HttpStatus;

public class InternalException extends TrailenceException {

	private static final long serialVersionUID = 1L;

	public InternalException(Throwable cause) {
		super(HttpStatus.INTERNAL_SERVER_ERROR, "internal-error", "Internal error");
	}
	
}
