package org.trailence.global.exceptions;

import org.springframework.http.HttpStatus;

public class QuotaExceededException extends TrailenceException {

	private static final long serialVersionUID = 1L;

	public QuotaExceededException(String quotaType) {
		super(HttpStatus.FORBIDDEN, "quota-exceeded-" + quotaType, "Quota exceeded for " + quotaType);
	}
	
}
