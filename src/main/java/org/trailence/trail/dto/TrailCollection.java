package org.trailence.trail.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TrailCollection {

    private String uuid;
    private String owner;
    private long version;
    
    private long createdAt;
    private long updatedAt;

    private String name;
    private TrailCollectionType type;

}
