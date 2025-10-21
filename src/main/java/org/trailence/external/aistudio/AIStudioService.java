package org.trailence.external.aistudio;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import reactor.core.publisher.Mono;

@Service
public class AIStudioService {

	@Value("${trailence.external.aistudio.baseUrl:}")
	private String baseUrl;
	@Value("${trailence.external.aistudio.apiKey:}")
	private String apiKey;
	
	private static final String[] models = {
		"gemini-2.5-pro",
		"gemini-2.5-flash-tts",
		"gemini-2.5-flash",
	};
	
	public Mono<String> generateContent(String text) {
		return generateContent(text, 0);
	}
	
	private Mono<String> generateContent(String text, int modelIndex) {
		if (modelIndex >= models.length) return Mono.just("");
		return generateContent(text, models[modelIndex])
		.switchIfEmpty(Mono.defer(() -> generateContent(text, modelIndex + 1)));
	}
	
	private Mono<String> generateContent(String text, String model) {
		if (apiKey.isBlank() || baseUrl.isBlank() || model.isBlank()) return Mono.just("");
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
