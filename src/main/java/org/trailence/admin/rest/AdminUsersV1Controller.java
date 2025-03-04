package org.trailence.admin.rest;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.trailence.auth.AuthService;
import org.trailence.auth.dto.UserKey;
import org.trailence.global.TrailenceUtils;
import org.trailence.global.dto.PageResult;
import org.trailence.quotas.QuotaService;
import org.trailence.quotas.dto.UserQuotas;
import org.trailence.quotas.dto.UserSubscription;
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
	private final QuotaService quotaService;
	
	@GetMapping()
	public Mono<PageResult<User>> getUsers(Pageable pageable) {
		return service.getUsers(pageable);
	}
	
	@GetMapping("/{user}/keys")
	public Flux<UserKey> getUserKeys(@PathVariable("user") String user) {
		return authService.getUserKeys(user);
	}
	
	@PutMapping("/{user}/roles")
	public Mono<List<String>> updateUserRoles(@PathVariable("user") String user, @RequestBody List<String> roles) {
		return service.updateUserRoles(user, roles);
	}
	
	@GetMapping("/{user}/subscriptions")
	public Flux<UserSubscription> getUserSubscriptions(@PathVariable("user") String user) {
		return quotaService.getUserSubscriptions(user);
	}
	
	@PostMapping("/{user}/subscriptions")
	public Mono<UserSubscription> addUserSubscription(@PathVariable("user") String user, @RequestBody String planName) {
		return quotaService.addUserSubscription(user, planName);
	}
	
	@DeleteMapping("/{user}/subscriptions/{subscriptionUuid}")
	public Mono<UserSubscription> stopUserSubscription(@PathVariable("user") String user, @PathVariable("subscriptionUuid") UUID subscriptionUuid) {
		return quotaService.stopUserSubscription(user, subscriptionUuid);
	}

	@GetMapping("/{user}/quoats")
	@PreAuthorize(TrailenceUtils.PREAUTHORIZE_ADMIN)
	public Mono<UserQuotas> getUserQuotas(@PathVariable("user") String user) {
		return quotaService.getUserQuotas(user);
	}
	
}
