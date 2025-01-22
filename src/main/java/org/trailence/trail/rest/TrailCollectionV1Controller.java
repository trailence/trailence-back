package org.trailence.trail.rest;

import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.trailence.global.dto.UpdateResponse;
import org.trailence.global.dto.Versioned;
import org.trailence.trail.TrailCollectionService;
import org.trailence.trail.dto.TrailCollection;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/trail-collection/v1")
@RequiredArgsConstructor
public class TrailCollectionV1Controller {

	private final TrailCollectionService service;
	
	@PostMapping("/_bulkCreate")
	public Mono<List<TrailCollection>> bulkCreate(@RequestBody List<TrailCollection> collections, Authentication auth) {
		return service.bulkCreate(collections, auth);
	}
	
	@PutMapping("/_bulkUpdate")
	public Flux<TrailCollection> bulkUpdate(@RequestBody List<TrailCollection> collections, Authentication auth) {
		return service.bulkUpdate(collections, auth);
	}
	
	@PostMapping("/_bulkGetUpdates")
	public Mono<UpdateResponse<TrailCollection>> bulkGetUpdates(@RequestBody List<Versioned> known, Authentication auth) {
		return service.getUpdates(known, auth);
	}
	
	@PostMapping("/_bulkDelete")
	public Mono<Void> bulkDelete(@RequestBody List<String> uuids, Authentication auth) {
		return service.bulkDelete(uuids, auth);
	}
	
}
