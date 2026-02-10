package org.trailence.global.rest;

import java.util.concurrent.TimeUnit;

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
		checkExchange(exchange, 10, schedule);
		return chain.filter(exchange);
	}
	
	private void checkExchange(ServerWebExchange exchange, int seconds, MutableObject<Disposable> schedule) {
		schedule.setValue(Schedulers.boundedElastic().schedule(() -> {
			if (seconds < 10 * 60)
				checkExchange(exchange, seconds * 2, schedule);
			else
				schedule.setValue(null);
			log.warn("Request not comitted after {} seconds: {} {}", seconds, exchange.getRequest().getMethod(), exchange.getRequest().getPath());
		}, 10, TimeUnit.SECONDS));
	}
	
}
