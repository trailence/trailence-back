package org.trailence.global.rest;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.trailence.global.exceptions.TrailenceException;

import reactor.core.publisher.Mono;

@RestControllerAdvice
public class RestExceptions {

	@ExceptionHandler(TrailenceException.class)
	public Mono<ResponseEntity<ApiError>> handle(TrailenceException error) {
		return Mono.fromSupplier(() -> ResponseEntity.status(error.getStatus()).body(error.toApiError()));
	}
	
}
