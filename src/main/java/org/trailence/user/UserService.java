package org.trailence.user;

import java.util.Map;
import java.util.UUID;

import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.stereotype.Service;
import org.trailence.email.EmailService;
import org.trailence.global.TrailenceUtils;
import org.trailence.global.exceptions.ForbiddenException;
import org.trailence.global.exceptions.NotFoundException;
import org.trailence.global.rest.TokenService;
import org.trailence.global.rest.TokenService.TokenData;
import org.trailence.trail.db.TrailCollectionEntity;
import org.trailence.trail.dto.TrailCollectionType;
import org.trailence.user.db.UserEntity;
import org.trailence.user.db.UserRepository;
import org.trailence.verificationcode.VerificationCodeService;
import org.trailence.verificationcode.VerificationCodeService.Spec;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

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

	public Mono<Void> createUser(String email, String password) {
		log.info("Creating new user: {}", email);
		email = email.toLowerCase();
		UserEntity entity = new UserEntity(email, password != null ? TrailenceUtils.hashPassword(password) : null, System.currentTimeMillis(), 0);
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

	
}
