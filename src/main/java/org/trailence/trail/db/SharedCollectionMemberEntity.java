package org.trailence.trail.db;

import java.util.UUID;

import org.springframework.data.relational.core.mapping.Table;
import org.springframework.data.relational.core.sql.Column;
import org.trailence.global.db.AbstractEntityUuidOwner;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Table("shared_collection_members")
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class SharedCollectionMemberEntity extends AbstractEntityUuidOwner {

	private UUID sharedCollectionUuid;
	private String name;
	
	public static final org.springframework.data.relational.core.sql.Table TABLE = org.springframework.data.relational.core.sql.Table.create("shared_collection_members");
	public static final Column COL_SHARED_COLLECTION_UUID = Column.create("shared_collection_uuid", TABLE);
	public static final Column COL_OWNER = Column.create("owner", TABLE);
	public static final Column COL_UUID = Column.create("uuid", TABLE);
}
