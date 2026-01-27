package org.trailence.preferences.db;

import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Table;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Table("user_avatar")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserAvatarEntity {

	@Id
	private String email;
    @Version
    private long version;
	
    private UUID publicUuid;
	private Long currentFileId;
	private boolean currentPublic;
	private Long newFileId;
	private boolean newPublic;
	private Long newFileSubmittedAt;

}
