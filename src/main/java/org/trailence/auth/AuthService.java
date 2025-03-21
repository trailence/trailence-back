package org.trailence.auth;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.apache.commons.codec.binary.Base64;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.sql.Assignments;
import org.springframework.data.relational.core.sql.Conditions;
import org.springframework.data.relational.core.sql.Delete;
import org.springframework.data.relational.core.sql.Expressions;
import org.springframework.data.relational.core.sql.SQL;
import org.springframework.data.relational.core.sql.SimpleFunction;
import org.springframework.data.relational.core.sql.Update;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.trailence.auth.db.UserKeyEntity;
import org.trailence.auth.db.UserKeyRepository;
import org.trailence.auth.dto.AuthResponse;
import org.trailence.auth.dto.ForgotPasswordRequest;
import org.trailence.auth.dto.InitRenewRequest;
import org.trailence.auth.dto.InitRenewResponse;
import org.trailence.auth.dto.LoginRequest;
import org.trailence.auth.dto.LoginShareRequest;
import org.trailence.auth.dto.RenewTokenRequest;
import org.trailence.auth.dto.UserKey;
import org.trailence.captcha.CaptchaService;
import org.trailence.extensions.UserExtensionsService;
import org.trailence.global.TrailenceUtils;
import org.trailence.global.db.DbUtils;
import org.trailence.global.db.PlusExpression;
import org.trailence.global.db.SqlBuilder;
import org.trailence.global.exceptions.ForbiddenException;
import org.trailence.global.exceptions.ValidationUtils;
import org.trailence.global.rest.JwtAuthenticationManager;
import org.trailence.global.rest.TokenService;
import org.trailence.preferences.UserPreferencesService;
import org.trailence.preferences.dto.UserPreferences;
import org.trailence.quotas.QuotaService;
import org.trailence.quotas.dto.UserQuotas;
import org.trailence.user.UserService;
import org.trailence.user.db.UserEntity;
import org.trailence.user.db.UserRepository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;

