package org.trailence.trail.rest;

import java.time.Instant;
import java.util.List;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.trailence.global.exceptions.UnauthorizedException;
import org.trailence.trail.PublicTrailService;
import org.trailence.trail.dto.MyPublicTrail;
import org.trailence.trail.dto.PublicTrack;
import org.trailence.trail.dto.PublicTrail;
import org.trailence.trail.dto.PublicTrailSearch.SearchByBoundsRequest;
import org.trailence.trail.dto.PublicTrailSearch.SearchByBoundsResponse;
import org.trailence.trail.dto.PublicTrailSearch.SearchByTileRequest;
import org.trailence.trail.dto.PublicTrailSearch.SearchByTileResponse;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/public/trails/v1")
@RequiredArgsConstructor
public class PublicTrailV1Controller {
	
	private final PublicTrailService service;

	@PostMapping("/countByTile")
	public Mono<SearchByTileResponse> searchByTiles(@RequestBody SearchByTileRequest request) {
		return service.searchByTiles(request);
	}
	
	@PostMapping("/searchByBounds")
	public Mono<SearchByBoundsResponse> searchByBounds(@RequestBody SearchByBoundsRequest request) {
		return service.searchByBounds(request);
	}
	
	@GetMapping("/trailById/{uuid}")
	public Mono<PublicTrail> getById(@PathVariable("uuid") String uuid, Authentication auth) {
		return service.getById(uuid, auth);
	}
	
	@GetMapping("/trailBySlug/{slug}")
	public Mono<PublicTrail> getBySlug(@PathVariable("slug") String slug, Authentication auth) {
		return service.getBySlug(slug, auth);
	}
	
	@PostMapping("/trailsByIds")
	public Mono<List<PublicTrail>> getByIds(@RequestBody List<String> uuids, Authentication auth) {
		return service.getByIds(uuids, auth);
	}

	@GetMapping("/mine")
	public Flux<MyPublicTrail> getMines(Authentication auth) {
		if (auth == null) return Flux.error(new UnauthorizedException());
		return service.getMines(auth);
	}
	
	@GetMapping("/photo/{trailUuid}/{photoUuid}")
	public Mono<ResponseEntity<Flux<DataBuffer>>> getPhotoContent(
		@PathVariable("trailUuid") String trailUuid,
		@PathVariable("photoUuid") String photoUuid
	) {
		return service.getPhotoFileContent(trailUuid, photoUuid)
		.map(flux -> ResponseEntity
			.ok()
			.contentType(MediaType.APPLICATION_OCTET_STREAM)
			.header("Cache-Control", "public, max-age=86400")
			.header("Expires", Instant.now().plusSeconds(86400).toString())
			.body(flux)
		);
	}
	
	@GetMapping("/track/{trailUuid}")
	public Mono<PublicTrack> getTrack(@PathVariable("trailUuid") String trailUuid) {
		return service.getTrack(trailUuid);
	}
	
	@GetMapping(value = "/random", produces = {"text/html"})
	public Mono<String> random() {
		return service.randomSlugs()
		.collectList()
		.map(slugs -> {
			StringBuilder html = new StringBuilder(65536);
			html.append("<html><body>");
			for (var slug : slugs) {
				html.append("<a href=\"/fr/trail/").append(slug.getSlug()).append("\">").append(slug.getName()).append("</a><br/>");
				html.append("<a href=\"/en/trail/").append(slug.getSlug()).append("\">").append(slug.getName()).append("</a><br/>");
			}
			html.append("</body></html>");
			return html.toString();
		});
	}
	
	
}
