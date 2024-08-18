package org.trailence.auth.db;

import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

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
	
	private String random;
	private Long randomExpires;
	
	private Json deviceInfo;
	
	private int invalidAttempts;
	
}
