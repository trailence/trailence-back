package org.trailence.init;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@ConfigurationProperties(prefix = "trailence.free-plan")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FreePlanProperties {

	private long collections;
	private long trails;
	private long tracks;
	private long tracksSize;
	private long photos;
	private long photosSize;
	private long tags;
	private long trailTags;
	private long shares;
	
}
