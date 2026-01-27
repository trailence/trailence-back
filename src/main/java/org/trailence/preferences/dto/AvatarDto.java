package org.trailence.preferences.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AvatarDto {

	private long version;
	private boolean hasAvatar;
	private boolean isAvatarPublic;
	private boolean hasPending;
	private boolean isPendingPublic;
	
}
