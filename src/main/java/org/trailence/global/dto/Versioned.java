package org.trailence.global.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Versioned {

	private String uuid;
	private String owner;
	private long version;
	
}
