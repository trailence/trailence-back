package org.trailence.notifications.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Notification {

	private String uuid;
	private long date;
	private boolean isRead;
	private String text;
	private List<String> textElements;
	
}
