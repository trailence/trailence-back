package org.trailence.trail.rest;

import java.util.List;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.trailence.trail.TrailLinkService;
import org.trailence.trail.dto.MyTrailLink;
import org.trailence.trail.dto.TrailLinkContent;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/trail-link/v1")
@RequiredArgsConstructor
public class TrailLinkV1Controller {

	private final TrailLinkService service;
	
	@GetMapping
	public Mono<List<MyTrailLink>> getMyLinks(Authentication auth) {
		return service.getMyLinks(auth);
	}
	
	@PostMapping
	public Mono<MyTrailLink> createLink(@RequestBody String trailUuid, Authentication auth) {
		return service.createLink(trailUuid, auth);
	}
	
	@DeleteMapping("/{trailUuid}")
	public Mono<Void> deleteLink(@PathVariable("trailUuid") String trailUuid, Authentication auth) {
		return service.deleteLink(trailUuid, auth);
	}
	
	@GetMapping("/trail/{link}")
	public Mono<TrailLinkContent> getTrailByLink(@PathVariable("link") String link) {
		return service.getTrailByLink(link);
	}
	
	@GetMapping(path = "/photo/{link}/{uuid}", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
	public Mono<ResponseEntity<Flux<DataBuffer>>> getFileContent(
		@PathVariable("link") String link,
		@PathVariable("uuid") String uuid
	) {
		return service.getPhoto(link, uuid).map(flux -> ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM).body(flux));
	}
	
}
