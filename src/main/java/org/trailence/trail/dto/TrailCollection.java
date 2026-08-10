package org.trailence.trail.dto;

import java.util.List;

import org.trailence.global.dto.Versioned;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TrailCollection implements Versioned.Interface {

    private String uuid;
    private String owner;
    private long version;
    
    private long createdAt;
    private long updatedAt;

    private String name;
    private TrailCollectionType type;
    
    // shared collections
    private List<String> sharedWith;
    private String sharedBy;

}
