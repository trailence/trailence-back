package org.trailence.user.rest;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.trailence.user.UserService;
import org.trailence.user.dto.ChangePasswordRequest;
import org.trailence.user.dto.ResetPasswordRequest;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/user/v1")
@RequiredArgsConstructor
public class UserV1Controller {

	private final UserService service;
	
	@GetMapping("sendChangePasswordCode")
	public Mono<Void> sendChangePasswordCode(@RequestParam("lang") String lang, Authentication auth) {
		return service.sendChangePasswordCode(auth.getPrincipal().toString(), lang, false);
	}
	
	@DeleteMapping("changePassword")
	public Mono<Void> cancelChangePassword(@RequestParam("token") String token) {
		return service.cancelChangePassword(token);
	}
	
	@PostMapping("changePassword")
	public Mono<Void> changePassword(@RequestBody @Valid ChangePasswordRequest request, Authentication auth) {
		return service.changePassword(auth.getPrincipal().toString(), request.getCode(), request.getNewPassword(), request.getPreviousPassword(), false);
	}
	
	@PostMapping("resetPassword")
	public Mono<Void> changePassword(@RequestBody ResetPasswordRequest request) {
		return service.changePassword(request.getEmail(), request.getCode(), request.getNewPassword(), null, true);
	}
	
}
