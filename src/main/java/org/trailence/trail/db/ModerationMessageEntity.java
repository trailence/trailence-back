package org.trailence.trail.db;

import java.util.UUID;

import org.springframework.data.relational.core.mapping.Table;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Table("moderation_messages")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ModerationMessageEntity {

	private UUID uuid;
	private String owner;
	
	private String authorMessage;
	private String moderatorMessage;
	
}
