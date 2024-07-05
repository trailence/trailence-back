package org.trailence.preferences.rest;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.trailence.preferences.UserPreferencesService;
import org.trailence.preferences.dto.UserPreferences;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/preferences/v1")
@RequiredArgsConstructor
public class UserPreferencesV1Controller {

	private final UserPreferencesService service;
	
	@GetMapping
	public Mono<UserPreferences> getPreferences(Authentication auth) {
		return service.getPreferences(auth.getPrincipal().toString());
	}
	
	@PutMapping
	public Mono<UserPreferences> setPreferences(@RequestBody UserPreferences dto, Authentication auth) {
		return service.setPreferences(dto, auth.getPrincipal().toString());
	}
	
}
