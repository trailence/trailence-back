package org.trailence.trail.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Share {

	private String id;
	private String name;
	private String from;
	private String to;
	private ShareElementType type;
	private long createdAt;
	private List<String> elements;
	private List<String> trails;
	
}
