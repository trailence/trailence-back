package org.trailence.trail.rest;

import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.trailence.global.dto.UpdateResponse;
import org.trailence.global.dto.Versioned;
import org.trailence.trail.TagService;
import org.trailence.trail.TrailTagService;
import org.trailence.trail.dto.Tag;
import org.trailence.trail.dto.TrailTag;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/tag/v1")
@RequiredArgsConstructor
public class TagV1Controller {

	private final TagService tagService;
	private final TrailTagService trailTagService;
	
	@PostMapping("/_bulkCreate")
	public Mono<List<Tag>> bulkCreate(@RequestBody List<Tag> tags, Authentication auth) {
		return tagService.bulkCreate(tags, auth);
	}
	
	@PutMapping("/_bulkUpdate")
	public Flux<Tag> bulkUpdate(@RequestBody List<Tag> tags, Authentication auth) {
		return tagService.bulkUpdate(tags, auth);
	}
	
	@PostMapping("/_bulkDelete")
	public Mono<Void> bulkDelete(@RequestBody List<String> uuids, Authentication auth) {
		return tagService.bulkDelete(uuids, auth);
	}
	
	@PostMapping("/_bulkGetUpdates")
	public Mono<UpdateResponse<Tag>> bulkGetUpdates(@RequestBody List<Versioned> known, Authentication auth) {
		return tagService.getUpdates(known, auth);
	}


	@GetMapping("/trails")
	public Flux<TrailTag> getAllTrailTags(Authentication auth) {
		return trailTagService.getAll(auth);
	}
	
	@PostMapping("/trails/_bulkCreate")
	public Mono<List<TrailTag>> bulkCreateTrailsTags(@RequestBody List<TrailTag> dtos, Authentication auth) {
		return trailTagService.bulkCreate(dtos, auth);
	}
	
	@PostMapping("/trails/_bulkDelete")
	public Mono<Void> bulkDeleteTrailsTags(@RequestBody List<TrailTag> dtos, Authentication auth) {
		return trailTagService.bulkDelete(dtos, auth);
	}
	
}
