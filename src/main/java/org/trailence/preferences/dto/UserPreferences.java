package org.trailence.preferences.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserPreferences {

	private String lang;
	private String distanceUnit;
	private String hourFormat;
	private String dateFormat;
	private String theme;
	
	private Integer traceMinMeters;
	private Long traceMinMillis;
	
	private Integer offlineMapMaxKeepDays;
	private Short offlineMapMaxZoom;
	
	private Long estimatedBaseSpeed;
	private Long longBreakMinimumDuration;
	private Long longBreakMaximumDistance;
	
	private Integer photoMaxPixels;
	private Short photoMaxQuality;
	private Integer photoMaxSizeKB;
	private Integer photoCacheDays;

}
