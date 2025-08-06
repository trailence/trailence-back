package org.trailence.trail.exceptions;

import org.trailence.global.exceptions.NotFoundException;

@SuppressWarnings("java:S110")
public class TrailNotFound extends NotFoundException {

	private static final long serialVersionUID = 1L;

	public TrailNotFound(String uuid, String owner) {
		super("trail", uuid + " " + owner);
	}
	
}
