package org.trailence.trail.db;

import java.util.UUID;

import org.springframework.data.relational.core.mapping.Table;
import org.springframework.data.relational.core.sql.Column;
import org.trailence.global.db.AbstractEntityUuidOwner;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Table("photos")
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class PhotoEntity extends AbstractEntityUuidOwner {

    private long fileId;
    private UUID trailUuid;
    
    private String description;
    private Long dateTaken;
    private Long latitude;
    private Long longitude;
    private boolean isCover;
    private int index;
    
    public static final org.springframework.data.relational.core.sql.Table TABLE = org.springframework.data.relational.core.sql.Table.create("photos");
    public static final Column COL_UUID = Column.create("uuid", TABLE);
    public static final Column COL_OWNER = Column.create("owner", TABLE);
    public static final Column COL_TRAIL_UUID = Column.create("trail_uuid", TABLE);
	
}
