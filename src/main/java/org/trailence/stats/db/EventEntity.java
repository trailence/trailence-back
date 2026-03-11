package org.trailence.stats.db;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import io.r2dbc.postgresql.codec.Json;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Table("events")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class EventEntity {

	@Id
	private Long id;
	private String type;
	private Long timestamp;
	private Json data;

}
