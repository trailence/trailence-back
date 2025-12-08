package org.trailence.external.aistudio;

import java.util.List;

import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.annotation.PostConstruct;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class AIStudioService {

	@Value("${trailence.external.aistudio.baseUrl:}")
	private String baseUrl;
	@Value("${trailence.external.aistudio.apiKey:}")
	private String apiKey;
	@Value("${trailence.external.aistudio.models:gemini-2.5-pro,gemini-2.5-flash-tts,gemini-2.5-flash}")
	private String configuredModels;
	
	@PostConstruct
	public void init() {
		if (!this.configuredModels.isBlank())
			this.models = this.configuredModels.split(",");
	}
	
	private String[] models = {};
	
	public void logStatus(Logger logger) {
		if (baseUrl.isBlank()) {
			logger.warn(" ❌ AI Studio not configured: no base URL");
		} else if (apiKey.isBlank()) {
			logger.warn(" ❌ AI Studio not configured: no api key");
		} else if (this.models.length == 0) {
			logger.warn(" ❌ AI Studio not configured: no model");
		} else {
			logger.info(" ✔ AI Studio configured: {}, with base URL: {}", this.models, this.baseUrl);
		}
	}
	
	public Mono<String> generateContent(String text) {
		return generateContent(text, 0);
	}
	
	private Mono<String> generateContent(String text, int modelIndex) {
		if (modelIndex >= models.length) {
			log.info("No more available AI Studio model");
			return Mono.just("");
		}
		return generateContent(text, models[modelIndex])
		.switchIfEmpty(Mono.defer(() -> {
			log.info("Model reached rate limits or invalid: {}", models[modelIndex]);
			return generateContent(text, modelIndex + 1);
		}));
	}
	
	private Mono<String> generateContent(String text, String model) {
		if (apiKey.isBlank() || baseUrl.isBlank() || model.isBlank()) {
			log.warn("Missing AI Studio config");
			return Mono.just("");
		}
		WebClient client = WebClient.builder().baseUrl(this.baseUrl).build();
		return client
		.post()
		.uri("/" + model + ":generateContent")
		.header("x-goog-api-key", this.apiKey)
		.header("Content-Type", "application/json")
		.body(BodyInserters.fromValue(new GeminiRequest(List.of(new GeminiRequestContent(List.of(new GeminiRequestContentPart(text)))))))
		.exchangeToMono(response -> response.bodyToMono(GeminiResponse.class))
		.flatMap(response -> {
			if (response.getCandidates() == null) return Mono.empty();
			for (var candidate : response.getCandidates()) {
				if (candidate.getContent() == null || candidate.getContent().getParts() == null || candidate.getContent().getParts().isEmpty()) continue;
				var part = candidate.getContent().getParts().get(0);
				if (part.text != null && !part.text.isBlank()) return Mono.just(part.text);
			}
			return Mono.empty();
		});
	}
	
	@Data
	@AllArgsConstructor
	public static class GeminiRequest {
		private List<GeminiRequestContent> contents;
	}
	
	@Data
	@AllArgsConstructor
	public static class GeminiRequestContent {
		private List<GeminiRequestContentPart> parts;
	}
	
	@Data
	@AllArgsConstructor
	public static class GeminiRequestContentPart {
		private String text;
	}
	
	@Data
	@AllArgsConstructor
	@NoArgsConstructor
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class GeminiResponse {
		private List<GeminiResponseCandidate> candidates;
	}
	
	@Data
	@AllArgsConstructor
	@NoArgsConstructor
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class GeminiResponseCandidate {
		private GeminiResponseCandidateContent content;
	}
	
	@Data
	@AllArgsConstructor
	@NoArgsConstructor
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class GeminiResponseCandidateContent {
		private List<GeminiResponseCandidateContentPart> parts;
	}

	@Data
	@AllArgsConstructor
	@NoArgsConstructor
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class GeminiResponseCandidateContentPart {
		private String text;
	}
}
