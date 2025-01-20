package org.trailence.auth.db;

import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.data.relational.core.sql.Column;

import io.r2dbc.postgresql.codec.Json;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Table("user_keys")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserKeyEntity {

	@Id
	private UUID id;
	private String email;
	private byte[] publicKey;
	private long createdAt;
	private long lastUsage;
	private Long deletedAt;
	private long expiresAfter;
	
	private String random;
	private Long randomExpires;
	
	private Json deviceInfo;
	
	private int invalidAttempts;
	
	public static final org.springframework.data.relational.core.sql.Table TABLE = org.springframework.data.relational.core.sql.Table.create("user_keys");
	public static final Column COL_EMAIL = Column.create("email", TABLE);
	public static final Column COL_CREATED_AT = Column.create("created_at", TABLE);
	public static final Column COL_LAST_USAGE = Column.create("last_usage", TABLE);
	public static final Column COL_DELETED_AT = Column.create("deleted_at", TABLE);
	public static final Column COL_EXPIRES_AFTER = Column.create("expires_after", TABLE);

}
