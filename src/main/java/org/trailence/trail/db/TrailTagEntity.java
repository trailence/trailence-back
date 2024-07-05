package org.trailence.trail.db;

import java.util.UUID;

import org.springframework.data.relational.core.mapping.Table;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Table("trails_tags")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TrailTagEntity {

	private UUID tagUuid;
	private UUID trailUuid;
	private String owner;
	private long createdAt;
	
}
