package org.trailence.captcha;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class CaptchaService {
	
	@Value("${trailence.external.captcha.clientKey:}")
	private String clientKey;
	@Value("${trailence.external.captcha.secretKey:}")
	private String secretKey;
	@Value("${trailence.external.captcha.url}")
	private String url;
	
	public boolean isActivated() {
		return !clientKey.isEmpty() && !secretKey.isEmpty();
	}
	
	public Mono<String> getKey() {
		return Mono.just(clientKey);
	}

	public Mono<Boolean> validate(String token) {
		if (clientKey.isEmpty() || secretKey.isEmpty()) return Mono.just(false);
		WebClient client = WebClient.builder().baseUrl(url).build();
		return client.post()
		.body(BodyInserters.fromFormData("secret", secretKey).with("response", token))
		.exchangeToMono(response -> response.bodyToMono(Map.class))
		.map(response -> {
			Object success = response.get("success");
			return Boolean.TRUE.equals(success);
		});
	}
	
}
