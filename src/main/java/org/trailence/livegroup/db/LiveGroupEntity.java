package org.trailence.livegroup.db;

import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Table("live_groups")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LiveGroupEntity {
	
	@Id
	private UUID uuid;
	private String owner;
	private String slug;
	private String name;
	private long startedAt;
	private String trailOwner;
	private String trailUuid;
	private boolean trailShared;
	
}
