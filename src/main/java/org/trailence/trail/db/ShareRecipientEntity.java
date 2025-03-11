package org.trailence.trail.db;

import java.util.UUID;

import org.springframework.data.relational.core.mapping.Table;
import org.springframework.data.relational.core.sql.Column;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Table("share_recipients")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShareRecipientEntity {

	private UUID uuid;
	private String owner;
	private String recipient;
	
	public static final org.springframework.data.relational.core.sql.Table TABLE = org.springframework.data.relational.core.sql.Table.create("share_recipients");
	public static final Column COL_UUID = Column.create("uuid", TABLE);
	public static final Column COL_OWNER = Column.create("owner", TABLE);
	public static final Column COL_RECIPIENT = Column.create("recipient", TABLE);
	
}
