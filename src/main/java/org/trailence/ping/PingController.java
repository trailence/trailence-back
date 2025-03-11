package org.trailence.ping;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.AllArgsConstructor;
import lombok.Data;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/ping")
public class PingController {

	@GetMapping
	public Mono<PingResponse> ping() {
		return Mono.just(RESPONSE);
	}
	
	@Data
	@AllArgsConstructor
	public static class PingResponse {
		private String minSupportedVersion;
	}
	
	private static final PingResponse RESPONSE = new PingResponse("0.10.0");
	
}
