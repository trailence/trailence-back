package org.trailence.trail.rest;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.trailence.global.TrailenceUtils;
import org.trailence.global.rest.RetryRest;
import org.trailence.preferences.AvatarService;
import org.trailence.trail.ModerationService;
import org.trailence.trail.PublicTrailService;
import org.trailence.trail.dto.CreatePublicTrailRequest;
import org.trailence.trail.dto.FeedbackToReview;
import org.trailence.trail.dto.Photo;
import org.trailence.trail.dto.PublicTrailRemoveRequest;
import org.trailence.trail.dto.Track;
import org.trailence.trail.dto.Trail;
import org.trailence.trail.dto.TrailAndPhotos;
import org.trailence.translations.TranslationService;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/moderation/v1")
@RequiredArgsConstructor
public class ModerationV1Controller {
	
	private final ModerationService service;
	private final PublicTrailService publicTrailService;
	private final TranslationService translation;
	private final AvatarService avatarService;

	@GetMapping("/trailsToReview")
	@PreAuthorize(TrailenceUtils.PREAUTHORIZE_ADMIN + " or " + TrailenceUtils.PREAUTHORIZE_MODERATOR)
	public Flux<TrailAndPhotos> getTrailsToReview(@RequestParam(name = "size", defaultValue = "100") int size, Authentication auth) {
		if (size < 1) size = 100;
		if (size > 500) size = 500;
		return service.getTrailsToReview(size, auth);
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
	
	@PostMapping(path = "/photoFromReview/{photoUuid}/{photoOwner}/{trailUuid}", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
	public Mono<Photo> storePhoto(
		@PathVariable("photoUuid") String photoUuid,
		@PathVariable("photoOwner") String photoOwner,
		@PathVariable("trailUuid") String trailUuid,
		@RequestHeader("X-Description") String description,
		@RequestHeader(name = "X-DateTaken", required = false) Long dateTaken,
		@RequestHeader(name = "X-Latitude", required = false) Long latitude,
		@RequestHeader(name = "X-Longitude", required = false) Long longitude,
		@RequestHeader(name = "X-Cover", defaultValue = "false") boolean isCover,
		@RequestHeader(name = "X-Index", defaultValue = "1") int index,
		@RequestHeader("Content-Length") long size,
		ServerHttpRequest request,
		Authentication auth
	) {
		return RetryRest.retry(service.createPhoto(photoUuid, photoOwner, trailUuid, description != null ? URLDecoder.decode(description, StandardCharsets.UTF_8) : null, dateTaken, latitude, longitude, isCover, index, request.getBody(), size, auth));
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
	
	@GetMapping("/trailToReview/{trailUuid}/{trailOwner}/currentPublic")
	@PreAuthorize(TrailenceUtils.PREAUTHORIZE_ADMIN + " or " + TrailenceUtils.PREAUTHORIZE_MODERATOR)
	public Mono<String> getPublicUuidForTrailToReview(
		@PathVariable("trailUuid") String trailUuid,
		@PathVariable("trailOwner") String trailOwner,
		Authentication auth
	) {
		return publicTrailService.getCurrentPublicUuid(trailUuid, trailOwner);
	}
	
	@PostMapping("/detectLanguage")
	@PreAuthorize(TrailenceUtils.PREAUTHORIZE_ADMIN + " or " + TrailenceUtils.PREAUTHORIZE_MODERATOR)
	public Mono<String> detectLanguage(@RequestBody String text) {
		return translation.detectLanguage(text).switchIfEmpty(Mono.just(""));
	}

	@PostMapping("/translate")
	@PreAuthorize(TrailenceUtils.PREAUTHORIZE_ADMIN + " or " + TrailenceUtils.PREAUTHORIZE_MODERATOR)
	public Mono<String> translate(@RequestBody String text, @RequestParam("from") String from, @RequestParam("to") String to) {
		return translation.translate(text, from, to).switchIfEmpty(Mono.just(""));
	}
	
	@PostMapping("/translateai")
	@PreAuthorize(TrailenceUtils.PREAUTHORIZE_ADMIN + " or " + TrailenceUtils.PREAUTHORIZE_MODERATOR)
	public Mono<String> translateWithAI(@RequestBody String text) {
		return translation.translateWithAI(text);
	}
	
	@GetMapping("/commentsToReview")
	@PreAuthorize(TrailenceUtils.PREAUTHORIZE_ADMIN + " or " + TrailenceUtils.PREAUTHORIZE_MODERATOR)
	public Mono<List<FeedbackToReview>> getFeedbackToReview(Authentication auth) {
		return service.getFeedbackToReview(auth);
	}
	
	@PutMapping("/commentsToReview/validate/{feedbackUuid}")
	@PreAuthorize(TrailenceUtils.PREAUTHORIZE_ADMIN + " or " + TrailenceUtils.PREAUTHORIZE_MODERATOR)
	public Mono<Void> validateFeedback(@PathVariable("feedbackUuid") String feedbackUuid) {
		return service.feedbackValidated(feedbackUuid);
	}
	
	@PutMapping("/commentsToReview/reply/validate/{replyUuid}")
	@PreAuthorize(TrailenceUtils.PREAUTHORIZE_ADMIN + " or " + TrailenceUtils.PREAUTHORIZE_MODERATOR)
	public Mono<Void> validateFeedbackReply(@PathVariable("replyUuid") String replyUuid) {
		return service.feedbackReplyValidated(replyUuid);
	}
	
	@GetMapping("/removeRequests")
	@PreAuthorize(TrailenceUtils.PREAUTHORIZE_ADMIN + " or " + TrailenceUtils.PREAUTHORIZE_MODERATOR)
	public Mono<List<PublicTrailRemoveRequest>> getRemoveRequests(Authentication auth) {
		return service.getRemoveRequests(auth);
	}
	
	@PostMapping("/removeRequests/decline")
	@PreAuthorize(TrailenceUtils.PREAUTHORIZE_ADMIN + " or " + TrailenceUtils.PREAUTHORIZE_MODERATOR)
	public Mono<Void> declineRemoveRequests(@RequestBody List<String> uuids, Authentication auth) {
		return service.declineRemoveRequests(uuids, auth);
	}
	
	@PostMapping("/removeRequests/accept")
	@PreAuthorize(TrailenceUtils.PREAUTHORIZE_ADMIN + " or " + TrailenceUtils.PREAUTHORIZE_MODERATOR)
	public Mono<Void> acceptRemoveRequests(@RequestBody List<String> uuids, Authentication auth) {
		return service.acceptRemoveRequests(uuids, auth);
	}
	
	@DeleteMapping("/publicTrail/{trailUuid}")
	@PreAuthorize(TrailenceUtils.PREAUTHORIZE_ADMIN)
	public Mono<Void> deletePublicTrail(@PathVariable("trailUuid") String trailUuid, Authentication auth) {
		return publicTrailService.deletePublicTrailAsModerator(trailUuid);
	}

	@GetMapping("/avatars")
	@PreAuthorize(TrailenceUtils.PREAUTHORIZE_ADMIN + " or " + TrailenceUtils.PREAUTHORIZE_MODERATOR)
	public Mono<List<String>> getAvatarsToReview(Authentication auth) {
		return avatarService.getAvatarsToReview(auth);
	}
	
	@GetMapping("/avatars/{email}")
	@PreAuthorize(TrailenceUtils.PREAUTHORIZE_ADMIN + " or " + TrailenceUtils.PREAUTHORIZE_MODERATOR)
	public Mono<ResponseEntity<Flux<DataBuffer>>> getAvatarFile(@PathVariable("email") String email, Authentication auth) {
		return RetryRest.retry(avatarService.getAvatarFileToReview(email, auth).map(flux -> ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM).body(flux)));
	}
	
	@PostMapping("/avatars/{email}/accept")
	@PreAuthorize(TrailenceUtils.PREAUTHORIZE_ADMIN + " or " + TrailenceUtils.PREAUTHORIZE_MODERATOR)
	public Mono<Void> acceptAvatar(@PathVariable("email") String email, Authentication auth) {
		return avatarService.avatarModeration(email, true, auth);
	}
	
	@PostMapping("/avatars/{email}/decline")
	@PreAuthorize(TrailenceUtils.PREAUTHORIZE_ADMIN + " or " + TrailenceUtils.PREAUTHORIZE_MODERATOR)
	public Mono<Void> declineAvatar(@PathVariable("email") String email, Authentication auth) {
		return avatarService.avatarModeration(email, false, auth);
	}
}
