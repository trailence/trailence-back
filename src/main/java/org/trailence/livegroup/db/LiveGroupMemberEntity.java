package org.trailence.livegroup.db;

import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Table("live_group_members")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LiveGroupMemberEntity {

	@Id
	private UUID uuid;
	private UUID groupUuid;
	private String memberId;
	private String memberName;
	private Long joinAt;
	private Long lastPositionLat;
	private Long lastPositionLon;
	private Long lastPositionAt;

}
