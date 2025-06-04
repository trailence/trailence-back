package org.trailence.extensions.rest;

import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.trailence.extensions.UserExtensionsService;
import org.trailence.extensions.dto.UserExtension;
import org.trailence.global.rest.RetryRest;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/extensions/v1")
@RequiredArgsConstructor
public class ExtensionsV1Controller {

	private final UserExtensionsService service;
	
	@PostMapping
	public Flux<UserExtension> sync(@RequestBody List<UserExtension> list, Authentication auth) {
		return RetryRest.retry(service.syncMyExtensions(list, auth));
	}
	
}
