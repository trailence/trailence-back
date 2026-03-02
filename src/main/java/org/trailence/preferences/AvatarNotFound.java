package org.trailence.preferences;

import org.trailence.global.exceptions.NotFoundException;

@SuppressWarnings("java:S110")
public class AvatarNotFound extends NotFoundException {

	private static final long serialVersionUID = 1L;

	public AvatarNotFound(String id) {
		super("avatar", id);
	}
	
	public static class CurrentAvatarNotFound extends AvatarNotFound {
		private static final long serialVersionUID = 1L;
		public CurrentAvatarNotFound() {
			super("current");
		}
	}

	public static class PendingAvatarNotFound extends AvatarNotFound {
		private static final long serialVersionUID = 1L;
		public PendingAvatarNotFound() {
			super("pending");
		}
	}
	
}
