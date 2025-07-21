package org.trailence.notifications.rest;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.trailence.notifications.NotificationsService;
import org.trailence.notifications.dto.Notification;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/notifications/v1")
@RequiredArgsConstructor
public class NotificationsV1Controller {

	private final NotificationsService service;
	
	@GetMapping
	public Flux<Notification> getMyNotifications(
		@RequestParam(name = "page", required = false, defaultValue = "0") int page,
		@RequestParam(name = "size", required = false, defaultValue = "100") int size,
		Authentication auth
	) {
		return service.getMyNotifications(page, size, auth);
	}
	
	@PutMapping("/{uuid}")
	public Mono<Notification> updateNotification(@PathVariable("uuid") String uuid, @RequestBody Notification body, Authentication auth) {
		return service.updateNotification(uuid, body, auth);
	}
	
}
