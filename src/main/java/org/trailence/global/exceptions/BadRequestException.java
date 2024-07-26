package org.trailence.global.exceptions;

import org.springframework.http.HttpStatus;

public class BadRequestException extends TrailenceException {

	private static final long serialVersionUID = 1L;

	public BadRequestException(String message) {
		super(HttpStatus.BAD_REQUEST, "bad-request", message);
	}
	
}
