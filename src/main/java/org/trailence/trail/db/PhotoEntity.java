package org.trailence.trail.db;

import java.util.UUID;

import org.springframework.data.relational.core.mapping.Table;
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
	
}
