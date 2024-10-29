package org.trailence.geo.rest;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.trailence.external.geonames.GeonamesService;
import org.trailence.geo.dto.Place;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/place/v1")
@RequiredArgsConstructor
public class PlaceV1Controller {

	private final GeonamesService geonames;
	
	@GetMapping
	public Mono<List<List<String>>> getPlaces(@RequestParam("lat") double lat, @RequestParam("lng") double lng, @RequestParam("lang") String language) {
		return geonames.findNearbyPlaceName(lat, lng, language);
	}
	
	@GetMapping("/search")
	public Mono<List<Place>> searchPlace(@RequestParam("terms") String terms, @RequestParam("lang") String language) {
		return geonames.searchPlace(terms, language);
	}
	
}
