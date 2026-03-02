package org.trailence.livegroup;

import org.trailence.global.exceptions.NotFoundException;

@SuppressWarnings("java:S110")
public class LiveGroupNotFound extends NotFoundException {

	private static final long serialVersionUID = 1L;

	public LiveGroupNotFound(String slug) {
		super("live-group", slug);
	}
	
}
