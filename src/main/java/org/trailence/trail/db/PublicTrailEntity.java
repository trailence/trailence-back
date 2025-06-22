package org.trailence.trail.db;

import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.geo.Box;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.data.relational.core.sql.Column;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Table("public_trails")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PublicTrailEntity {

	@Id
	private UUID uuid;
	private String author;
	private UUID authorUuid;
	private long createdAt;
	private long updatedAt;
	private String slug;
	
	private String name;
	private String description;
	private String location;
	private long date;

	private long distance;
	private Long positiveElevation;
	private Long negativeElevation;
	private Long highestAltitude;
	private Long lowestAltitude;
	private Long duration;
	private long breaksDuration;
	private long estimatedDuration;
	
	private String loopType;
	private String activity;
	
	private Box bounds;
	
	private int tileZoom1;
	private int tileZoom2;
	private int tileZoom3;
	private int tileZoom4;
	private int tileZoom5;
	private int tileZoom6;
	private int tileZoom7;
	private int tileZoom8;
	private int tileZoom9;
	private int tileZoom10;
	
	private Integer[] simplifiedPath;
	
	private long nbRate0;
	private long nbRate1;
	private long nbRate2;
	private long nbRate3;
	private long nbRate4;
	private long nbRate5;
	
	public static final org.springframework.data.relational.core.sql.Table TABLE = org.springframework.data.relational.core.sql.Table.create("public_trails");
	public static final Column COL_UUID = Column.create("uuid", TABLE);
	public static final Column COL_DURATION = Column.create("duration", TABLE);
	public static final Column COL_BREAKS_DURATION = Column.create("breaks_duration", TABLE);
	public static final Column COL_ESTIMATED_DURATION = Column.create("estimated_duration", TABLE);
	public static final Column COL_DISTANCE = Column.create("distance", TABLE);
	public static final Column COL_POSITIVE_ELEVATION = Column.create("positive_elevation", TABLE);
	public static final Column COL_NEGATIVE_ELEVATION = Column.create("negative_elevation", TABLE);
	public static final Column COL_LOOP_TYPE = Column.create("loop_type", TABLE);
	public static final Column COL_ACTIVITY = Column.create("activity", TABLE);
	
}
