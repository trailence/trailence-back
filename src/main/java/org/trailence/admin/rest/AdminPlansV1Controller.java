package org.trailence.admin.rest;

import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.trailence.global.dto.PageResult;
import org.trailence.quotas.QuotaService;
import org.trailence.quotas.dto.Plan;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/admin/plans/v1")
@RequiredArgsConstructor
public class AdminPlansV1Controller {

	private final QuotaService quotaService;
	
	@GetMapping()
	public Mono<PageResult<Plan>> getPlans(Pageable pageable) {
		return quotaService.getPlans(pageable);
	}
	
	@PostMapping()
	public Mono<Plan> createPlan(@RequestBody Plan plan) {
		return quotaService.createPlan(plan);
	}
	
	@PutMapping("/{planName}")
	public Mono<Plan> updatePlan(@PathVariable("planName") String planName, @RequestBody Plan plan) {
		return quotaService.updatePlan(planName, plan);
	}
	
	@DeleteMapping("/{planName}")
	public Mono<Void> deletePlan(@PathVariable("planName") String planName) {
		return quotaService.deletePlan(planName);
	}
	
	
}
