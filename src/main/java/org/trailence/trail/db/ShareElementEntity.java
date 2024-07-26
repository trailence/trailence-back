package org.trailence.trail.db;

import java.util.UUID;

import org.springframework.data.relational.core.mapping.Table;

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
	
}
