package org.trailence.trail.rest;

import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.trailence.trail.UserSelectionService;
import org.trailence.trail.dto.UserSelection;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/user_selection/v1")
@RequiredArgsConstructor
public class UserSelectionV1Controller {

	private final UserSelectionService service;
	
	@GetMapping
	public Mono<List<UserSelection>> getMySelection(Authentication auth) {
		return service.getMySelection(auth);
	}
	
	@PostMapping
	public Mono<List<UserSelection>> createSelection(@RequestBody List<UserSelection> list, Authentication auth) {
		return service.createSelection(list, auth);
	}
	
	@PostMapping("/delete")
	public Mono<Void> deleteSelection(@RequestBody List<UserSelection> list, Authentication auth) {
		return service.deleteSelection(list, auth);
	}
	
}
