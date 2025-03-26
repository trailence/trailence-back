package org.trailence.external;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.trailence.external.fawazahmed0.CurrencyApi;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class CurrencyConverterService {
	
	private final CurrencyApi api;
	private long lastFetch = 0;
	private Map<String, Double> euroConversions = null;
	
	public Mono<BigDecimal> convertToEuro(String currency, BigDecimal amount) {
		return Mono.defer(this::getConversion)
		.flatMap(map -> {
			Double value = map.get(currency.toLowerCase());
			if (value == null) return Mono.error(new RuntimeException("Unknown currency: " + currency));
			return Mono.just(amount.divide(BigDecimal.valueOf(value), 10, RoundingMode.FLOOR));
		});
	}
	
	private Mono<Map<String, Double>> getConversion() {
		if (euroConversions != null && System.currentTimeMillis() - lastFetch < 60L * 60 * 1000) {
			return Mono.just(euroConversions);
		}
		return api.getEuroConversion();
	}

}
