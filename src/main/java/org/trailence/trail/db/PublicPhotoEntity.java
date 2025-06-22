package org.trailence.trail.db;

import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Table("public_photos")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PublicPhotoEntity {

	@Id
	private UUID uuid;
	private UUID trailUuid;
    private long fileId;
    
    private String description;
    private Long date;
    private Long latitude;
    private Long longitude;
    private int index;
	
}
