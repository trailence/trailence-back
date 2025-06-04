package org.trailence.global.rest;

import java.time.Duration;

import org.apache.commons.lang3.RandomUtils;
import org.springframework.r2dbc.UncategorizedR2dbcException;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class RetryRest {

	public static boolean isRetryable(Throwable t) {
		if (t instanceof UncategorizedR2dbcException) {
			return true;
		}
		return false;
	}

	public static <T> Mono<T> retry(Mono<T> transaction) {
		return retry(transaction, 0);
	}
	
	private static <T> Mono<T> retry(Mono<T> transaction, int numRetry) {
		if (numRetry == 2) return transaction;
		return transaction.onErrorResume(error -> {
			if (isRetryable(error)) return delay(numRetry).then(retry(transaction, numRetry + 1));
			return Mono.error(error);
		});
	}
	
	public static <T> Flux<T> retry(Flux<T> transaction) {
		return retry(transaction, 0);
	}
	
	private static <T> Flux<T> retry(Flux<T> transaction, int numRetry) {
		if (numRetry == 2) return transaction;
		return transaction.onErrorResume(error -> {
			if (isRetryable(error)) return delay(numRetry).thenMany(retry(transaction, numRetry + 1));
			return Flux.error(error);
		});
	}
	
	private static Mono<Integer> delay(int numRetry) {
		if (numRetry == 0) return Mono.just(1).delayElement(Duration.ofMillis(RandomUtils.insecure().randomLong(10, 200)));
		return Mono.just(1).delayElement(Duration.ofMillis(RandomUtils.insecure().randomLong(numRetry * 10, numRetry * 200)));
	}
	
}
