package org.trailence.storage.db;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Table("files")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FileEntity {

	@Id
	private Long id;
	
	private Long createdAt;
	private Long size;
	
	private String storageId;
	private boolean tmp;
	
}
