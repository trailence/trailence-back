package org.trailence.contact.db;

import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.data.relational.core.sql.Column;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Table("contact_messages")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ContactMessageEntity {

	@Id
	private UUID uuid;
	
	private String email;
	private String messageType;
	private String messageText;
	private long sentAt;
	private boolean isRead;
	
	public static final org.springframework.data.relational.core.sql.Table TABLE = org.springframework.data.relational.core.sql.Table.create("contact_messages");
	public static final Column COL_UUID = Column.create("uuid", TABLE);
	public static final Column COL_EMAIL = Column.create("email", TABLE);
	public static final Column COL_MESSAGE_TYPE = Column.create("message_type", TABLE);
	public static final Column COL_MESSAGE_TEXT = Column.create("message_text", TABLE);
	public static final Column COL_SENT_AT = Column.create("sent_at", TABLE);
	public static final Column COL_IS_READ = Column.create("is_read", TABLE);
	
}
