package org.trailence.jobs;

import org.trailence.jobs.db.JobEntity;

import io.r2dbc.postgresql.codec.Json;
import lombok.AllArgsConstructor;
import lombok.Data;
import reactor.core.publisher.Mono;

public interface Job {

	String getType();
	
	long getInitialDelayMillis();
	long getExpirationDelayMillis();
	
	Long acceptNewJob(JobEntity job);
	
	Mono<Result> execute(Json data, int trial);
	
	@Data
	@AllArgsConstructor
	class Result {
		boolean success;
		Long retryAt;
	}
	
}
