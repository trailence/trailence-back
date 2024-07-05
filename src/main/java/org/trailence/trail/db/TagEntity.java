package org.trailence.trail.db;

import java.util.UUID;

import org.springframework.data.relational.core.mapping.Table;
import org.trailence.global.db.AbstractEntityUuidOwner;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Table("tags")
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class TagEntity extends AbstractEntityUuidOwner {

	private String name;
	private UUID parentUuid;
	
	private UUID collectionUuid;
	
}
