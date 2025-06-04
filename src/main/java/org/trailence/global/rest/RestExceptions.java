package org.trailence.global.rest;

import org.springframework.core.MethodParameter;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebInputException;
import org.trailence.global.exceptions.TrailenceException;
import org.trailence.global.exceptions.ValidationUtils;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@RestControllerAdvice
@Slf4j
public class RestExceptions {

	@ExceptionHandler(TrailenceException.class)
	public Mono<ResponseEntity<ApiError>> handleTrailenceError(TrailenceException error, ServerWebExchange exchange) {
		log.warn("Trailence error returned by {} {}: {} - {} - {}", exchange.getRequest().getMethod(), exchange.getRequest().getURI(), error.getClass().getSimpleName(), error.getErrorCode(), error.getMessage());
		return Mono.fromSupplier(() -> ResponseEntity.status(error.getStatus()).body(error.toApiError()));
	}
	
	@ExceptionHandler(WebExchangeBindException.class)
	public Mono<ResponseEntity<ApiError>> handleBindError(WebExchangeBindException error, ServerWebExchange exchange) {
		ApiError result;
		if (error.hasFieldErrors()) {
			result = new ApiError(400, ValidationUtils.INVALID_PREFIX + error.getFieldError().getField(), error.getFieldError().getDefaultMessage());
		} else {
			result = new ApiError(400, "invalid-input", "Invalid request");
		}
		log.warn("Framework bind input error returned by {} {}: 400 - {} => {}", exchange.getRequest().getMethod(), exchange.getRequest().getURI(), error.getMessage(), result);
		return Mono.fromSupplier(() -> ResponseEntity.status(400).body(result));
	}
	
	@ExceptionHandler(ServerWebInputException.class)
	public Mono<ResponseEntity<ApiError>> handleInputError(ServerWebInputException error, ServerWebExchange exchange) {
		ApiError result;
		String parameterName = getParameterName(error.getMethodParameter());
		if (parameterName != null) {
			result = new ApiError(400, ValidationUtils.INVALID_PREFIX + parameterName, error.getMessage());
		} else {
			result = new ApiError(400, "invalid-input", "Invalid request");
		}
		log.warn("Framework input error returned by {} {}: 400 - {} - {} => {}", exchange.getRequest().getMethod(), exchange.getRequest().getURI(), error.getMethodParameter(), error.getMessage(), result);
		return Mono.fromSupplier(() -> ResponseEntity.status(400).body(result));
	}
	
	private String getParameterName(MethodParameter p) {
		if (p == null) return null;
		if (p.getParameterName() != null) return p.getParameterName();
		PathVariable pv = p.getParameterAnnotation(PathVariable.class);
		if (pv != null && !pv.name().isBlank()) return pv.name();
		RequestParam rp = p.getParameterAnnotation(RequestParam.class);
		if (rp != null && !rp.name().isBlank()) return rp.name();
		RequestHeader rh = p.getParameterAnnotation(RequestHeader.class);
		if (rh != null && !rh.name().isBlank()) {
			var name = rh.name();
			if (name.startsWith("X-")) name = name.substring(2);
			if (name.length() > 1) name = Character.toLowerCase(name.charAt(0)) + name.substring(1);
			return name;
		}
		return null;
	}
	
	@ExceptionHandler(ErrorResponseException.class)
	public Mono<ResponseEntity<ApiError>> handleFrameworkError(ErrorResponseException error, ServerWebExchange exchange) {
		int status = error.getStatusCode().value();
		var body = error.getBody();
		var result = new ApiError(status, body.getTitle(), body.getDetail());
		log.warn("Framework error returned by {} {}: {} - {} - {} => {}", exchange.getRequest().getMethod(), exchange.getRequest().getURI(), error.getClass().getSimpleName(), status, error.getMessage(), result);
		return Mono.fromSupplier(() -> ResponseEntity.status(status).body(result));
	}
	
	@ExceptionHandler(AccessDeniedException.class)
	public Mono<ResponseEntity<ApiError>> handleAccessDenied(AccessDeniedException error, ServerWebExchange exchange) {
		log.warn("Access denied for {} {}: {} - {}", exchange.getRequest().getMethod(), exchange.getRequest().getURI(), error.getClass().getSimpleName(), error.getMessage());
		return Mono.fromSupplier(() -> ResponseEntity.status(403).body(new ApiError(403, "forbidden", "Access denied")));
	}
	
	@ExceptionHandler(DuplicateKeyException.class)
	public Mono<ResponseEntity<ApiError>> handleDuplicateKey(Exception error, ServerWebExchange exchange) {
		log.warn("Duplicate key error returned by {} {}: {} - {}", exchange.getRequest().getMethod(), exchange.getRequest().getURI(), error.getClass().getSimpleName(), error.getMessage(), error);
		return Mono.fromSupplier(() -> ResponseEntity.status(409).body(new ApiError(409, "conflict", "Duplicate")));
	}
	
	@ExceptionHandler(Exception.class)
	public Mono<ResponseEntity<ApiError>> handleOtherError(Exception error, ServerWebExchange exchange) {
		log.warn("Generic error returned by {} {}: {} - {}", exchange.getRequest().getMethod(), exchange.getRequest().getURI(), error.getClass().getSimpleName(), error.getMessage(), error);
		return Mono.fromSupplier(() -> ResponseEntity.status(500).body(new ApiError(500, "internal-error", "Internal server error")));
	}
	
}
