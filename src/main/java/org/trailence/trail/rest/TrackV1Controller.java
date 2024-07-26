package org.trailence.trail.rest;

import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.trailence.global.dto.UpdateResponse;
import org.trailence.global.dto.UuidAndOwner;
import org.trailence.global.dto.Versioned;
import org.trailence.trail.TrackService;
import org.trailence.trail.dto.Track;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/track/v1")
@RequiredArgsConstructor
public class TrackV1Controller {

	private final TrackService service;

	@GetMapping("/{owner}/{uuid}")
	public Mono<Track> getTrack(@PathVariable("uuid") String uuid, @PathVariable("owner") String owner, Authentication auth) {
		return service.getTrack(uuid, owner, auth);
	}
	
	@PostMapping()
	public Mono<Track> create(@RequestBody Track track, Authentication auth) {
		return service.createTrack(track, auth);
	}
	
	@PutMapping()
	public Mono<Track> update(@RequestBody Track track, Authentication auth) {
		return service.updateTrack(track, auth);
	}
	
	@PostMapping("/_bulkDelete")
	public Mono<Void> bulkDelete(@RequestBody List<String> uuids, Authentication auth) {
		return service.bulkDelete(uuids, auth);
	}
	
	@PostMapping("/_bulkGetUpdates")
	public Mono<UpdateResponse<UuidAndOwner>> bulkGetUpdates(@RequestBody List<Versioned> known, Authentication auth) {
		return service.getUpdates(known, auth);
	}
	
}
