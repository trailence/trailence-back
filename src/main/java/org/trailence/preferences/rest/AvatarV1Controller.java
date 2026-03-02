package org.trailence.preferences.rest;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.trailence.global.rest.RetryRest;
import org.trailence.preferences.AvatarService;
import org.trailence.preferences.dto.AvatarDto;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/avatar/v1")
@RequiredArgsConstructor
public class AvatarV1Controller {
	
	private final AvatarService service;
	
	@GetMapping()
	public Mono<AvatarDto> getMyAvatarInfo(Authentication auth) {
		return RetryRest.retry(service.getMyAvatarInfo(auth));
	}
	
	@GetMapping("/current")
	public Mono<ResponseEntity<Flux<DataBuffer>>> getMyCurrentAvatarFile(Authentication auth) {
		return RetryRest.retry(service.getMyCurrentAvatarFile(auth).map(flux -> ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM).body(flux)));
	}

	@GetMapping("/pending")
	public Mono<ResponseEntity<Flux<DataBuffer>>> getMyPendingAvatarFile(Authentication auth) {
		return RetryRest.retry(service.getMyPendingAvatarFile(auth).map(flux -> ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM).body(flux)));
	}

	@GetMapping("/public/{uuid}")
	public Mono<ResponseEntity<Flux<DataBuffer>>> getPublicAvatarFile(@PathVariable("uuid") String uuid) {
		return RetryRest.retry(service.getPublicAvatarFile(uuid).map(flux -> ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM).body(flux)));
	}
	
	@PostMapping(consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
	public Mono<AvatarDto> storeNewAvatar(
		@RequestHeader("X-Avatar-Public") boolean isPublic,
		@RequestHeader("Content-Length") long size,
		ServerHttpRequest request,
		Authentication auth
	) {
		return RetryRest.retry(service.storeNewAvatar(isPublic, request.getBody(), size, auth));
	}
	
	@DeleteMapping("/current")
	public Mono<AvatarDto> deleteMyCurrent(Authentication auth) {
		return RetryRest.retry(service.deleteMyCurrent(auth));
	}
	
	@DeleteMapping("/pending")
	public Mono<AvatarDto> deleteMyPending(Authentication auth) {
		return RetryRest.retry(service.deleteMyPending(auth));
	}
}
