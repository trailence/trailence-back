package org.trailence.trail.rest;

import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.trailence.trail.ShareService;
import org.trailence.trail.dto.CreateShareRequest;
import org.trailence.trail.dto.Share;
import org.trailence.trail.dto.ShareElementType;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/share/v1")
@RequiredArgsConstructor
@Deprecated(since = "0.13.0", forRemoval = true)
@SuppressWarnings("java:S1123")
public class ShareV1Controller {

	private final ShareService service;
	
	@PostMapping
	public Mono<ShareV1> createShare(@Valid @RequestBody CreateShareRequestV1 request, Authentication auth) {
		return service.createShare(request.toV2(), auth).map(ShareV1::toV1);
	}
	
	@GetMapping
	public Flux<ShareV1> getShares(Authentication auth) {
		return service.getShares(auth).map(ShareV1::toV1);
	}
	
	@DeleteMapping("/{from}/{id}")
	public Mono<Void> deleteShare(@PathVariable("id") String id, @PathVariable("from") String from, Authentication auth) {
		return service.deleteShare(id, from, auth);
	}
	
	@Data
	@AllArgsConstructor
	@NoArgsConstructor
	public static class ShareV1 {

		private String id;
		private String name;
		private String from;
		private String to;
		private ShareElementType type;
		private long createdAt;
		private List<String> elements;
		private List<String> trails;
		private boolean includePhotos;
		
		private static ShareV1 toV1(Share v2) {
			return new ShareV1(
				v2.getUuid(),
				v2.getName(),
				v2.getOwner(),
				v2.getRecipients().getFirst(),
				v2.getType(),
				v2.getCreatedAt(),
				v2.getElements(),
				v2.getTrails(),
				v2.isIncludePhotos()
			);
		}
		
	}
	
	@Data
	@AllArgsConstructor
	@NoArgsConstructor
	public static class CreateShareRequestV1 {

		@NotNull
		private String id;
		@NotNull
		@Size(min = 1)
		private String name;
		@NotNull
		@Email
		private String to;
		@NotNull
		private ShareElementType type;
		@NotNull @NotEmpty
		private List<String> elements;
		@NotNull
		@Size(min = 2, max = 2)
		private String toLanguage;
		private boolean includePhotos = false;
		
		public CreateShareRequest toV2() {
			return new CreateShareRequest(
				id,
				name,
				List.of(to),
				type,
				elements,
				toLanguage,
				includePhotos
			);
		}
		
	}

	
}
