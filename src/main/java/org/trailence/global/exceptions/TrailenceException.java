package org.trailence.global.exceptions;

import org.springframework.http.HttpStatus;
import org.trailence.global.rest.ApiError;

import lombok.Getter;

@Getter
public abstract class TrailenceException extends RuntimeException {

	private static final long serialVersionUID = 1L;
	
	private HttpStatus status;
	private String errorCode;
	
	protected TrailenceException(HttpStatus status, String errorCode, String message) {
		super(message);
		this.status = status;
		this.errorCode = errorCode;
	}
	
	public ApiError toApiError() {
		return new ApiError(status.value(), errorCode, getMessage());
	}
	
}
