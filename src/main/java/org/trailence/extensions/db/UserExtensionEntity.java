package org.trailence.extensions.db;

import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Table;

import io.r2dbc.postgresql.codec.Json;
import lombok.Data;
import lombok.NoArgsConstructor;

@Table("user_extensions")
@Data
@NoArgsConstructor
public class UserExtensionEntity {

	@Id
	private UUID id;
	@Version
	private long version;
	
	private String email;
	private String extension;
	private Json data;

}
