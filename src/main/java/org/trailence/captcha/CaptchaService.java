package org.trailence.captcha;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import jakarta.annotation.PostConstruct;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class CaptchaService {
	
	@Value("${trailence.external.captcha.clientKey:}")
	private String clientKey;
	@Value("${trailence.external.captcha.secretKey:}")
	private String secretKey;
	@Value("${trailence.external.captcha.url:}")
	private String url;
	@Value("${trailence.external.captcha.provider}")
	private String provider;
	
	@PostConstruct
	@SuppressWarnings({"java:S131", "java:S1301"})
	public void init() {
		if (this.url.isBlank())
			switch (this.provider) {
			case "recaptcha": this.url = "https://www.google.com/recaptcha/api/siteverify"; break;
			case "turnstile": this.url = "https://challenges.cloudflare.com/turnstile/v0/siteverify"; break;
			}
	}
	
	@Data
	@AllArgsConstructor
	@NoArgsConstructor
	public static class PublicConfig {
		private String provider;
		private String clientKey;
	}
	
	public boolean isActivated() {
		return !clientKey.isEmpty() && !secretKey.isEmpty() && (provider.equals("recaptcha") || provider.equals("turnstile"));
	}
	
	public PublicConfig getConfig() {
		return new PublicConfig(this.provider, this.clientKey);
	}

	public Mono<Boolean> validate(String token) {
		if (!isActivated()) return Mono.just(false);
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
