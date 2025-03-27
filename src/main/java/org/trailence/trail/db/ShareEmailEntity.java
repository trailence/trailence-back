package org.trailence.trail.db;

import java.util.UUID;

import org.springframework.data.relational.core.mapping.Table;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Table("share_emails")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShareEmailEntity {

	private UUID shareUuid;
	private String fromEmail;
	private String toEmail;
	private long sentAt;
	
}
