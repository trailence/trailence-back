package org.trailence.notifications.db;

import java.util.List;
import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Table("notifications")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NotificationEntity {

	@Id
	private UUID uuid;
	private String owner;
	private long date;
	private boolean isRead;
	private String text;
	private List<String> textElements;
	
}
