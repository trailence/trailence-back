package org.trailence.geo.rest;

import java.time.Duration;
import java.util.Set;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.trailence.global.exceptions.NotFoundException;
import org.trailence.storage.provider.FileStorageProviderService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/geo-data/v1")
@RequiredArgsConstructor
@Slf4j
public class GeoDataV1Controller {

	private final FileStorageProviderService storageService;
	
	private static final Set<String> KNOWN_TYPES = Set.of("guidepost", "drinking_water", "toilets", "ways"); 
	
	@GetMapping("/{type}/{tile}")
	public Mono<ResponseEntity<Flux<DataBuffer>>> getTile(@PathVariable("type") String type, @PathVariable("tile") String tile) {
		if (!KNOWN_TYPES.contains(type)) return Mono.error(new NotFoundException(type, tile));
		Long tileNum = Long.parseLong(tile);
		String filePath;
		switch (type) {
		case "ways":
			filePath = type + "/" + (tileNum / 1000) + "/" + tileNum + ".tile";
			break;
		default:
			filePath = type + "/" + tileNum + ".tile";
		}
		var getFile = storageService.getLocation("osmData")
			.map(storage -> storage.getFile(null, filePath, () -> new NotFoundException(type, tile)))
			.switchIfEmpty(Mono.error(new NotFoundException(type, tile)));
		return getFile.map(buffers ->
			ResponseEntity.ok()
			.contentType(MediaType.APPLICATION_OCTET_STREAM)
			.cacheControl(CacheControl.maxAge(Duration.ofDays(1)))
			.body(buffers)
		);
	}
	
}
