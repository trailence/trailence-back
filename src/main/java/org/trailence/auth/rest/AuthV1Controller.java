package org.trailence.auth.rest;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.trailence.auth.AuthService;
import org.trailence.auth.dto.AuthResponse;
import org.trailence.auth.dto.InitRenewRequest;
import org.trailence.auth.dto.InitRenewResponse;
import org.trailence.auth.dto.LoginRequest;
import org.trailence.auth.dto.RenewTokenRequest;
import org.trailence.auth.dto.UserKey;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/auth/v1")
@RequiredArgsConstructor
public class AuthV1Controller {

	private final AuthService service;
	
	@PostMapping("login")
	public Mono<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
		return service.login(request);
	}
	
	@PostMapping("init_renew")
	public Mono<InitRenewResponse> initRenew(@Valid @RequestBody InitRenewRequest request) {
		return service.initRenew(request);
	}
	
	@PostMapping("renew")
	public Mono<AuthResponse> renew(@Valid @RequestBody RenewTokenRequest request) {
		return service.renew(request);
	}
	
	@GetMapping("mykeys")
	public Flux<UserKey> getMyKeys(Authentication auth) {
		return service.getMyKeys(auth);
	}
	
	@DeleteMapping("mykeys/{keyid}")
	public Mono<Void> deleteMyKey(@PathVariable("keyid") String id, Authentication auth) {
		return service.deleteMyKey(id, auth);
	}
	
}
