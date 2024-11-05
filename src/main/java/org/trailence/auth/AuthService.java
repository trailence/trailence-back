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
import java.util.Map;
import java.util.UUID;

import org.apache.commons.codec.binary.Base64;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
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
import org.trailence.global.TrailenceUtils;
import org.trailence.global.exceptions.ForbiddenException;
import org.trailence.global.exceptions.ValidationUtils;
import org.trailence.global.rest.JwtAuthenticationManager;
import org.trailence.global.rest.TokenService;
import org.trailence.preferences.UserPreferencesService;
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
	
	private static final String ERROR_CODE_INVALID_CREDENTIALS = "invalid-credentials";
	private static final String ERROR_CODE_LOCKED = "locked";
	private static final String ERROR_CODE_CAPTCHA_NEEDED = "captcha-needed";
	
	private static final int MAX_ATTEMPTS_BEFORE_CAPTCHA = 2;
	private static final int MAX_ATTEMPTS_BEFORE_LOCK = 10;
	private static final int MAX_ATTEMPTS_BEFORE_REMOVING_KEY = 3;
	
	public Mono<AuthResponse> login(LoginRequest request) {
		ValidationUtils.field("publicKey", request.getPublicKey()).valid(key -> KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(key)));
		return userRepo.findByEmail(request.getEmail().toLowerCase())
			.switchIfEmpty(Mono.error(new ForbiddenException(ERROR_CODE_INVALID_CREDENTIALS)))
			.flatMap(user -> checkInvalidAttempts(user, request.getCaptchaToken()))
			.flatMap(user -> checkPassword(user, request.getPassword()))
			// ok, authenticate
			.flatMap(user -> {
				var token = auth.generateToken(user.getEmail(), true);
				
				UserKeyEntity key = new UserKeyEntity();
				key.setId(UUID.randomUUID());
				key.setEmail(user.getEmail());
				key.setPublicKey(request.getPublicKey());
				key.setCreatedAt(System.currentTimeMillis());
				key.setLastUsage(System.currentTimeMillis());
				toDeviceInfo(request.getDeviceInfo(), key);
				
				var response = new AuthResponse(token.getT1(), token.getT2().toEpochMilli(), user.getEmail(), key.getId().toString(), null, true);
				user.setInvalidAttempts(0);
				
				return userRepo.save(user)
				.then(r2dbc.insert(key))
				.thenReturn(response)
				.flatMap(this::withPreferences);
			});
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
				userService.createUser(tokenData.getEmail().toLowerCase(), null)
				.then(userRepo.findById(tokenData.getEmail()))
			))
			.flatMap(user -> {
				if (user.getPassword() != null) return Mono.error(new ForbiddenException());
				
				var token = auth.generateToken(user.getEmail(), false);
				
				UserKeyEntity key = new UserKeyEntity();
				key.setId(UUID.randomUUID());
				key.setEmail(user.getEmail());
				key.setPublicKey(request.getPublicKey());
				key.setCreatedAt(System.currentTimeMillis());
				key.setLastUsage(System.currentTimeMillis());
				key.setInvalidAttempts(0);
				toDeviceInfo(request.getDeviceInfo(), key);
				
				var response = new AuthResponse(token.getT1(), token.getT2().toEpochMilli(), user.getEmail(), key.getId().toString(), null, false);
				
				return r2dbc.insert(key).thenReturn(response).flatMap(this::withPreferences);
			})
		);
	}
	
	public Mono<InitRenewResponse> initRenew(InitRenewRequest request) {
		return keyRepo.findByIdAndEmail(UUID.fromString(request.getKeyId()), request.getEmail().toLowerCase())
		.switchIfEmpty(Mono.error(new ForbiddenException()))
		.flatMap(key -> {
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
			if (key.getRandom() == null || key.getRandomExpires() < System.currentTimeMillis()) return Mono.error(new ForbiddenException());
			if (!key.getRandom().equals(request.getRandom())) {
				key.setInvalidAttempts(key.getInvalidAttempts() + 1);
				if (key.getInvalidAttempts() > MAX_ATTEMPTS_BEFORE_REMOVING_KEY) {
					return keyRepo.delete(key).then(Mono.error(new ForbiddenException()));
				}
			}
			try {
				PublicKey publicKey = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(key.getPublicKey()));
				Signature signer = Signature.getInstance("SHA256withRSA");
				signer.initVerify(publicKey);
				signer.update((request.getEmail() + request.getRandom()).getBytes(StandardCharsets.UTF_8));
				if (!signer.verify(request.getSignature())) return Mono.error(new ForbiddenException());
			} catch (Exception e) {
				return Mono.error(new ForbiddenException());
			}
			return userRepo.findById(key.getEmail())
			.switchIfEmpty(Mono.error(new ForbiddenException()))
			.flatMap(user -> {	
				var token = auth.generateToken(key.getEmail(), user.getPassword() != null);
				key.setLastUsage(System.currentTimeMillis());
				toDeviceInfo(request.getDeviceInfo(), key);
				key.setRandom(null);
				key.setRandomExpires(0L);
				var response = new AuthResponse(token.getT1(), token.getT2().toEpochMilli(), key.getEmail(), key.getId().toString(), null, user.getPassword() != null);
				return keyRepo.save(key).thenReturn(response).flatMap(this::withPreferences);
			});
		});
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
	
	public Flux<UserKey> getMyKeys(Authentication auth) {
		return keyRepo.findByEmail(auth.getPrincipal().toString()).map(entity -> {
			Map<String, Object> info;
			try {
				info = TrailenceUtils.mapper.readValue(entity.getDeviceInfo().asString(), new TypeReference<Map<String, Object>>() {});
			} catch (Exception e) {
				log.error("Error decoding device info", e);
				info = new HashMap<>();
			}
			return new UserKey(entity.getId().toString(), entity.getCreatedAt(), entity.getLastUsage(), info);
		});
	}
	
	public Mono<Void> deleteMyKey(String id, Authentication auth) {
		return keyRepo.deleteByIdAndEmail(UUID.fromString(id), auth.getPrincipal().toString());
	}
	
	public Mono<String> getCaptchaKey() {
		return captchaService.getKey();
	}
	
	public Mono<Void> forgotPassword(ForgotPasswordRequest request) {
		if (!captchaService.isActivated()) return Mono.error(new ForbiddenException());
		if (request.getCaptchaToken() == null) return Mono.error(new ForbiddenException());
		if (request.getEmail() == null) return Mono.error(new ForbiddenException());
		String email = request.getEmail().toLowerCase();
		String lang = request.getLang();
		return userRepo.findByEmail(email)
		.switchIfEmpty(Mono.error(new ForbiddenException()))
		.flatMap(user -> 
			captchaService.validate(request.getCaptchaToken())
			.flatMap(ok -> {
				if (!ok.booleanValue()) return Mono.error(new ForbiddenException());
				user.setPassword(null);
				return userRepo.save(user);
			})
		)
		.flatMap(user -> userService.sendChangePasswordCode(email, lang))
		.then();
	}
	
}
