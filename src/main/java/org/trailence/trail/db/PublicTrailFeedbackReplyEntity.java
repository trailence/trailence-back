package org.trailence.trail.db;

import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Table("public_trail_feedback_reply")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PublicTrailFeedbackReplyEntity {

	@Id
	private UUID uuid;
	private UUID replyTo;
	private String email;
	private long date;
	private String comment;
	
}
