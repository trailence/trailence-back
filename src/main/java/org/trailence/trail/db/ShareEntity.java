package org.trailence.trail.db;

import java.util.UUID;

import org.springframework.data.relational.core.mapping.Table;
import org.trailence.trail.dto.ShareElementType;

import lombok.Data;
import lombok.NoArgsConstructor;

@Table("shares")
@Data
@NoArgsConstructor
public class ShareEntity {

	private UUID uuid;
	private String name;
	private String fromEmail;
	private String toEmail;
	private ShareElementType elementType;
	private long createdAt;
	private boolean includePhotos;
	
}
