package org.trailence.trail.db;

import java.util.UUID;

import org.springframework.data.relational.core.mapping.Table;
import org.springframework.data.relational.core.sql.Column;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Table("share_elements")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShareElementEntity {

	private UUID shareUuid;
	private UUID elementUuid;
	private String owner;
	
	public static final org.springframework.data.relational.core.sql.Table TABLE = org.springframework.data.relational.core.sql.Table.create("share_elements");
	public static final Column COL_SHARE_UUID = Column.create("share_uuid", TABLE);
	public static final Column COL_ELEMENT_UUID = Column.create("element_uuid", TABLE);
	public static final Column COL_OWNER = Column.create("owner", TABLE);
	
}
