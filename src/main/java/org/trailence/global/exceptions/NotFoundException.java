package org.trailence.global.exceptions;

import org.springframework.http.HttpStatus;

public class NotFoundException extends TrailenceException {

	private static final long serialVersionUID = 1L;

	public NotFoundException(String type, String item) {
		super(HttpStatus.NOT_FOUND, type + "-not-found", type + " not found: " + item);
	}
	
}
