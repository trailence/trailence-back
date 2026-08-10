package org.trailence.trail.db;

import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import lombok.Data;
import lombok.NoArgsConstructor;

@Table("shared_collections")
@Data
@NoArgsConstructor
public class SharedCollectionEntity {

	@Id
	private UUID uuid;
	private String owner;
	
}
