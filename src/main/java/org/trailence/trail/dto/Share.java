package org.trailence.trail.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Share {

	private String uuid;
	private String owner;
	private long version;
	
	private long createdAt;
    private long updatedAt;

	private List<String> recipients;
    
	private ShareElementType type;
	private String name;
	private boolean includePhotos;

	private List<String> elements;
	private List<String> trails;
	
}
