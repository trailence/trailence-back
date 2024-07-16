package org.trailence.trail.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Trail {

    private String uuid;
    private String owner;
    private long version;
    
    private long createdAt;
    private long updatedAt;

    private String name;
    private String description;
    private String location;

    private String originalTrackUuid;
    private String currentTrackUuid;

    private String collectionUuid;

}
