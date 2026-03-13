package org.trailence.admin.rest;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.trailence.stats.StatsService;
import org.trailence.stats.dto.StatsValue;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/admin/stats/v1")
@RequiredArgsConstructor
public class AdminStatsV1Controller {
	
	private final StatsService service;

	@GetMapping()
	
	public Mono<List<StatsValue>> getStats(@RequestParam("type") String type, @RequestParam("aggregation") String aggregation) {
		return service.getStats(type, aggregation);
	}
	
}
