package org.trailence.trail.db;

import java.util.UUID;

import org.springframework.data.relational.core.mapping.Table;
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

    private UUID originalTrackUuid;
    private UUID currentTrackUuid;

    private UUID collectionUuid;

}
