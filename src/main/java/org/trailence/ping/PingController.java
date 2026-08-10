package org.trailence.ping;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.PostConstruct;
import lombok.AllArgsConstructor;
import lombok.Data;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/ping")
public class PingController {
	
	@Data
	@AllArgsConstructor
	public static class PingResponse {
		private String minSupportedVersion;
		private Map<Integer, Long> osmDataVersions;
	}
	
	@Value("${trailence.osm-data.v1}")
	private long osmDataVersionV1;
	
	private static final String MIN_SUPPORTED_VERSION = "0.13.0";
	
	private static PingResponse response = null;
	
	@PostConstruct
	@SuppressWarnings("java:S2696")
	public void init() {
		response = new PingResponse(MIN_SUPPORTED_VERSION, Map.of(1, osmDataVersionV1));
	}

	@GetMapping
	public Mono<PingResponse> ping() {
		return Mono.just(response);
	}
	
}
