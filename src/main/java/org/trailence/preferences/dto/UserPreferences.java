package org.trailence.preferences.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserPreferences {

	private String lang;
	private String elevationUnit;
	private String distanceUnit;
	private String hourFormat;
	private String dateFormat;
	private String theme;
	
	private Integer traceMinMeters;
	private Long traceMinMillis;
	
	private Integer offlineMapMaxKeepDays;
	private Short offlineMapMaxZoom;

}
