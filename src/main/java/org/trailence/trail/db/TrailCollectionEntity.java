package org.trailence.trail.db;

import org.springframework.data.relational.core.mapping.Table;
import org.trailence.global.db.AbstractEntityUuidOwner;
import org.trailence.trail.dto.TrailCollectionType;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Table("collections")
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class TrailCollectionEntity extends AbstractEntityUuidOwner {

    private String name;
    private TrailCollectionType type;

}
