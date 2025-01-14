package org.trailence.user.rest;

import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.trailence.auth.AuthService;
import org.trailence.auth.dto.UserKey;
import org.trailence.global.dto.PageResult;
import org.trailence.user.UserService;
import org.trailence.user.dto.User;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/admin/users/v1")
@RequiredArgsConstructor
public class AdminUsersV1Controller {

	private final UserService service;
	private final AuthService authService;
	
	@GetMapping()
	public Mono<PageResult<User>> getUsers(Pageable pageable) {
		return service.getUsers(pageable);
	}
	
	@GetMapping("/{user}/keys")
	public Flux<UserKey> getUserKeys(@PathVariable("user") String user) {
		return authService.getUserKeys(user);
	}
	
}
