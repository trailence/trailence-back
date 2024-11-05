package org.trailence.preferences;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.trailence.preferences.dto.UserPreferences;
import org.trailence.test.AbstractTest;

class TestPreferences extends AbstractTest {

	@Test
	void testGetAndSet() {
		var user = test.createUserAndLogin();
		
		var response = user.get("/api/preferences/v1");
		assertThat(response.statusCode()).isEqualTo(200);
		var prefs = response.getBody().as(UserPreferences.class);
		
		assertThat(prefs.getDateFormat()).isNull();
		assertThat(prefs.getDistanceUnit()).isNull();
		assertThat(prefs.getEstimatedBaseSpeed()).isNull();
		assertThat(prefs.getHourFormat()).isNull();
		assertThat(prefs.getLang()).isNull();
		assertThat(prefs.getLongBreakMaximumDistance()).isNull();
		assertThat(prefs.getLongBreakMinimumDuration()).isNull();
		assertThat(prefs.getOfflineMapMaxKeepDays()).isNull();
		assertThat(prefs.getOfflineMapMaxZoom()).isNull();
		assertThat(prefs.getPhotoCacheDays()).isNull();
		assertThat(prefs.getPhotoMaxPixels()).isNull();
		assertThat(prefs.getPhotoMaxQuality()).isNull();
		assertThat(prefs.getPhotoMaxSizeKB()).isNull();
		assertThat(prefs.getTheme()).isNull();
		assertThat(prefs.getTraceMinMeters()).isNull();
		assertThat(prefs.getTraceMinMillis()).isNull();
		
		prefs = new UserPreferences(
			"fr",
			"IMPERIAL",
			"H24",
			"dd/mm/yyyy",
			"DARK",
			1, 5000L,
			30, (short) 17,
			1000L, 1001L, 1002L,
			500, (short) 90, 250, 20
		);
		
		response = user.put("/api/preferences/v1", prefs);
		assertThat(response.statusCode()).isEqualTo(200);
		var prefs2 = response.getBody().as(UserPreferences.class);
		assertThat(prefs2).isEqualTo(prefs);
		
		response = user.get("/api/preferences/v1");
		assertThat(response.statusCode()).isEqualTo(200);
		prefs2 = response.getBody().as(UserPreferences.class);
		assertThat(prefs2).isEqualTo(prefs);
	}
	
}
