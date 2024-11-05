package org.trailence.global.exceptions;

import org.springframework.http.HttpStatus;

public class BadRequestException extends TrailenceException {

	private static final long serialVersionUID = 1L;

	public BadRequestException(String message) {
		this("bad-request", message);
	}
	
	public BadRequestException(String errorCode, String message) {
		super(HttpStatus.BAD_REQUEST, errorCode, message);
	}
	
}
