package org.trailence.trail.db;

import org.springframework.data.relational.core.mapping.Table;
import org.trailence.global.db.AbstractEntityUuidOwner;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Table("tracks")
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class TrackEntity extends AbstractEntityUuidOwner {

	private byte[] data;
	
}
