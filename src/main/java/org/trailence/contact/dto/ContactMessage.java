package org.trailence.contact.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ContactMessage {

	private String uuid;
	private String email;
	private String type;
	private String message;
	private long sentAt;
	private boolean read;
	
}
