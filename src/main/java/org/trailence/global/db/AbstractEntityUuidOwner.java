package org.trailence.global.db;

import java.util.UUID;

import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.InsertOnlyProperty;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class AbstractEntityUuidOwner {

	private UUID uuid;
    private String owner;
    @Version
    private long version;
    
    @InsertOnlyProperty
    private long createdAt;
    private long updatedAt;
	
}
