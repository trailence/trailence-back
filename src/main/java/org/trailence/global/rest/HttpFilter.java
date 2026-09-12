package org.trailence.global.rest;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.commons.lang3.mutable.MutableObject;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import lombok.extern.slf4j.Slf4j;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Slf4j
public class HttpFilter implements WebFilter {

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
		long start = System.currentTimeMillis();
		MutableObject<Disposable> schedule = new MutableObject<>(null);
		exchange.getResponse().beforeCommit(() -> Mono.fromRunnable(() -> {
			Disposable d = schedule.get();
			if (d != null && !d.isDisposed()) d.dispose();
			long time = System.currentTimeMillis() - start;
			if (time > 2000) log.info("Request took {} ms: {} {}", time, exchange.getRequest().getMethod(), exchange.getRequest().getPath());
		}));
		checkExchange(exchange, 10, 0, schedule);
		return chain.filter(exchange)
			.map(_ -> Boolean.TRUE)
			.switchIfEmpty(Mono.just(Boolean.TRUE))
			.timeout(Duration.ofMinutes(11))
			.doOnError(TimeoutException.class, _ -> {
				Disposable d = schedule.get();
				if (d != null && !d.isDisposed()) d.dispose();
				log.info("Request timeout: {} {}", exchange.getRequest().getMethod(), exchange.getRequest().getPath());
			})
			.doOnCancel(() -> {
				Disposable d = schedule.get();
				if (d != null && !d.isDisposed()) d.dispose();
				log.info("Request cancelled: {} {}", exchange.getRequest().getMethod(), exchange.getRequest().getPath());
			})
			.then();
	}
	
	private void checkExchange(ServerWebExchange exchange, int delay, int delayAlreadyDone, MutableObject<Disposable> schedule) {
		schedule.setValue(Schedulers.boundedElastic().schedule(() -> {
			log.warn("Request not comitted after {} seconds: {} {}", delayAlreadyDone + delay, exchange.getRequest().getMethod(), exchange.getRequest().getPath());
			if (delayAlreadyDone < 5 * 60)
				checkExchange(exchange, delay * 2, delayAlreadyDone + delay, schedule);
			else if (delayAlreadyDone < 10 * 60)
				checkExchange(exchange, delay, delayAlreadyDone + delay, schedule);
			else
				schedule.setValue(null);
		}, delay, TimeUnit.SECONDS));
	}
	
}
