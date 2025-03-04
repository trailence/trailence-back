package org.trailence.global.exceptions;

import org.springframework.http.HttpStatus;

public class ConflictException extends TrailenceException {

	private static final long serialVersionUID = 1L;

	public ConflictException(String errorCode, String message) {
		super(HttpStatus.CONFLICT, errorCode, message);
	}
	
}
