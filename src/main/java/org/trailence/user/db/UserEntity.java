package org.trailence.user.db;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

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
	
}
