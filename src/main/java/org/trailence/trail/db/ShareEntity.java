package org.trailence.trail.db;

import java.util.UUID;

import org.springframework.data.relational.core.mapping.Table;
import org.springframework.data.relational.core.sql.Column;
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
	
	public static final org.springframework.data.relational.core.sql.Table TABLE = org.springframework.data.relational.core.sql.Table.create("shares");
	public static final Column COL_UUID = Column.create("uuid", TABLE);
	public static final Column COL_FROM_EMAIL = Column.create("from_email", TABLE);
	public static final Column COL_TO_EMAIL = Column.create("to_email", TABLE);
	public static final Column COL_ELEMENT_TYPE = Column.create("element_type", TABLE);
	public static final Column COL_INCLUDE_PHOTOS = Column.create("include_photos", TABLE);
	
}
