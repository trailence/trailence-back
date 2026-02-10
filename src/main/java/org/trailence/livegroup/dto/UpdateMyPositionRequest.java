package org.trailence.livegroup.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateMyPositionRequest {

	private String memberId;
	private LiveGroup.Position position;
	private Long positionAt;
	
}
