package org.trailence.preferences;

import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.stereotype.Service;
import org.trailence.preferences.db.UserPreferencesEntity;
import org.trailence.preferences.db.UserPreferencesRepository;
import org.trailence.preferences.dto.UserPreferences;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@SuppressWarnings("java:S1301") // switch instead of if
public class UserPreferencesService {
	
	private final UserPreferencesRepository repo;
	private final R2dbcEntityTemplate r2dbc;
	
	public Mono<UserPreferences> getPreferences(String email) {
		return repo.findById(email)
			.map(this::toDto)
			.switchIfEmpty(Mono.just(new UserPreferences()));
	}
	
	public Mono<UserPreferences> setPreferences(UserPreferences dto, String email) {
		return repo.findById(email)
		.flatMap(entity -> {
			toEntity(dto, entity);
			return r2dbc.update(entity);
		})
		.switchIfEmpty(Mono.defer(() -> {
			UserPreferencesEntity entity = new UserPreferencesEntity();
			entity.setEmail(email);
			toEntity(dto, entity);
			return r2dbc.insert(entity);
		}))
		.map(this::toDto);
	}
	
	private void toEntity(UserPreferences dto, UserPreferencesEntity entity) {
		entity.setLang(dto.getLang());
		entity.setDistanceUnit(distanceUnitFromDto(dto.getDistanceUnit()));
		entity.setHourFormat(hourFormatFromDto(dto.getHourFormat()));
		entity.setDateFormat(dateFormatFromDto(dto.getDateFormat()));
		entity.setTheme(themeFromDto(dto.getTheme()));
		entity.setTraceMinMeters(dto.getTraceMinMeters());
		entity.setTraceMinMillis(dto.getTraceMinMillis());
		entity.setOfflineMapMaxKeepDays(dto.getOfflineMapMaxKeepDays());
		entity.setOfflineMapMaxZoom(dto.getOfflineMapMaxZoom());
		entity.setEstimatedBaseSpeed(dto.getEstimatedBaseSpeed());
		entity.setLongBreakMinimumDuration(dto.getLongBreakMinimumDuration());
		entity.setLongBreakMaximumDistance(dto.getLongBreakMaximumDistance());
		entity.setPhotoMaxPixels(dto.getPhotoMaxPixels());
		entity.setPhotoMaxQuality(dto.getPhotoMaxQuality());
		entity.setPhotoMaxSizeKB(dto.getPhotoMaxSizeKB());
		entity.setPhotoCacheDays(dto.getPhotoCacheDays());
	}
	
	private UserPreferences toDto(UserPreferencesEntity entity) {
		return new UserPreferences(
			entity.getLang(),
			distanceUnitToDto(entity.getDistanceUnit()),
			hourFormatToDto(entity.getHourFormat()),
			dateFormatToDto(entity.getDateFormat()),
			themeToDto(entity.getTheme()),
			entity.getTraceMinMeters(),
			entity.getTraceMinMillis(),
			entity.getOfflineMapMaxKeepDays(),
			entity.getOfflineMapMaxZoom(),
			entity.getEstimatedBaseSpeed(),
			entity.getLongBreakMinimumDuration(),
			entity.getLongBreakMaximumDistance(),
			entity.getPhotoMaxPixels(),
			entity.getPhotoMaxQuality(),
			entity.getPhotoMaxSizeKB(),
			entity.getPhotoCacheDays()
		);
	}

	private String distanceUnitToDto(Short value) {
		if (value == null) return null;
		switch (value) {
		case 1: return "IMPERIAL";
		default: return "METERS";
		}
	}
	
	private Short distanceUnitFromDto(String value) {
		if (value == null) return null;
		switch (value) {
		case "IMPERIAL": return 1;
		default: return 0;
		}
	}
	
	private String hourFormatToDto(Short value) {
		if (value == null) return null;
		switch (value) {
		case 1: return "H12";
		default: return "H24";
		}
	}
	
	private Short hourFormatFromDto(String value) {
		if (value == null) return null;
		switch (value) {
		case "H12": return 1;
		default: return 0;
		}
	}

	private String dateFormatToDto(Short value) {
		if (value == null) return null;
		switch (value) {
		case 1: return "m/d/yyyy";
		default: return "dd/mm/yyyy";
		}
	}
	
	private Short dateFormatFromDto(String value) {
		if (value == null) return null;
		switch (value) {
		case "m/d/yyyy": return 1;
		default: return 0;
		}
	}
	
	private String themeToDto(Short value) {
		if (value == null) return null;
		switch (value) {
		case 1: return "DARK";
		case 2: return "LIGHT";
		default: return "SYSTEM";
		}
	}
	
	private Short themeFromDto(String value) {
		if (value == null) return null;
		switch (value) {
		case "DARK": return 1;
		case "LIGHT": return 2;
		default: return 0;
		}
	}
	
}
