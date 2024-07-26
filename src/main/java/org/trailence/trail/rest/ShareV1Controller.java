package org.trailence.trail.rest;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.trailence.trail.ShareService;
import org.trailence.trail.dto.CreateShareRequest;
import org.trailence.trail.dto.Share;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/share/v1")
@RequiredArgsConstructor
public class ShareV1Controller {

	private final ShareService service;
	
	@PostMapping
	public Mono<Share> createShare(@Valid @RequestBody CreateShareRequest request, Authentication auth) {
		return service.createShare(request, auth);
	}
	
	@GetMapping
	public Flux<Share> getShares(Authentication auth) {
		return service.getShares(auth);
	}
	
	@DeleteMapping("/{from}/{id}")
	public Mono<Void> deleteShare(@PathVariable("id") String id, @PathVariable("from") String from, Authentication auth) {
		return service.deleteShare(id, from, auth);
	}
	
}