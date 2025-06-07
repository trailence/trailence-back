package org.trailence.trail.dto;

import org.trailence.global.dto.Versioned;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Trail implements Versioned.Interface {

    private String uuid;
    private String owner;
    private long version;
    
    private long createdAt;
    private long updatedAt;

    private String name;
    private String description;
    private String location;
    private String loopType;
    private String activity;

    private String originalTrackUuid;
    private String currentTrackUuid;

    private String collectionUuid;

}
