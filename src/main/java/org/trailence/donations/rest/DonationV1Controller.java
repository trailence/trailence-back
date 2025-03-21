package org.trailence.donations.rest;

import java.util.List;

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
import org.trailence.donations.DonationService;
import org.trailence.donations.dto.CreateDonationRequest;
import org.trailence.donations.dto.Donation;
import org.trailence.donations.dto.DonationGoal;
import org.trailence.donations.dto.DonationStatus;
import org.trailence.global.TrailenceUtils;
import org.trailence.global.dto.PageResult;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/donation/v1")
@RequiredArgsConstructor
public class DonationV1Controller {

	private final DonationService service;
	
	@GetMapping()
	@PreAuthorize(TrailenceUtils.PREAUTHORIZE_ADMIN)
	public Mono<PageResult<Donation>> getDonation(Pageable pageable) {
		return service.getDonations(pageable);
	}
	
	@PostMapping()
	@PreAuthorize(TrailenceUtils.PREAUTHORIZE_ADMIN)
	public Mono<Donation> createDonation(@RequestBody CreateDonationRequest request) {
		return service.createDonation(request);
	}
	
	@PutMapping("/{uuid}")
	@PreAuthorize(TrailenceUtils.PREAUTHORIZE_ADMIN)
	public Mono<Donation> updateDonation(@PathVariable("uuid") String uuid, @RequestBody Donation donation) {
		donation.setUuid(uuid);
		return service.updateDonation(donation);
	}
	
	@DeleteMapping("/{uuid}")
	@PreAuthorize(TrailenceUtils.PREAUTHORIZE_ADMIN)
	public Mono<Void> deleteDonation(@PathVariable("uuid") String uuid) {
		return service.deleteDonation(uuid);
	}
	
	@GetMapping("/goals")
	@PreAuthorize(TrailenceUtils.PREAUTHORIZE_ADMIN)
	public Mono<List<DonationGoal>> getGoals() {
		return service.getGoals();
	}
	
	@PostMapping("/goals")
	@PreAuthorize(TrailenceUtils.PREAUTHORIZE_ADMIN)
	public Mono<List<DonationGoal>> updateGoals(@RequestBody List<DonationGoal> goals) {
		return service.updateGoals(goals);
	}
	
	@GetMapping("/status")
	public Mono<DonationStatus> getStatus() {
		return service.getStatus();
	}
}