import io.r2dbc.postgresql.codec.Json;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

	private final UserRepository userRepo;
	private final UserKeyRepository keyRepo;
	private final JwtAuthenticationManager auth;
	private final R2dbcEntityTemplate r2dbc;
	private final SecureRandom random;
	private final UserPreferencesService userPreferencesService;
	private final TokenService tokenService;
	private final UserService userService;
	private final CaptchaService captchaService;
	private final QuotaService quotaService;
	private final UserExtensionsService extensionsService;
	
	private static final String ERROR_CODE_INVALID_CREDENTIALS = "invalid-credentials";
	private static final String ERROR_CODE_LOCKED = "locked";
	private static final String ERROR_CODE_CAPTCHA_NEEDED = "captcha-needed";
	
	private static final int MAX_ATTEMPTS_BEFORE_CAPTCHA = 2;
	private static final int MAX_ATTEMPTS_BEFORE_LOCK = 10;
	private static final int MAX_ATTEMPTS_BEFORE_REMOVING_KEY = 3;
	private static final long DEFAULT_KEY_EXPIRES_AFTER = 16070400000L; // 6 * 31 days
	
	public Mono<AuthResponse> login(LoginRequest request) {
		ValidationUtils.field("publicKey", request.getPublicKey()).valid(key -> KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(key)));
		return userRepo.findByEmail(request.getEmail().toLowerCase())
			.switchIfEmpty(Mono.error(new ForbiddenException(ERROR_CODE_INVALID_CREDENTIALS)))
			.flatMap(user -> checkInvalidAttempts(user, request.getCaptchaToken()))
			.flatMap(user -> checkPassword(user, request.getPassword()))
			// ok, authenticate
			.flatMap(user -> {
				var roles = getRoles(user);
				var token = auth.generateToken(user.getEmail(), true, user.isAdmin(), roles);
				
				UserKeyEntity key = createKey(user.getEmail(), request.getPublicKey(), request.getExpiresAfter(), request.getDeviceInfo());
				
				var response = response(token, user, key, null, null, roles);
				user.setInvalidAttempts(0);
				
				return userRepo.save(user)
				.then(r2dbc.insert(key))
				.thenReturn(response)
				.flatMap(this::withPreferences).flatMap(this::withQuotas);
			});
	}
	
	private List<String> getRoles(UserEntity user) {
		return userService.toRolesList(user.getRoles());
	}
	
	private UserKeyEntity createKey(String email, byte[] publicKey, Long expiresAfter, Map<String, Object> deviceInfo) {
		UserKeyEntity key = new UserKeyEntity();
		key.setId(UUID.randomUUID());
		key.setEmail(email);
		key.setPublicKey(publicKey);
		key.setCreatedAt(System.currentTimeMillis());
		key.setLastUsage(key.getCreatedAt());
		key.setExpiresAfter(getExpiresAfter(expiresAfter));
		key.setInvalidAttempts(0);
		toDeviceInfo(deviceInfo, key);
		return key;
	}
	
	private long getExpiresAfter(Long received) {
		if (received == null) return DEFAULT_KEY_EXPIRES_AFTER;
		long v = received.longValue();
		if (v < 1L) return DEFAULT_KEY_EXPIRES_AFTER;
		return v;
	}
	
	private Mono<UserEntity> checkInvalidAttempts(UserEntity user, String captchaToken) {
		if (user.getInvalidAttempts() >= MAX_ATTEMPTS_BEFORE_CAPTCHA && captchaService.isActivated() && captchaToken == null)
			return Mono.error(new ForbiddenException(ERROR_CODE_CAPTCHA_NEEDED));
		if (captchaToken != null)
			return captchaService.validate(captchaToken)
				.flatMap(ok -> {
					if (!ok.booleanValue()) return Mono.error(new ForbiddenException(ERROR_CODE_CAPTCHA_NEEDED));
					return Mono.just(user);
				});
		return Mono.just(user);
	}
	
	private Mono<UserEntity> checkPassword(UserEntity user, String password) {
		if (user.getPassword() == null)
			return Mono.error(new ForbiddenException(user.getInvalidAttempts() >= MAX_ATTEMPTS_BEFORE_LOCK ? ERROR_CODE_LOCKED : ERROR_CODE_INVALID_CREDENTIALS));
		if (!TrailenceUtils.hashPassword(password).equals(user.getPassword())) {
			user.setInvalidAttempts(user.getInvalidAttempts() + 1);
			if (user.getInvalidAttempts() >= MAX_ATTEMPTS_BEFORE_LOCK)
				user.setPassword(null);
			return userRepo.save(user).then(Mono.error(new ForbiddenException(user.getPassword() == null ? ERROR_CODE_LOCKED : ERROR_CODE_INVALID_CREDENTIALS)));
		}
		return Mono.just(user);
	}
	
	public Mono<AuthResponse> loginShare(LoginShareRequest request) {
		return Mono.fromCallable(() -> tokenService.check(request.getToken()))
		.flatMap(tokenData ->
			userRepo.findById(tokenData.getEmail())
			.switchIfEmpty(Mono.defer(() ->
				// first time the user use a share link -> create his account without password
				userService.createUser(tokenData.getEmail().toLowerCase(), null, false, List.of(Tuples.of(TrailenceUtils.FREE_PLAN, Optional.empty())))
				.then(userRepo.findById(tokenData.getEmail()))
			))
			.flatMap(user -> {
				if (user.getPassword() != null) return Mono.error(new ForbiddenException());
				
				var token = auth.generateToken(user.getEmail(), false, false, List.of());
				UserKeyEntity key =  createKey(user.getEmail(), request.getPublicKey(), request.getExpiresAfter(), request.getDeviceInfo());

				var response = response(token, user, key, null, null, List.of());
				return r2dbc.insert(key).thenReturn(response).flatMap(this::withPreferences).flatMap(this::withQuotas);
			})
		);
	}
	
	public Mono<InitRenewResponse> initRenew(InitRenewRequest request) {
		return keyRepo.findByIdAndEmail(UUID.fromString(request.getKeyId()), request.getEmail().toLowerCase())
		.switchIfEmpty(Mono.error(new ForbiddenException()))
		.flatMap(key -> {
			if (key.getDeletedAt() != null) return Mono.error(new ForbiddenException());
			byte[] bytes = new byte[33];
			random.nextBytes(bytes);
			String randomBase64 = Base64.encodeBase64String(bytes);
			key.setRandom(randomBase64);
			key.setRandomExpires(Instant.now().plus(Duration.ofMinutes(1)).toEpochMilli());
			return keyRepo.save(key)
			.thenReturn(new InitRenewResponse(randomBase64));
		});
	}
	
	public Mono<AuthResponse> renew(RenewTokenRequest request) {
		return keyRepo.findByIdAndEmail(UUID.fromString(request.getKeyId()), request.getEmail().toLowerCase())
		.switchIfEmpty(Mono.error(new ForbiddenException()))
		.flatMap(key -> {
			if (key.getDeletedAt() != null || key.getRandom() == null || key.getRandomExpires() < System.currentTimeMillis()) return Mono.error(new ForbiddenException());
			boolean isValid = key.getRandom().equals(request.getRandom());
			if (isValid && !isValidSignature(key.getPublicKey(), request.getEmail(), key.getRandom(), request.getSignature())) isValid = false;
			if (!isValid) {
				key.setInvalidAttempts(key.getInvalidAttempts() + 1);
				if (key.getInvalidAttempts() > MAX_ATTEMPTS_BEFORE_REMOVING_KEY) {
					key.setDeletedAt(System.currentTimeMillis());
				}
				return keyRepo.save(key).then(Mono.error(new ForbiddenException()));
			}
			return userRepo.findById(key.getEmail())
			.switchIfEmpty(Mono.error(new ForbiddenException()))
			.flatMap(user -> {
				key.setLastUsage(System.currentTimeMillis());
				toDeviceInfo(request.getDeviceInfo(), key);
				key.setRandom(null);
				key.setRandomExpires(0L);
				key.setInvalidAttempts(0);
				if (request.getNewPublicKey() == null) {
					var roles = getRoles(user);
					var token = auth.generateToken(key.getEmail(), user.getPassword() != null, user.isAdmin(), roles);
					var response = response(token, user, key, null, null, roles);
					return keyRepo.save(key).thenReturn(response).flatMap(this::withPreferences).flatMap(this::withQuotas);
				}
				// new key
				UserKeyEntity newKey = createKey(user.getEmail(), request.getNewPublicKey(), request.getNewKeyExpiresAfter(), request.getDeviceInfo());
				var roles = getRoles(user);
				var token = auth.generateToken(user.getEmail(), user.getPassword() != null, user.isAdmin(), roles);
				var response = response(token, user, newKey, null, null, roles);
				key.setDeletedAt(System.currentTimeMillis());
				return keyRepo.save(key)
					.then(r2dbc.insert(newKey))
					.thenReturn(response)
					.flatMap(this::withPreferences)
					.flatMap(this::withQuotas);
			});
		});
	}
	
	private boolean isValidSignature(byte[] publicKeyBytes, String email, String random, byte[] signature) {
		try {
			PublicKey publicKey = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(publicKeyBytes));
			Signature signer = Signature.getInstance("SHA256withRSA");
			signer.initVerify(publicKey);
			signer.update((email + random).getBytes(StandardCharsets.UTF_8));
			return signer.verify(signature);
		} catch (Exception e) {
			return false;
		}
	}
	
	private AuthResponse response(Tuple2<String, Instant> token, UserEntity user, UserKeyEntity key, UserPreferences preferences, UserQuotas quotas, List<String> roles) {
		return new AuthResponse(
			token.getT1(),
			token.getT2().toEpochMilli(),
			user.getEmail(),
			key.getId().toString(),
			key.getCreatedAt(),
			key.getCreatedAt() + key.getExpiresAfter(),
			preferences,
			user.getPassword() != null,
			user.isAdmin(),
			quotas,
			extensionsService.getAllowedExtensions(user.isAdmin(), roles)
		);
	}
	
	private void toDeviceInfo(Map<String, Object> map, UserKeyEntity entity) {
		try {
			entity.setDeviceInfo(Json.of(TrailenceUtils.mapper.writeValueAsString(map)));
		} catch (JsonProcessingException e) {
			log.error("Invalid device info", e);
		}
	}
	
	private Mono<AuthResponse> withPreferences(AuthResponse response) {
		return userPreferencesService.getPreferences(response.getEmail())
		.map(pref -> {
			response.setPreferences(pref);
			return response;
		});
	}
	
	private Mono<AuthResponse> withQuotas(AuthResponse response) {
		return quotaService.getUserQuotas(response.getEmail())
		.map(quotas -> {
			response.setQuotas(quotas);
			return response;
		});
	}
	
	public Flux<UserKey> getMyKeys(Authentication auth) {
		return internalGetUserKeys(auth.getPrincipal().toString(), false);
	}
	
	@PreAuthorize(TrailenceUtils.PREAUTHORIZE_ADMIN)
	public Flux<UserKey> getUserKeys(String userEmail) {
		return internalGetUserKeys(userEmail.toLowerCase(), true);
	}
	
	private Flux<UserKey> internalGetUserKeys(String email, boolean includeDeleted) {
		return keyRepo.findByEmail(email)
		.filter(entity -> includeDeleted || entity.getDeletedAt() == null)
		.map(entity -> {
			Map<String, Object> info;
			try {
				info = TrailenceUtils.mapper.readValue(entity.getDeviceInfo().asString(), new TypeReference<Map<String, Object>>() {});
			} catch (Exception e) {
				log.error("Error decoding device info", e);
				info = new HashMap<>();
			}
			return new UserKey(entity.getId().toString(), entity.getCreatedAt(), entity.getLastUsage(), info, entity.getDeletedAt());
		});
	}
	
	public Mono<Void> deleteMyKey(String id, Authentication auth) {
		return keyRepo.findByIdAndEmail(UUID.fromString(id), auth.getPrincipal().toString())
		.flatMap(entity -> {
			if (entity.getDeletedAt() != null) return Mono.empty();
			entity.setDeletedAt(System.currentTimeMillis());
			return keyRepo.save(entity).then();
		});
	}
	
	public Mono<CaptchaService.PublicConfig> getCaptchaConfig() {
		return Mono.just(captchaService.getConfig());
	}
	
	public Mono<Void> forgotPassword(ForgotPasswordRequest request) {
		if (captchaService.isActivated() && request.getCaptchaToken() == null) return Mono.error(new ForbiddenException());
		if (request.getEmail() == null) return Mono.error(new ForbiddenException());
		String email = request.getEmail().toLowerCase();
		String lang = request.getLang();
		return userRepo.findByEmail(email)
		.flatMap(user -> 
			(captchaService.isActivated() ? captchaService.validate(request.getCaptchaToken()) : Mono.just(true))
			.flatMap(ok -> {
				if (!ok.booleanValue()) return Mono.error(new ForbiddenException());
				return Mono.just(user);
			})
		)
		.flatMap(user -> userService.sendChangePasswordCode(email, lang, true))
		.then();
	}
	
	@Scheduled(initialDelayString = "10m", fixedDelayString = "1d")
	public Mono<Void> handleExpiredKeys() {
		return Mono.defer(() -> {
			log.info("Deleting expired keys");
			var now = SQL.literalOf(System.currentTimeMillis());
			var update = Update.builder().table(UserKeyEntity.TABLE)
				.set(Assignments.value(UserKeyEntity.COL_DELETED_AT, now))
				.where(
					Conditions.isNull(UserKeyEntity.COL_DELETED_AT)
					.and(
						Conditions.isLess(
							new PlusExpression(UserKeyEntity.COL_CREATED_AT, UserKeyEntity.COL_EXPIRES_AFTER),
							now
						)
					)
				)
				.build();
			var operation = DbUtils.update(update, null, r2dbc);
			return r2dbc.getDatabaseClient().sql(operation).fetch().rowsUpdated()
			.flatMap(nb -> {
				log.info("Deleted expired keys: {}", nb);
				return Mono.empty();
			});
		});
	}
	
	@Scheduled(initialDelayString = "12m", fixedDelayString = "1d")
	public Mono<Void> cleanKeys() {
		return Mono.defer(() -> {
			log.info("Cleaning deleted keys since more than 15 months");
			var delete = Delete.builder()
			.from(UserKeyEntity.TABLE)
			.where(
				Conditions.isLess(UserKeyEntity.COL_DELETED_AT, SQL.literalOf(System.currentTimeMillis() - 15L * 31 * 24 * 60 * 60 * 1000))
				.and(Conditions.not(Conditions.isNull(UserKeyEntity.COL_DELETED_AT)))
			).build();
			return r2dbc.getDatabaseClient().sql(DbUtils.delete(delete, null, r2dbc)).fetch().rowsUpdated()
			.map(nb -> {
				log.info("Deleted keys: {}", nb);
				return nb;
			});
		}).then(Mono.defer(() -> {
			// definitely remove keys having a more recent key for the same user and same deviceId, except keys without a deviceId
			log.info("Cleaning deleted keys having a more recent key for the same user and device id");
			var select = DbUtils.operation(
				new SqlBuilder()
				.select(
					UserKeyEntity.COL_EMAIL,
					Expressions.just("device_info ->> 'deviceId' as device_id"),
					SimpleFunction.create("MAX", List.of(UserKeyEntity.COL_CREATED_AT))
				)
				.from(UserKeyEntity.TABLE)
				.where(
					Conditions.not(Conditions.isNull(Expressions.just("device_info ->> 'deviceId'")))
				)
				.groupBy(UserKeyEntity.COL_EMAIL, Expressions.just("device_id"))
				.build(),
				null
			);
			return r2dbc.getDatabaseClient().sql(select)
			.map((row, meta) -> Tuples.of(row.get(0, String.class), row.get(1, String.class), row.get(2, Long.class)))
			.all()
			.concatMap(row -> {
				var delete = Delete.builder()
				.from(UserKeyEntity.TABLE)
				.where(
					Conditions.isEqual(UserKeyEntity.COL_EMAIL, SQL.literalOf(row.getT1()))
					.and(Conditions.isEqual(Expressions.just("device_info ->> 'deviceId'"), SQL.literalOf(row.getT2())))
					.and(Conditions.isLess(UserKeyEntity.COL_CREATED_AT, SQL.literalOf(row.getT3())))
					.and(Conditions.not(Conditions.isNull(UserKeyEntity.COL_DELETED_AT)))
				).build();
				return r2dbc.getDatabaseClient().sql(DbUtils.delete(delete, null, r2dbc)).fetch().rowsUpdated()
					.map(nb -> {
						if (nb > 0) log.info("Deleted keys for user {} device id {}: {}", row.getT1(), row.getT2(), nb);
						return Tuples.of(1L, nb);
					});
			})
			.reduce(Tuples.of(0L, 0L), (p, n) -> Tuples.of(p.getT1() + n.getT1(), p.getT2() + n.getT2()))
			.flatMap(tuple -> {
				log.info("Cleaning deleted keys done: {} users/devices processed, {} keys removed", tuple.getT1(), tuple.getT2());
				return Mono.empty();
			});
		}));
	}
	
}
