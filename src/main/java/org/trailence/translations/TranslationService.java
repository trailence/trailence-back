package org.trailence.translations;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class TranslationService {

	@Value("${trailence.translations.url:}")
	private String baseUrl;
	
	public Mono<String> detectLanguage(String text) {
		WebClient client = WebClient.builder().baseUrl(baseUrl).build();
		return client.post().uri("/detect").bodyValue(new DetectRequest(text))
		.exchangeToFlux(response -> {
			if (response.statusCode().is2xxSuccessful())
				return response.bodyToFlux(DetectResponse.class);
			return Flux.empty();
		})
		.collectList()
		.flatMap(results -> {
			if (results.isEmpty()) return Mono.empty();
			results.sort((r1, r2) -> r1.getConfidence() == null ? 1 : r2.getConfidence() == null ? -1 : r2.getConfidence().compareTo(r1.getConfidence()));
			return Mono.just(results.getFirst().getLanguage());
		});
	}
	
	@Data
	@AllArgsConstructor
	public static class DetectRequest {
		private String q;
	}
	
	@Data
	@NoArgsConstructor
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class DetectResponse {
		private Double confidence;
		private String language;
	}
	
}
