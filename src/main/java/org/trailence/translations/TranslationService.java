package org.trailence.translations;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class TranslationService {

	@Value("${trailence.translations.url:}")
	private String baseUrl;
	
	public Mono<String> detectLanguage(String text) {
		WebClient client = WebClient.builder().baseUrl(baseUrl).build();
		return client.post().uri("/detect").bodyValue(new DetectRequest(text))
		.exchangeToFlux(response -> {
			if (response.statusCode().is2xxSuccessful())
				return response.bodyToFlux(DetectResponse.class);
			log.warn("Cannot detect language: code {} for text {}", response.statusCode().value(), text);
			return Flux.empty();
		})
		.collectList()
		.flatMap(results -> {
			if (results.isEmpty()) return Mono.empty();
			results.sort((r1, r2) -> r1.getConfidence() == null ? 1 : r2.getConfidence() == null ? -1 : r2.getConfidence().compareTo(r1.getConfidence()));
			return Mono.just(results.getFirst().getLanguage());
		})
		.onErrorResume(e -> {
			log.error("Error detecting language for text {}", text, e);
			return Mono.empty();
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
	
	public Mono<String> translate(String text, String from, String to) {
		WebClient client = WebClient.builder().baseUrl(baseUrl).build();
		return client.post().uri("/translate").bodyValue(new TranslateRequest(text, from, to, "text", 0))
		.exchangeToMono(response -> {
			if (response.statusCode().is2xxSuccessful())
				return response.bodyToMono(TranslateResponse.class);
			log.warn("Cannot translate from {} to {}: code {} for text {}", from, to, response.statusCode().value(), text);
			return Mono.empty();
		})
		.map(response -> response.getTranslatedText())
		.onErrorResume(e -> {
			log.error("Error translating from {} to {} text {}", from, to, text, e);
			return Mono.empty();
		});
	}
	
	@Data
	@AllArgsConstructor
	public static class TranslateRequest {
		private String q;
		private String source;
		private String target;
		private String format;
		private int alternatives;
	}
	
	@Data
	@NoArgsConstructor
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class TranslateResponse {
		private String translatedText;
	}
	
}
