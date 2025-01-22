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
	
	public static interface Interface {
		
		public String getUuid();
		public void setUuid(String uuid);
		
		public String getOwner();
		public void setOwner(String owner);
		
		public long getVersion();
		public void setVersion(long version);
		
	}
	
}
