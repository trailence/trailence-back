package org.trailence.external.fawazahmed0;

import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.core.publisher.Mono;

@Service
public class CurrencyApi {

	public Mono<Map<String, Double>> getEuroConversion() {
		WebClient client = WebClient.builder().baseUrl("https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@latest/v1/currencies/eur.json").build();
		return client.get()
		.header("Cache", "no-cache")
		.exchangeToMono(response -> response.bodyToMono(Map.class))
		.map(response -> {
			@SuppressWarnings({ "unchecked" })
			var eur = (Map<String, Object>) response.get("eur");
			var result = new HashMap<String, Double>();
			for (var entry : eur.entrySet()) {
				if (entry.getValue() instanceof Number n) result.put(entry.getKey(), n.doubleValue());
			}
			return result;
		});
	}
	
}
