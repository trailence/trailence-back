package org.trailence.contact.rest;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.trailence.contact.ContactService;
import org.trailence.contact.dto.ContactMessage;
import org.trailence.contact.dto.CreateMessageRequest;
import org.trailence.global.dto.PageResult;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/contact/v1")
@RequiredArgsConstructor
public class ContactV1Controller {
	
	private final ContactService service;

	@PostMapping
	public Mono<Void> createMessage(@Valid @RequestBody CreateMessageRequest request, Authentication auth) {
		return service.createMessage(request, auth);
	}

	@GetMapping("/unread")
	public Mono<Long> getUnreadCount() {
		return service.getUnreadCount();
	}
	
	@GetMapping
	public Mono<PageResult<ContactMessage>> getMessages(Pageable pageable) {
		return service.getMessages(pageable);
	}
	
	@PutMapping("read")
	public Mono<Void> markMessagesAsRead(@RequestBody List<String> uuids) {
		return service.markAsRead(uuids, true);
	}
	
	@PutMapping("unread")
	public Mono<Void> markMessagesAsUnread(@RequestBody List<String> uuids) {
		return service.markAsRead(uuids, false);
	}
	
	@PostMapping("delete")
	public Mono<Void> deleteMessages(@RequestBody List<String> uuids) {
		return service.deleteMessages(uuids);
	}
	
}
