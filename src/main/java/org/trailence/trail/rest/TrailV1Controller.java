package org.trailence.trail.rest;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.trailence.global.dto.UpdateResponse;
import org.trailence.global.dto.Versioned;
import org.trailence.trail.TrailService;
import org.trailence.trail.dto.Trail;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/api/trail/v1")
@RequiredArgsConstructor
public class TrailV1Controller {

	private final TrailService service;
	
	@PostMapping("/_bulkCreate")
	public Mono<List<Trail>> bulkCreate(@RequestBody List<Trail> trails, Authentication auth) {
		return service.bulkCreate(trails, auth);
	}
	
	@PutMapping("/_bulkUpdate")
	public Flux<Trail> bulkUpdate(@RequestBody List<Trail> trails, Authentication auth) {
		return service.bulkUpdate(trails, auth);
	}
	
	@PostMapping("/_bulkDelete")
	public Mono<Void> bulkDelete(@RequestBody List<String> uuids, Authentication auth) {
		return service.bulkDelete(uuids, auth);
	}
	
	@PostMapping("/_bulkGetUpdates")
	public Mono<UpdateResponse<Trail>> bulkGetUpdates(@RequestBody List<Versioned> known, Authentication auth) {
		return service.getUpdates(known, auth);
	}
	
}
