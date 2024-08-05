package org.trailence.preferences.db;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Table("user_preferences")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserPreferencesEntity {

	@Id
	private String email;
	
	private String lang;
	private Short elevationUnit;
	private Short distanceUnit;
	private Short hourFormat;
	private Short dateFormat;
	private Short theme;
	
	private Integer traceMinMeters;
	private Long traceMinMillis;
	
	private Integer offlineMapMaxKeepDays;
	private Short offlineMapMaxZoom;
	
	private Long estimatedBaseSpeed;
	private Long longBreakMinimumDuration;
	private Long longBreakMaximumDistance;
	
}
