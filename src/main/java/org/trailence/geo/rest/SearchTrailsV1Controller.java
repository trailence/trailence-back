package org.trailence.geo.rest;

import java.util.List;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.trailence.external.outdooractive.OutdoorActiveService;
import org.trailence.external.visorando.VisorandoService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/search-trails/v1")
@RequiredArgsConstructor
@Slf4j
public class SearchTrailsV1Controller {

	private final VisorandoService visorando;
	private final OutdoorActiveService outdoor;
	
	@GetMapping("/visorando/available")
	public boolean isVisorandoAvailable(Authentication auth) {
		return visorando.isAvailable(auth);
	}
	
	@GetMapping("/visorando")
	public Mono<List<VisorandoService.Rando>> searchVisorando(@RequestParam("bbox") String bbox, Authentication auth) {
		return visorando.searchBbox(bbox, auth);
	}
	
	@GetMapping("/outdooractive/available")
	public boolean isOutdoorActiveAvailable(Authentication auth) {
		return outdoor.available(auth);
	}
	
	@GetMapping("/outdooractive")
	public Mono<List<String>> searchOutdoorActive(@RequestParam("lat") double lat, @RequestParam("lng") double lng, @RequestParam("radius") int radius, @RequestParam("limit") int limit, Authentication auth) {
		return outdoor.search(lat, lng, radius, limit, auth);
	}
	
	@PostMapping("/outdooractive/trails")
	public Mono<List<OutdoorActiveService.Rando>> getTrails(@RequestBody List<String> ids, @RequestParam("lang") String lang, Authentication auth) {
		return outdoor.getDetails(ids, lang, auth);
	}
	
	@GetMapping(path = "/outdooractive/photo", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
	public ResponseEntity<Flux<DataBuffer>> getOutdoorActivePhoto(
		@RequestParam("id") String photoId,
		@RequestParam("size") String size,
		Authentication auth
	) {
		var flux = outdoor.getPhoto(photoId, size, auth).doOnError(e -> log.warn("Error retrieving outdoor active photo " + photoId + " " + size, e));
		return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM).body(flux);
	}
	
}
