package org.trailence.global.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateResponse<T> {

	private List<UuidAndOwner> deleted;
	private List<T> updated;
	private List<T> created;
	
}
