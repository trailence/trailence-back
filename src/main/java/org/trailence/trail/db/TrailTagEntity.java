package org.trailence.trail.db;

import java.util.UUID;

import org.springframework.data.relational.core.mapping.Table;
import org.springframework.data.relational.core.sql.Column;

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
	
	public static final org.springframework.data.relational.core.sql.Table TABLE = org.springframework.data.relational.core.sql.Table.create("trails_tags");
	public static final Column COL_TAG_UUID = Column.create("tag_uuid", TABLE);
	public static final Column COL_TRAIL_UUID = Column.create("trail_uuid", TABLE);
	public static final Column COL_OWNER = Column.create("owner", TABLE);
}
