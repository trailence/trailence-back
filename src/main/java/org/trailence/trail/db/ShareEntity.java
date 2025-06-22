package org.trailence.trail.db;

import org.springframework.data.relational.core.mapping.Table;
import org.springframework.data.relational.core.sql.Column;
import org.trailence.global.db.AbstractEntityUuidOwner;
import org.trailence.trail.dto.ShareElementType;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Table("shares")
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class ShareEntity extends AbstractEntityUuidOwner {

	private String name;
	private ShareElementType elementType;
	private boolean includePhotos;

	public static final org.springframework.data.relational.core.sql.Table TABLE = org.springframework.data.relational.core.sql.Table.create("shares");
	public static final Column COL_UUID = Column.create("uuid", TABLE);
	public static final Column COL_OWNER = Column.create("owner", TABLE);
	public static final Column COL_ELEMENT_TYPE = Column.create("element_type", TABLE);
	public static final Column COL_INCLUDE_PHOTOS = Column.create("include_photos", TABLE);
	public static final Column COL_NAME = Column.create("name", TABLE);
	
}
