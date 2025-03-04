package org.trailence.quotas.db;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.data.relational.core.sql.Column;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Table("plans")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlanEntity {
	
	@Id
	private String name;
	
	private long collections;
	private long trails;
	private long tracks;
	private long tracksSize;
	private long photos;
	private long photosSize;
	private long tags;
	private long trailTags;
	private long shares;

	public static final org.springframework.data.relational.core.sql.Table TABLE = org.springframework.data.relational.core.sql.Table.create("plans");
	public static final Column COL_NAME = Column.create("name", TABLE);
	public static final Column COL_COLLECTIONS = Column.create("collections", TABLE);
	public static final Column COL_TRAILS = Column.create("trails", TABLE);
	public static final Column COL_TRACKS = Column.create("tracks", TABLE);
	public static final Column COL_TRACKS_SIZE = Column.create("tracks_size", TABLE);
	public static final Column COL_PHOTOS = Column.create("photos", TABLE);
	public static final Column COL_PHOTOS_SIZE = Column.create("photos_size", TABLE);
	public static final Column COL_TAGS = Column.create("tags", TABLE);
	public static final Column COL_TRAIL_TAGS = Column.create("trail_tags", TABLE);
	public static final Column COL_SHARES = Column.create("shares", TABLE);
}
