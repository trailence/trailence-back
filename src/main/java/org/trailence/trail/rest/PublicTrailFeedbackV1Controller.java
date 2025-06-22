package org.trailence.trail.rest;

import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.trailence.trail.PublicTrailService;
import org.trailence.trail.dto.CreateFeedbackRequest;
import org.trailence.trail.dto.MyFeedback;
import org.trailence.trail.dto.PublicTrailFeedback;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/public_trail_feedback/v1")
@RequiredArgsConstructor
public class PublicTrailFeedbackV1Controller {
	
	private final PublicTrailService service;

	@PostMapping()
	public Mono<Void> createFeedback(@RequestBody CreateFeedbackRequest request, Authentication auth) {
		return service.createFeedback(request, auth);
	}
	
	@DeleteMapping("/{feedbackUuid}")
	public Mono<Void> deleteComment(
		@PathVariable("feedbackUuid") String feedbackUuid,
		Authentication auth
	) {
		return service.deleteComment(feedbackUuid, auth);
	}
	
	@PostMapping("/{feedbackUuid}")
	public Mono<PublicTrailFeedback.Reply> replyToFeedback(
		@PathVariable("feedbackUuid") String feedbackUuid,
		@RequestBody String reply,
		Authentication auth
	) {
		return service.replyToFeedback(feedbackUuid, reply, auth);
	}

	@DeleteMapping("/reply/{replyUuid}")
	public Mono<Void> deleteReply(
		@PathVariable("replyUuid") String replyUuid,
		Authentication auth
	) {
		return service.deleteReply(replyUuid, auth);
	}
	
	@GetMapping("/{trailUuid}")
	public Mono<List<PublicTrailFeedback>> getFeedbacks(
		@PathVariable("trailUuid") String trailUuid,
		@RequestParam(name = "pageFromDate", required = false, defaultValue = "0") long pageFromDate,
		@RequestParam(name = "size", required = false, defaultValue = "25") int size,
		@RequestParam(name = "pageFromDateExclude", required = false, defaultValue = "") String excludeFromStartingDate,
		@RequestParam(name = "filterRate", required = false) Integer filterRate,
		Authentication auth
	) {
		return service.getFeedbacks(trailUuid, pageFromDate, size, excludeFromStartingDate, filterRate, auth);
	}
	
	@GetMapping("/{trailUuid}/mine")
	public Mono<MyFeedback> getMyFeedback(@PathVariable("trailUuid") String trailUuid, Authentication auth) {
		return service.getMyFeedback(trailUuid, auth);
	}
}
