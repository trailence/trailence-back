package org.trailence.trail.db;

import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Table("trail_links")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TrailLinkEntity {
	@Id
	private UUID uuid;
	private UUID linkKey1;
	private UUID linkKey2;
	private String author;
	private UUID authorUuid;
	private long createdAt;
}
