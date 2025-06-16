package org.trailence.trail.db;

import java.util.UUID;

import org.springframework.data.relational.core.mapping.Table;
import org.springframework.data.relational.core.sql.Column;
import org.trailence.global.db.AbstractEntityUuidOwner;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Table("trails")
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class TrailEntity extends AbstractEntityUuidOwner {

    private String name;
    private String description;
    private String location;
    private Long date;
    private String loopType;
    private String activity;
    private String sourceType;
    private String source;
    private Long sourceDate;

    private UUID originalTrackUuid;
    private UUID currentTrackUuid;

    private UUID collectionUuid;
    
    public static final org.springframework.data.relational.core.sql.Table TABLE = org.springframework.data.relational.core.sql.Table.create("trails");
    public static final Column COL_UUID = Column.create("uuid", TABLE);
    public static final Column COL_OWNER = Column.create("owner", TABLE);
    public static final Column COL_ORIGINAL_TRACK_UUID = Column.create("original_track_uuid", TABLE);
    public static final Column COL_CURRENT_TRACK_UUID = Column.create("current_track_uuid", TABLE);
    public static final Column COL_COLLECTION_UUID = Column.create("collection_uuid", TABLE);

}
