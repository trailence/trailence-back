package org.trailence.user;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.sql.AliasedExpression;
import org.springframework.data.relational.core.sql.Column;
import org.springframework.data.relational.core.sql.Conditions;
import org.springframework.data.relational.core.sql.Expressions;
import org.springframework.data.relational.core.sql.SimpleFunction;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.trailence.auth.db.UserKeyEntity;
import org.trailence.email.EmailService;
import org.trailence.global.TrailenceUtils;
import org.trailence.global.db.DbUtils;
import org.trailence.global.db.SqlBuilder;
import org.trailence.global.dto.PageResult;
import org.trailence.global.exceptions.ForbiddenException;
import org.trailence.global.exceptions.NotFoundException;
import org.trailence.global.rest.TokenService;
import org.trailence.global.rest.TokenService.TokenData;
import org.trailence.trail.db.TrailCollectionEntity;
import org.trailence.trail.dto.TrailCollectionType;
import org.trailence.user.db.UserEntity;
import org.trailence.user.db.UserRepository;
import org.trailence.user.dto.User;
import org.trailence.verificationcode.VerificationCodeService;
import org.trailence.verificationcode.VerificationCodeService.Spec;

import io.r2dbc.spi.Row;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

	private final UserRepository userRepo;
	private final R2dbcEntityTemplate r2dbc;
	private final VerificationCodeService verificationCodeService;
	private final EmailService emailService;
	private final TokenService tokenService;
	
	private static final String CHANGE_PASSWORD_VERIFICATION_CODE_TYPE = "change_password";
	private static final String FORGOT_PASSWORD_VERIFICATION_CODE_TYPE = "forgot_password";

	public Mono<Void> createUser(String email, String password, boolean isAdmin) {
		log.info("Creating new user: {}", email);
		email = email.toLowerCase();
		UserEntity entity = new UserEntity(email, password != null ? TrailenceUtils.hashPassword(password) : null, System.currentTimeMillis(), 0, isAdmin);
		TrailCollectionEntity myTrails = new TrailCollectionEntity();
		myTrails.setUuid(UUID.randomUUID());
		myTrails.setOwner(email);
		myTrails.setType(TrailCollectionType.MY_TRAILS);
		myTrails.setName("");
		return r2dbc.insert(entity)
				.then(r2dbc.insert(myTrails))
				.then();
	}
	
	public Mono<Void> sendChangePasswordCode(String email, String lang, boolean isForgot) {
		String codeType = isForgot ? FORGOT_PASSWORD_VERIFICATION_CODE_TYPE : CHANGE_PASSWORD_VERIFICATION_CODE_TYPE;
		return userRepo.findByEmail(email)
		.switchIfEmpty(Mono.error(new NotFoundException("user", email)))
		.flatMap(user -> Mono.fromCallable(() -> tokenService.generate(new TokenData("stop_change_password", email, ""))))
		.flatMap(stopLink ->
			verificationCodeService.generate(email, codeType, System.currentTimeMillis() + 10L * 60 * 1000, "", 6, Spec.DIGITS, 3)
			.flatMap(code -> emailService.send(email, "change_password_code", lang, Map.of(
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

	private static final Column COL_MIN_VERSION = Column.create("min_version", UserKeyEntity.TABLE);
	private static final Column COL_MAX_VERSION = Column.create("max_version", UserKeyEntity.TABLE);
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
	}

	@PreAuthorize(TrailenceUtils.PREAUTHORIZE_ADMIN)
	public Mono<PageResult<User>> getUsers(Pageable pageable) {
		String sql = new SqlBuilder()
		.select(
			UserEntity.TABLE,
			UserEntity.COL_EMAIL,
			UserEntity.COL_CREATED_AT,
			new AliasedExpression(Conditions.not(Conditions.isNull(UserEntity.COL_PASSWORD)), COL_COMPLETE),
			UserEntity.COL_IS_ADMIN,
			UserEntity.COL_INVALID_ATTEMPTS,
			UserKeyEntity.COL_LAST_USAGE,
			COL_MIN_VERSION,
			COL_MAX_VERSION
		)
		.leftJoin("(" + 
				new SqlBuilder()
				.select(
					UserKeyEntity.TABLE,
					UserKeyEntity.COL_EMAIL,
					SimpleFunction.create("MAX", List.of(UserKeyEntity.COL_LAST_USAGE)).as(UserKeyEntity.COL_LAST_USAGE.getName()),
					SimpleFunction.create("MIN", List.of(Expressions.just("(device_info ->> 'versionCode')::bigint"))).as(COL_MIN_VERSION.getName()),
					SimpleFunction.create("MAX", List.of(Expressions.just("(device_info ->> 'versionCode')::bigint"))).as(COL_MAX_VERSION.getName())
				)
				.groupBy(UserKeyEntity.COL_EMAIL)
				.build()
			+ ")",
			Conditions.isEqual(UserKeyEntity.COL_EMAIL, UserEntity.COL_EMAIL),
			UserKeyEntity.TABLE.toString()
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
			row.get(UserEntity.COL_INVALID_ATTEMPTS.getName().toString(), Integer.class),
			row.get(UserKeyEntity.COL_LAST_USAGE.getName().toString(), Long.class),
			row.get(COL_MIN_VERSION.getName().toString(), Long.class),
			row.get(COL_MAX_VERSION.getName().toString(), Long.class)
		);
	}
	
}
