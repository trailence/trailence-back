package org.trailence.trail.db;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import io.r2dbc.postgresql.codec.Json;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Table("user_selection")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserSelectionEntity {

	@Id
	private String email;
	private Json selection;
	
}
