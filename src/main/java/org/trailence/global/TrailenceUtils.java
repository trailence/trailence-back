package org.trailence.global;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.security.core.Authentication;
import org.trailence.global.exceptions.BadRequestException;

import io.r2dbc.postgresql.codec.Json;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Slf4j
public class TrailenceUtils {
	
	public static final ObjectMapper mapper = new ObjectMapper();
	
	public static final long STARTUP_TIME = System.currentTimeMillis();
	
	public static final String AUTHORITY_COMPLETE_USER = "complete";
	public static final String AUTHORITY_ADMIN_USER = "admin";
	public static final String ROLE_MODERATOR = "moderator";
	public static final String AUTHORITY_MODERATOR = "ROLE_MODERATOR";

	private static final String HAS_AUTORITHY = "hasAuthority('";
	public static final String PREAUTHORIZE_COMPLETE = HAS_AUTORITHY + AUTHORITY_COMPLETE_USER + "')";
	public static final String PREAUTHORIZE_ADMIN = HAS_AUTORITHY + AUTHORITY_ADMIN_USER + "')";
	public static final String PREAUTHORIZE_MODERATOR = HAS_AUTORITHY + AUTHORITY_MODERATOR + "')";
	
	public static final int MIN_PASSWORD_SIZE = 6;
	
	public static final String FREE_PLAN = "free";

	public static String hashPassword(String password) {
		return DigestUtils.sha256Hex(password);
	}
	
	public static <T> List<T> merge(List<T> list1, List<T> list2) {
		List<T> result = new ArrayList<>(list1.size() + list2.size());
		result.addAll(list1);
		result.addAll(list2);
		return result;
	}

	public static <T> List<T> merge(List<T> list1, List<T> list2, List<T> list3) {
		List<T> result = new ArrayList<>(list1.size() + list2.size() + list3.size());
		result.addAll(list1);
		result.addAll(list2);
		result.addAll(list3);
		return result;
	}
	
	public static Mono<String> readResource(String filename) {
		return Mono.fromCallable(() -> {
			try (InputStream in = TrailenceUtils.class.getClassLoader().getResourceAsStream(filename)) {
				return new String(in.readAllBytes(), StandardCharsets.UTF_8);
			}
		}).subscribeOn(Schedulers.boundedElastic()).publishOn(Schedulers.parallel());
	}
	
	@SuppressWarnings("java:S2259")
	public static String email(Authentication auth) {
		return auth.getPrincipal().toString();
	}
	
	public static boolean hasRole(Authentication auth, String role) {
		return auth != null && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_" + role.toUpperCase()) || a.getAuthority().equals(AUTHORITY_ADMIN_USER));
	}
	
	public static boolean isAdmin(Authentication auth) {
		return auth != null && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals(AUTHORITY_ADMIN_USER));
	}
	
	public static Optional<UUID> ifUuid(String s) {
		try {
			return Optional.of(UUID.fromString(s));
		} catch (IllegalArgumentException _) {
			return Optional.empty();
		}
	}

	@SuppressWarnings({"java:S5998", "java:S6353", "java:S5843"})
	private static final String EMAIL_REGEXP = "^(([^<>()\\[\\]\\\\.,;:\\s@\"]+(\\.[^<>()\\[\\]\\\\.,;:\\s@\"]+)*)|(\".+\"))@((\\[[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}])|(([a-zA-Z\\-0-9]+\\.)+[a-zA-Z\\-0-9]{2,}))$";
	
	public static String normalizeEmail(String email) {
		email = email.toLowerCase();
		if (!email.matches(EMAIL_REGEXP)) throw new BadRequestException("Invalid email: " + email);
		return email;
	}
	
	public static Json toJsonOrNull(Object value) {
		try {
			return Json.of(mapper.writeValueAsBytes(value));
		} catch (Exception e) {
			log.error("Mapping error", e);
			return null;
		}
	}
	
	public static <T> T fromJsonOr(byte[] value, TypeReference<T> type, T defaultValue) {
		try {
			return TrailenceUtils.mapper.readValue(value, type);
		} catch (Exception e) {
			log.error("Mapping error", e);
			return defaultValue;
		}
	}
	
}
