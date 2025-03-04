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
			Disposable d = schedule.getValue();
			if (d != null && !d.isDisposed()) d.dispose();
			long time = System.currentTimeMillis() - start;
			if (time > 2000) log.info("Request took {} ms: {} {}", time, exchange.getRequest().getMethod(), exchange.getRequest().getPath());
		}));
		schedule.setValue(Schedulers.boundedElastic().schedule(() -> {
			schedule.setValue(null);
			log.warn("Request not comitted after 10 seconds: {} {}", exchange.getRequest().getMethod(), exchange.getRequest().getPath());
		}, 10, TimeUnit.SECONDS));
		return chain.filter(exchange);
	}
	
}
