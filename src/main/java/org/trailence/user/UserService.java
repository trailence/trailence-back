package org.trailence.user;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.sql.AliasedExpression;
import org.springframework.data.relational.core.sql.AsteriskFromTable;
import org.springframework.data.relational.core.sql.Column;
import org.springframework.data.relational.core.sql.Conditions;
import org.springframework.data.relational.core.sql.Expressions;
import org.springframework.data.relational.core.sql.SimpleFunction;
import org.springframework.data.relational.core.sql.Table;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestBody;
import org.trailence.auth.db.UserKeyEntity;
import org.trailence.auth.db.UserKeyRepository;
import org.trailence.captcha.CaptchaService;
import org.trailence.email.EmailService;
import org.trailence.extensions.db.UserExtensionRepository;
import org.trailence.global.TrailenceUtils;
import org.trailence.global.db.DbUtils;
import org.trailence.global.db.SqlBuilder;
import org.trailence.global.dto.PageResult;
import org.trailence.global.exceptions.ForbiddenException;
import org.trailence.global.exceptions.NotFoundException;
import org.trailence.global.rest.TokenService;
import org.trailence.global.rest.TokenService.TokenData;
import org.trailence.notifications.db.NotificationsRepository;
import org.trailence.preferences.db.UserPreferencesRepository;
import org.trailence.quotas.QuotaService;
import org.trailence.quotas.db.UserQuotasEntity;
import org.trailence.quotas.db.UserQuotasRepository;
import org.trailence.quotas.db.UserSubscriptionEntity;
import org.trailence.quotas.db.UserSubscriptionRepository;
import org.trailence.quotas.dto.UserQuotas;
import org.trailence.trail.ShareService;
import org.trailence.trail.TrailCollectionService;
import org.trailence.trail.db.TrailCollectionEntity;
import org.trailence.trail.dto.TrailCollectionType;
import org.trailence.user.db.UserEntity;
import org.trailence.user.db.UserRepository;
import org.trailence.user.dto.RegisterNewUserCodeRequest;
import org.trailence.user.dto.RegisterNewUserRequest;
import org.trailence.user.dto.User;
import org.trailence.verificationcode.VerificationCodeService;
import org.trailence.verificationcode.VerificationCodeService.Spec;

import com.fasterxml.jackson.core.JsonProcessingException;

