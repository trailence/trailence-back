package org.trailence.user.db;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.data.relational.core.sql.Column;

import io.r2dbc.postgresql.codec.Json;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Table("users")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserEntity {

	@Id
	private String email;
	private String password;
	private long createdAt;
	private int invalidAttempts;
	private boolean isAdmin;
	private Json roles;
	
	public static final org.springframework.data.relational.core.sql.Table TABLE = org.springframework.data.relational.core.sql.Table.create("users");
	public static final Column COL_EMAIL = Column.create("email", TABLE);
	public static final Column COL_PASSWORD = Column.create("password", TABLE);
	public static final Column COL_CREATED_AT = Column.create("created_at", TABLE);
	public static final Column COL_INVALID_ATTEMPTS = Column.create("invalid_attempts", TABLE);
	public static final Column COL_IS_ADMIN = Column.create("is_admin", TABLE);
	public static final Column COL_ROLES = Column.create("roles", TABLE);
}
