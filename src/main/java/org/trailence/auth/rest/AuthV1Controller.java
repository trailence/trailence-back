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
import org.trailence.auth.dto.ForgotPasswordRequest;
import org.trailence.auth.dto.InitRenewRequest;
import org.trailence.auth.dto.InitRenewResponse;
import org.trailence.auth.dto.LoginRequest;
import org.trailence.auth.dto.LoginShareRequest;
import org.trailence.auth.dto.RenewTokenRequest;
import org.trailence.auth.dto.UserKey;
import org.trailence.captcha.CaptchaService;
import org.trailence.global.rest.RetryRest;

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
		return RetryRest.retry(service.login(request));
	}
	
	@PostMapping("init_renew")
	public Mono<InitRenewResponse> initRenew(@Valid @RequestBody InitRenewRequest request) {
		return RetryRest.retry(service.initRenew(request));
	}
	
	@PostMapping("renew")
	public Mono<AuthResponse> renew(@Valid @RequestBody RenewTokenRequest request) {
		return RetryRest.retry(service.renew(request));
	}
	
	@GetMapping("mykeys")
	public Flux<UserKey> getMyKeys(Authentication auth) {
		return RetryRest.retry(service.getMyKeys(auth));
	}
	
	@DeleteMapping("mykeys/{keyid}")
	public Mono<Void> deleteMyKey(@PathVariable("keyid") String id, Authentication auth) {
		return RetryRest.retry(service.deleteMyKey(id, auth));
	}
	
	@PostMapping("share")
	public Mono<AuthResponse> loginShare(@Valid @RequestBody LoginShareRequest request) {
		return RetryRest.retry(service.loginShare(request));
	}
	
	@GetMapping("captcha")
	public Mono<CaptchaService.PublicConfig> getCaptchaConfig() {
		return RetryRest.retry(service.getCaptchaConfig());
	}
	
	@PostMapping("forgot")
	public Mono<Void> forgotPassword(@RequestBody ForgotPasswordRequest request) {
		return RetryRest.retry(service.forgotPassword(request));
	}
	
}
