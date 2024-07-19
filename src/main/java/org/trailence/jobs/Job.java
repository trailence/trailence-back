package org.trailence.jobs;

import io.r2dbc.postgresql.codec.Json;
import lombok.AllArgsConstructor;
import lombok.Data;
import reactor.core.publisher.Mono;

public interface Job {

	String getType();
	
	long getInitialDelayMillis();
	long getExpirationDelayMillis();
	
	Mono<Result> execute(Json data, int trial);
	
	@Data
	@AllArgsConstructor
	class Result {
		Long retryAt;
	}
	
}
