package org.trailence.jobs.db;

import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import io.r2dbc.postgresql.codec.Json;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Table("jobs_queue")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class JobEntity {

	@Id
	private UUID id;
	
	private String type;
	private long startAt;
	private long nextRetryAt;
	private int retry;
	private long expiresAt;
	
	private Json data;
	
}
