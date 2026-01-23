package org.trailence.trail.exceptions;

import org.trailence.global.exceptions.NotFoundException;

@SuppressWarnings("java:S110")
public class PublicTrailNotFound extends NotFoundException {

	private static final long serialVersionUID = 1L;

	public PublicTrailNotFound(String uuid) {
		super("public-trail", uuid);
	}
	
}
