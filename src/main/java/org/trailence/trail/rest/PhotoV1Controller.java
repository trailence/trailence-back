package org.trailence.trail.rest;

import java.util.List;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.trailence.global.dto.UpdateResponse;
import org.trailence.global.dto.Versioned;
import org.trailence.trail.PhotoService;
import org.trailence.trail.dto.Photo;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/photo/v1")
@RequiredArgsConstructor
public class PhotoV1Controller {
	
	private final PhotoService service;

	@PostMapping(path = "/{trailUuid}/{photoUuid}", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
	public Mono<Photo> storePhoto(
		@PathVariable("photoUuid") String photoUuid,
		@PathVariable("trailUuid") String trailUuid,
		@RequestHeader("X-Description") String description,
		@RequestHeader(name = "X-DateTaken", required = false) Long dateTaken,
		@RequestHeader(name = "X-Latitude", required = false) Long latitude,
		@RequestHeader(name = "X-Longitude", required = false) Long longitude,
		@RequestHeader(name = "X-Cover", defaultValue = "false") boolean isCover,
		@RequestHeader(name = "X-Index", defaultValue = "1") int index,
		@RequestHeader("Content-Length") long size,
		ServerHttpRequest request,
		Authentication auth
	) {
		return service.storePhoto(photoUuid, trailUuid, description, dateTaken, latitude, longitude, isCover, index, request.getBody(), size, auth);
	}
	
	@PutMapping("/_bulkUpdate")
	public Flux<Photo> bulkUpdate(@RequestBody List<Photo> photos, Authentication auth) {
		return service.bulkUpdate(photos, auth);
	}
	
	@PostMapping("/_bulkDelete")
	public Mono<Void> bulkDelete(@RequestBody List<String> uuids, Authentication auth) {
		return service.bulkDelete(uuids, auth);
	}
	
	@PostMapping("/_bulkGetUpdates")
	public Mono<UpdateResponse<Photo>> bulkGetUpdates(@RequestBody List<Versioned> known, Authentication auth) {
		return service.getUpdates(known, auth);
	}
	
	@GetMapping(path = "/{owner}/{uuid}", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
	public Mono<ResponseEntity<Flux<DataBuffer>>> getFileContent(
		@PathVariable("owner") String owner,
		@PathVariable("uuid") String uuid,
		Authentication auth
	) {
		return service.getFileContent(owner, uuid, auth).map(flux -> ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM).body(flux));
	}
}