import io.r2dbc.postgresql.codec.Json;
import io.r2dbc.spi.Row;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

	private final UserRepository userRepo;
	private final UserQuotasRepository userQuotaRepo;
	private final UserExtensionRepository userExtensionRepo;
	private final UserKeyRepository userKeyRepo;
	private final UserSubscriptionRepository userSubscriptionRepo;
	private final UserPreferencesRepository userPreferencesRepo;
	private final NotificationsRepository notificationsRepo;
	private final R2dbcEntityTemplate r2dbc;
	private final VerificationCodeService verificationCodeService;
	private final EmailService emailService;
	private final TokenService tokenService;
	private final QuotaService quotaService;
	private final CaptchaService captchaService;
	private final TrailCollectionService collectionService;
	private final ShareService shareService;
	
	private static final String CHANGE_PASSWORD_VERIFICATION_CODE_TYPE = "change_password";
	private static final String FORGOT_PASSWORD_VERIFICATION_CODE_TYPE = "forgot_password";
	private static final long CHANGE_PASSWORD_VERIFICATION_CODE_EXPIRATION = 10L * 60 * 1000;
	private static final String REGISTER_VERIFICATION_CODE_TYPE = "register_user";
	private static final long REGISTER_VERIFICATION_CODE_EXPIRATION = 10L * 60 * 1000;
	private static final String DELETION_VERIFICATION_CODE_TYPE = "delete_user";
	private static final long DELETION_VERIFICATION_CODE_EXPIRATION = 10L * 60 * 1000;

	@Transactional
	public Mono<Void> createUser(String emailInput, String password, boolean isAdmin, List<Tuple2<String, Optional<Long>>> subscriptions) {
		log.info("Creating new user: {} with admin {} and subscriptions {}", emailInput, isAdmin, subscriptions);
		String email = emailInput.toLowerCase();
		long now = System.currentTimeMillis();
		UserEntity entity = new UserEntity(
			email,
			password != null ? TrailenceUtils.hashPassword(password) : null,
			now, // createdAt
			0, // invalidAttempts
			isAdmin,
			null, // roles
			null // lastPasswordEmail
		);
		TrailCollectionEntity myTrails = new TrailCollectionEntity();
		myTrails.setUuid(UUID.randomUUID());
		myTrails.setOwner(email);
		myTrails.setType(TrailCollectionType.MY_TRAILS);
		myTrails.setName("");
		var subscriptionsEntities = subscriptions.stream().map(s -> new UserSubscriptionEntity(UUID.randomUUID(), email, s.getT1(), now, s.getT2().orElse(null)));
		return r2dbc.insert(entity)
		.then(r2dbc.insert(myTrails))
		.thenMany(Flux.fromStream(subscriptionsEntities).flatMap(r2dbc::insert))
		.then(quotaService.initUserQuotas(email, now));
	}
	
	public List<String> toRolesList(Json roles) {
		if (roles == null) return List.of();
		try {
			return Arrays.asList(TrailenceUtils.mapper.readValue(roles.asString(), String[].class));
		} catch (Exception e) {
			return List.of();
		}
	}
	
	public Mono<Void> sendChangePasswordCode(String email, String lang, boolean isForgot) {
		String codeType = isForgot ? FORGOT_PASSWORD_VERIFICATION_CODE_TYPE : CHANGE_PASSWORD_VERIFICATION_CODE_TYPE;
		int priority = isForgot ? EmailService.FORGOT_PASSWORD_PRIORITY : EmailService.CHANGE_PASSWORD_PRIORITY;
		return userRepo.findByEmail(email)
		.switchIfEmpty(Mono.error(new NotFoundException("user", email)))
		.flatMap(user -> {
			if (user.getLastPasswordEmail() != null && System.currentTimeMillis() - user.getLastPasswordEmail() < CHANGE_PASSWORD_VERIFICATION_CODE_EXPIRATION)
				return Mono.error(new ForbiddenException("change-password-already-sent"));
			user.setLastPasswordEmail(System.currentTimeMillis());
			return userRepo.save(user).then(Mono.fromCallable(() -> tokenService.generate(new TokenData("stop_change_password", email, ""))));
		})
		.flatMap(stopLink ->
			verificationCodeService.generate(email, codeType, System.currentTimeMillis() + CHANGE_PASSWORD_VERIFICATION_CODE_EXPIRATION, "", 6, Spec.DIGITS, 3)
			.flatMap(code -> emailService.send(priority, email, "change_password_code", lang, Map.of(
				"code", code,
				"stop_url", emailService.getLinkUrl(stopLink)
			)))
		);
	}
	
	public Mono<Void> cancelChangePassword(String token) {
		return Mono.fromCallable(() -> tokenService.check(token))
		.flatMap(data -> {
			log.info("Cancel change password requested for user: {}", data.getEmail());
			return verificationCodeService.cancelAll(CHANGE_PASSWORD_VERIFICATION_CODE_TYPE, data.getEmail())
			.then(verificationCodeService.cancelAll(FORGOT_PASSWORD_VERIFICATION_CODE_TYPE, data.getEmail()));
		});
	}
	
	public Mono<Void> changePassword(String email, String code, String newPassword, String previousPassword, boolean isForgot) {
		if (email == null) return Mono.error(new ForbiddenException());
		if (code == null) return Mono.error(new ForbiddenException());
		if (newPassword == null || newPassword.length() < TrailenceUtils.MIN_PASSWORD_SIZE) return Mono.error(new ForbiddenException());
		String userMail = email.toLowerCase();
		return userRepo.findByEmail(userMail)
		.flatMap(user -> {
			Mono<String> checkCode;
			if (isForgot)
				checkCode = verificationCodeService.check(code, FORGOT_PASSWORD_VERIFICATION_CODE_TYPE, userMail, String.class);
			else {
				if (user.getPassword() != null && !user.getPassword().equals(TrailenceUtils.hashPassword(previousPassword)))
					return Mono.error(new ForbiddenException());
				checkCode = verificationCodeService.check(code, CHANGE_PASSWORD_VERIFICATION_CODE_TYPE, userMail, String.class);
			}
			return checkCode
			.flatMap(check -> {
				user.setPassword(TrailenceUtils.hashPassword(newPassword));
				user.setInvalidAttempts(0);
				return userRepo.save(user);
			});
		})
		.then();
	}
	
	public Mono<Void> sendRegisterCode(RegisterNewUserCodeRequest request) {
		if (request.getEmail() == null) return Mono.error(new ForbiddenException());
		Mono<Void> checkCaptcha;
		if (captchaService.isActivated()) {
			if (request.getCaptcha() == null) return Mono.error(new ForbiddenException());
			checkCaptcha = captchaService.validate(request.getCaptcha()).flatMap(ok ->ok.booleanValue() ? Mono.empty() : Mono.error(new ForbiddenException()));
		} else
			checkCaptcha = Mono.empty();
		var email = request.getEmail().toLowerCase();
		return checkCaptcha
		.then(userRepo.findByEmail(email).map(Optional::of).switchIfEmpty(Mono.just(Optional.empty())))
		.flatMap(userExists -> {
			if (userExists.isPresent())
				return this.sendChangePasswordCode(email, request.getLang(), true);
			return verificationCodeService.generate(email, REGISTER_VERIFICATION_CODE_TYPE, System.currentTimeMillis() + REGISTER_VERIFICATION_CODE_EXPIRATION, "", 6, Spec.DIGITS, 3)
			.flatMap(code -> {
				String stopToken;
				try {
					stopToken = tokenService.generate(new TokenData("stop_registration", email, ""));
				} catch (Exception e) {
					return Mono.error(e);
				}
				return emailService.send(EmailService.REGISTER_USER_PRIORITY, email, "registration_code", request.getLang(), Map.of(
					"code", code,
					"stop_url", emailService.getLinkUrl(stopToken)
				));
			});
		});
	}
	
	public Mono<Void> cancelRegisterCode(String token) {
		return Mono.fromCallable(() -> tokenService.check(token))
		.flatMap(data -> {
			log.info("Cancel registration request for user: {}", data.getEmail());
			return verificationCodeService.cancelAll(REGISTER_VERIFICATION_CODE_TYPE, data.getEmail())
			.then(verificationCodeService.cancelAll(FORGOT_PASSWORD_VERIFICATION_CODE_TYPE, data.getEmail()));
		});
	}
	
	@Transactional
	@SuppressWarnings("java:S6809")
	public Mono<Void> registerNewUser(@RequestBody RegisterNewUserRequest request) {
		if (request.getEmail() == null) return Mono.error(new ForbiddenException());
		if (request.getCode() == null) return Mono.error(new ForbiddenException());
		if (request.getPassword() == null || request.getPassword().length() < TrailenceUtils.MIN_PASSWORD_SIZE) return Mono.error(new ForbiddenException());
		String email = request.getEmail().toLowerCase();
		return userRepo.findByEmail(email).map(Optional::of).switchIfEmpty(Mono.just(Optional.empty()))
		.flatMap(userExists -> {
			if (userExists.isPresent())
				return changePassword(email, request.getCode(), request.getPassword(), null, true);
			return verificationCodeService.check(request.getCode(), REGISTER_VERIFICATION_CODE_TYPE, email, String.class)
			.flatMap(check -> createUser(email, request.getPassword(), false, List.of(Tuples.of(TrailenceUtils.FREE_PLAN, Optional.empty()))));
		})
		.then();
	}
	
	public Mono<Void> sendDeletionCode(String lang, Authentication auth) {
		String email = auth.getPrincipal().toString();
		return verificationCodeService.generate(email, DELETION_VERIFICATION_CODE_TYPE, System.currentTimeMillis() + DELETION_VERIFICATION_CODE_EXPIRATION, "", 6, Spec.DIGITS, 3)
		.flatMap(code -> {
			String stopToken;
			try {
				stopToken = tokenService.generate(new TokenData("stop_deletion", email, ""));
			} catch (Exception e) {
				return Mono.error(e);
			}
			return emailService.send(EmailService.DELETE_USER_PRIORITY, email, "deletion_code", lang, Map.of(
				"code", code,
				"stop_url", emailService.getLinkUrl(stopToken)
			));
		});
	}
	
	public Mono<Void> cancelDeletionCode(String token) {
		return Mono.fromCallable(() -> tokenService.check(token))
		.flatMap(data -> {
			log.info("Cancel deletion request for user: {}", data.getEmail());
			return verificationCodeService.cancelAll(DELETION_VERIFICATION_CODE_TYPE, data.getEmail());
		});
	}
	
	@Transactional
	public Mono<Void> deleteUser(String code, String email) {
		return verificationCodeService.check(code, DELETION_VERIFICATION_CODE_TYPE, email, String.class)
		.flatMap(check ->
			collectionService.deleteUser(email)
			.then(shareService.deleteRecipient(email))
			.then(userRepo.deleteByEmail(email))
			.then(userKeyRepo.deleteAllByEmail(email))
			.then(userQuotaRepo.deleteById(email))
			.then(userExtensionRepo.deleteAllByEmail(email))
			.then(userPreferencesRepo.deleteById(email))
			.then(userSubscriptionRepo.deleteAllByUserEmail(email))
			.then(notificationsRepo.deleteByOwner(email))
			.then()
		);
	}

	private static final Table ALIAS_APP_VERSION = Table.create("app_versions");
	private static final String MIN_VERSION = "min_version";
	private static final String MAX_VERSION = "max_version";
	private static final Column COL_MIN_VERSION = Column.create(MIN_VERSION, ALIAS_APP_VERSION);
	private static final Column COL_MAX_VERSION = Column.create(MAX_VERSION, ALIAS_APP_VERSION);
	private static final String COL_COMPLETE = "complete";
	private static final Map<String, Object> userDtoFieldMapping = new HashMap<>();
	
	static {
		userDtoFieldMapping.put("email", UserEntity.COL_EMAIL);
		userDtoFieldMapping.put("createdAt", UserEntity.COL_CREATED_AT);
		userDtoFieldMapping.put(COL_COMPLETE, COL_COMPLETE);
		userDtoFieldMapping.put("admin", UserEntity.COL_IS_ADMIN);
		userDtoFieldMapping.put("invalidLoginAttempts", UserEntity.COL_INVALID_ATTEMPTS);
		userDtoFieldMapping.put("lastLogin", UserKeyEntity.COL_LAST_USAGE);
		userDtoFieldMapping.put("minAppVersion", COL_MIN_VERSION);
		userDtoFieldMapping.put("maxAppVersion", COL_MAX_VERSION);
		userDtoFieldMapping.put("quotas.collectionsUsed", UserQuotasEntity.COL_COLLECTIONS_USED);
		userDtoFieldMapping.put("quotas.trailsUsed", UserQuotasEntity.COL_TRAILS_USED);
		userDtoFieldMapping.put("quotas.tracksUsed", UserQuotasEntity.COL_TRACKS_USED);
		userDtoFieldMapping.put("quotas.tracksSizeUsed", UserQuotasEntity.COL_TRACKS_SIZE_USED);
		userDtoFieldMapping.put("quotas.tagsUsed", UserQuotasEntity.COL_TAGS_USED);
		userDtoFieldMapping.put("quotas.trailTagsUsed", UserQuotasEntity.COL_TRAIL_TAGS_USED);
		userDtoFieldMapping.put("quotas.photosUsed", UserQuotasEntity.COL_PHOTOS_USED);
		userDtoFieldMapping.put("quotas.photosSizeUsed", UserQuotasEntity.COL_PHOTOS_SIZE_USED);
		userDtoFieldMapping.put("quotas.sharesUsed", UserQuotasEntity.COL_SHARES_USED);
	}

	@PreAuthorize(TrailenceUtils.PREAUTHORIZE_ADMIN)
	public Mono<PageResult<User>> getUsers(Pageable pageable) {
		Table userVersionsTable = Table.create("user_versions");
		String sql = new SqlBuilder()
		.select(
			UserEntity.COL_EMAIL,
			UserEntity.COL_CREATED_AT,
			new AliasedExpression(Conditions.not(Conditions.isNull(UserEntity.COL_PASSWORD)), COL_COMPLETE),
			UserEntity.COL_IS_ADMIN,
			UserEntity.COL_ROLES,
			UserEntity.COL_INVALID_ATTEMPTS,
			UserKeyEntity.COL_LAST_USAGE,
			COL_MIN_VERSION,
			COL_MAX_VERSION,
			AsteriskFromTable.create(UserQuotasEntity.TABLE)
		)
		.from(UserEntity.TABLE)
		// user quotas
		.leftJoinTable(UserQuotasEntity.TABLE, Conditions.isEqual(UserQuotasEntity.COL_EMAIL, UserEntity.COL_EMAIL), null)
		// last usage from keys
		.leftJoinSubSelect(new SqlBuilder()
			.select(
				UserKeyEntity.COL_EMAIL,
				SimpleFunction.create("MAX", List.of(UserKeyEntity.COL_LAST_USAGE)).as(UserKeyEntity.COL_LAST_USAGE.getName())
			)
			.from(UserKeyEntity.TABLE)
			.groupBy(UserKeyEntity.COL_EMAIL)
			.build(),
			Conditions.isEqual(UserKeyEntity.COL_EMAIL, UserEntity.COL_EMAIL),
			UserKeyEntity.TABLE.toString()
		)
		// min and max version from keys
		.leftJoinSubSelect(
			new SqlBuilder()
			.select(
				Column.create(UserEntity.COL_EMAIL.getName(), userVersionsTable),
				SimpleFunction.create("MIN", List.of(Column.create(MIN_VERSION, userVersionsTable))).as(MIN_VERSION),
				SimpleFunction.create("MAX", List.of(Column.create(MAX_VERSION, userVersionsTable))).as(MAX_VERSION)
			)
			.from(
				new SqlBuilder()
				.select(
					Column.create(UserEntity.COL_EMAIL.getName(), Table.create("tmp_versions")),
					Expressions.just("tmp_versions.device_info ->> 'deviceId' as device_id"),
					Expressions.just("min((tmp_versions.device_info ->> 'versionCode')::bigint) as " + MIN_VERSION),
					Expressions.just("max((tmp_versions.device_info ->> 'versionCode')::bigint) as " + MAX_VERSION)
				)
				.from(UserKeyEntity.TABLE, "tmp_versions")
				.groupBy(Expressions.just("tmp_versions." + UserEntity.COL_EMAIL.getName().toString()), Expressions.just("tmp_versions.device_info ->> 'deviceId'"))
				.build(),
				"user_versions"
			)
			.groupBy(Column.create(UserEntity.COL_EMAIL.getName(), userVersionsTable))
			.build(),
			Conditions.isEqual(Column.create(UserEntity.COL_EMAIL.getName(), ALIAS_APP_VERSION), UserEntity.COL_EMAIL),
			ALIAS_APP_VERSION.getName().toString()
		)
		.pageable(pageable, userDtoFieldMapping)
		.build();
		return Mono.zip(
			r2dbc.query(DbUtils.operation(sql, null), this::toUserDto)
				.all().collectList().publishOn(Schedulers.parallel()),
			userRepo.count().publishOn(Schedulers.parallel())
		).map(result -> new PageResult<User>(pageable, result.getT1(), result.getT2()));
	}
	
	private User toUserDto(Row row) {
		return new User(
			row.get(UserEntity.COL_EMAIL.getName().toString(), String.class),
			row.get(UserEntity.COL_CREATED_AT.getName().toString(), Long.class),
			row.get(COL_COMPLETE, Boolean.class),
			row.get(UserEntity.COL_IS_ADMIN.getName().toString(), Boolean.class),
			toRolesList(row.get(UserEntity.COL_ROLES.getName().toString(), Json.class)),
			row.get(UserEntity.COL_INVALID_ATTEMPTS.getName().toString(), Integer.class),
			row.get(UserKeyEntity.COL_LAST_USAGE.getName().toString(), Long.class),
			row.get(COL_MIN_VERSION.getName().toString(), Long.class),
			row.get(COL_MAX_VERSION.getName().toString(), Long.class),
			new UserQuotas(
				row.get(UserQuotasEntity.COL_COLLECTIONS_USED.getName().toString(), Short.class),
				row.get(UserQuotasEntity.COL_COLLECTIONS_MAX.getName().toString(), Short.class),
				row.get(UserQuotasEntity.COL_TRAILS_USED.getName().toString(), Integer.class),
				row.get(UserQuotasEntity.COL_TRAILS_MAX.getName().toString(), Integer.class),
				row.get(UserQuotasEntity.COL_TRACKS_USED.getName().toString(), Integer.class),
				row.get(UserQuotasEntity.COL_TRACKS_MAX.getName().toString(), Integer.class),
				row.get(UserQuotasEntity.COL_TRACKS_SIZE_USED.getName().toString(), Integer.class),
				row.get(UserQuotasEntity.COL_TRACKS_SIZE_MAX.getName().toString(), Integer.class),
				row.get(UserQuotasEntity.COL_PHOTOS_USED.getName().toString(), Integer.class),
				row.get(UserQuotasEntity.COL_PHOTOS_MAX.getName().toString(), Integer.class),
				row.get(UserQuotasEntity.COL_PHOTOS_SIZE_USED.getName().toString(), Long.class),
				row.get(UserQuotasEntity.COL_PHOTOS_SIZE_MAX.getName().toString(), Long.class),
				row.get(UserQuotasEntity.COL_TAGS_USED.getName().toString(), Integer.class),
				row.get(UserQuotasEntity.COL_TAGS_MAX.getName().toString(), Integer.class),
				row.get(UserQuotasEntity.COL_TRAIL_TAGS_USED.getName().toString(), Integer.class),
				row.get(UserQuotasEntity.COL_TRAIL_TAGS_MAX.getName().toString(), Integer.class),
				row.get(UserQuotasEntity.COL_SHARES_USED.getName().toString(), Short.class),
				row.get(UserQuotasEntity.COL_SHARES_MAX.getName().toString(), Short.class)
			)
		);
	}
	
	@PreAuthorize(TrailenceUtils.PREAUTHORIZE_ADMIN)
	public Mono<List<String>> updateUserRoles(String email, List<String> roles) {
		return userRepo.findByEmail(email.toLowerCase())
		.flatMap(entity -> {
			if (roles.isEmpty()) entity.setRoles(null);
			else
				try {
					entity.setRoles(Json.of(TrailenceUtils.mapper.writeValueAsString(roles)));
				} catch (JsonProcessingException e) {
					// ignore
				}
			return userRepo.save(entity);
		})
		.map(entity -> toRolesList(entity.getRoles()));
	}
	
}
