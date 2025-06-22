package org.trailence.trail.rest;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.trailence.global.TrailenceUtils;
import org.trailence.trail.ModerationService;
import org.trailence.trail.PublicTrailService;
import org.trailence.trail.dto.CreatePublicTrailRequest;
import org.trailence.trail.dto.Photo;
import org.trailence.trail.dto.Track;
import org.trailence.trail.dto.Trail;
import org.trailence.trail.dto.TrailAndPhotos;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/moderation/v1")
@RequiredArgsConstructor
public class ModerationV1Controller {
	
	private final ModerationService service;
	private final PublicTrailService publicTrailService;

	@GetMapping("/trailsToReview")
	@PreAuthorize(TrailenceUtils.PREAUTHORIZE_ADMIN + " or " + TrailenceUtils.PREAUTHORIZE_MODERATOR)
	public Flux<TrailAndPhotos> getTrailsToReview(Authentication auth) {
		return service.getTrailsToReview(auth);
	}
	
	@GetMapping("/trailToReview/{trailUuid}/{trailOwner}")
	@PreAuthorize(TrailenceUtils.PREAUTHORIZE_ADMIN + " or " + TrailenceUtils.PREAUTHORIZE_MODERATOR)
	public Mono<TrailAndPhotos> getTrailToReview(
		@PathVariable("trailUuid") String trailUuid,
		@PathVariable("trailOwner") String trailOwner,
		Authentication auth
	) {
		return service.getTrailToReview(trailUuid, trailOwner, auth);
	}
	
	@GetMapping("/trackFromReview/{trailUuid}/{trailOwner}/{trackUuid}")
	@PreAuthorize(TrailenceUtils.PREAUTHORIZE_ADMIN + " or " + TrailenceUtils.PREAUTHORIZE_MODERATOR)
	public Mono<Track> getTrackFromReview(
		@PathVariable("trailUuid") String trailUuid,
		@PathVariable("trailOwner") String trailOwner,
		@PathVariable("trackUuid") String trackUuid,
		Authentication auth
	) {
		return service.getTrackFromReview(trailUuid, trailOwner, trackUuid, auth);
	}
	
	@GetMapping("/photoFromReview/{photoUuid}/{photoOwner}")
	@PreAuthorize(TrailenceUtils.PREAUTHORIZE_ADMIN + " or " + TrailenceUtils.PREAUTHORIZE_MODERATOR)
	public Mono<ResponseEntity<Flux<DataBuffer>>> getPhotoFileContentFromReview(
		@PathVariable("photoUuid") String photoUuid,
		@PathVariable("photoOwner") String photoOwner,
		Authentication auth
	) {
		return service.getPhotoFileContentFromReview(photoOwner, photoUuid, auth)
		.map(flux -> ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM).body(flux));
	}

	
	@PutMapping("/trailToReview")
	@PreAuthorize(TrailenceUtils.PREAUTHORIZE_ADMIN + " or " + TrailenceUtils.PREAUTHORIZE_MODERATOR)
	public Mono<Trail> updateTrailToReview(
		@RequestBody Trail trail,
		Authentication auth
	) {
		return service.updateTrailForReview(trail, auth);
	}
	
	@PutMapping("/photoFromReview")
	@PreAuthorize(TrailenceUtils.PREAUTHORIZE_ADMIN + " or " + TrailenceUtils.PREAUTHORIZE_MODERATOR)
	public Mono<Photo> updatePhoto(
		@RequestBody Photo photo,
		Authentication auth
	) {
		return service.updatePhoto(photo, auth);
	}
	
	@DeleteMapping("/photoFromReview/{photoUuid}/{photoOwner}")
	@PreAuthorize(TrailenceUtils.PREAUTHORIZE_ADMIN + " or " + TrailenceUtils.PREAUTHORIZE_MODERATOR)
	public Mono<Void> updatePhoto(
		@PathVariable("photoUuid") String photoUuid,
		@PathVariable("photoOwner") String photoOwner,
		Authentication auth
	) {
		return service.deletePhoto(photoUuid, photoOwner, auth);
	}
	
	@PutMapping("/trackFromReview/{trailUuid}/{trailOwner}")
	@PreAuthorize(TrailenceUtils.PREAUTHORIZE_ADMIN + " or " + TrailenceUtils.PREAUTHORIZE_MODERATOR)
	public Mono<Trail> updateTrailTrack(
		@PathVariable("trailUuid") String trailUuid,
		@PathVariable("trailOwner") String trailOwner,
		@RequestBody Track track,
		Authentication auth
	) {
		return service.updateTrailTrack(trailUuid, trailOwner, track, auth);
	}
	
	@PostMapping("/publish")
	@PreAuthorize(TrailenceUtils.PREAUTHORIZE_ADMIN + " or " + TrailenceUtils.PREAUTHORIZE_MODERATOR)
	public Mono<String> publishTrail(@RequestBody CreatePublicTrailRequest request, Authentication auth) {
		return publicTrailService.create(request, auth);
	}

	@PostMapping("/reject")
	@PreAuthorize(TrailenceUtils.PREAUTHORIZE_ADMIN + " or " + TrailenceUtils.PREAUTHORIZE_MODERATOR)
	public Mono<Trail> rejectTrail(
		@RequestBody Trail trail,
		Authentication auth
	) {
		return service.reject(trail, auth);
	}
	
}
