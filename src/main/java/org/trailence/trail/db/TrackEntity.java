package org.trailence.trail.db;

import org.springframework.data.relational.core.mapping.Table;
import org.springframework.data.relational.core.sql.Column;
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
	
    public static final org.springframework.data.relational.core.sql.Table TABLE = org.springframework.data.relational.core.sql.Table.create("tracks");
    public static final Column COL_UUID = Column.create("uuid", TABLE);
    public static final Column COL_OWNER = Column.create("owner", TABLE);
    public static final Column COL_VERSION = Column.create("version", TABLE);

}
