package org.trailence.trail.exceptions;

import org.trailence.global.exceptions.NotFoundException;

@SuppressWarnings("java:S110")
public class CollectionNotFound extends NotFoundException {

	private static final long serialVersionUID = 1L;

	public CollectionNotFound(String uuid) {
		super("collection", uuid);
	}
	
}
