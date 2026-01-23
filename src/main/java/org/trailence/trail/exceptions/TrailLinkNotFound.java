package org.trailence.trail.exceptions;

import org.trailence.global.exceptions.NotFoundException;

@SuppressWarnings("java:S110")
public class TrailLinkNotFound extends NotFoundException {

	private static final long serialVersionUID = 1L;

	public TrailLinkNotFound(String link) {
		super("trail-link", link);
	}
	
}
