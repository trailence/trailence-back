package org.trailence.global.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UuidAndOwner {

	private String uuid;
	private String owner;

}
