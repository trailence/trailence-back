package org.trailence.verificationcode.db;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import io.r2dbc.postgresql.codec.Json;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Table("verification_codes")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class VerificationCodeEntity {

	@Id
	private String code;
	private String type;
	private String key;

	private long expiresAt;
	private int invalidAttempts;
	
	private Json data;
	
}
