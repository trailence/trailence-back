package org.trailence.geo.rest;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.trailence.external.visorando.VisorandoService;
import org.trailence.external.visorando.VisorandoService.Rando;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/search-trails/v1")
@RequiredArgsConstructor
public class SearchTrailsV1Controller {

	private final VisorandoService visorando;
	
	@GetMapping("/visorando")
	public Mono<List<Rando>> searchVisorando(@RequestParam("bbox") String bbox) {
		return visorando.searchBbox(bbox);
	}
	
}
