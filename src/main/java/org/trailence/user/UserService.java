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
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class UserService {

	private final UserRepository userRepo;
	private final R2dbcEntityTemplate r2dbc;
	private final VerificationCodeService verificationCodeService;
	private final EmailService emailService;
	private final TokenService tokenService;

	public Mono<Void> createUser(String email, String password) {
		UserEntity entity = new UserEntity(email.toLowerCase(), password != null ? TrailenceUtils.hashPassword(password) : null, System.currentTimeMillis(), 0);
		TrailCollectionEntity myTrails = new TrailCollectionEntity();
		myTrails.setUuid(UUID.randomUUID());
		myTrails.setOwner(email);
		myTrails.setType(TrailCollectionType.MY_TRAILS);
		myTrails.setName("");
		return r2dbc.insert(entity)
				.then(r2dbc.insert(myTrails))
				.then();
	}
	
	public Mono<Void> sendChangePasswordCode(String email, String lang) {
		return userRepo.findByEmail(email)
		.switchIfEmpty(Mono.error(new NotFoundException("user", email)))
		.flatMap(user -> Mono.fromCallable(() -> tokenService.generate(new TokenData("stop_change_password", email, ""))))
		.flatMap(stopLink ->
			verificationCodeService.generate(email, "change_password", System.currentTimeMillis() + 10L * 60 * 1000, "", 6, Spec.DIGITS, 3)
			.flatMap(code -> emailService.send(email, "change_password_code", lang, Map.of(
				"code", code,
				"stop_url", emailService.getLinkUrl(stopLink)
			)))
		);
	}
	
	public Mono<Void> cancelChangePassword(String token) {
		return Mono.fromCallable(() -> tokenService.check(token))
		.flatMap(data -> verificationCodeService.cancelAll("change_password", data.getEmail()));
	}
	
	public Mono<Void> changePassword(String email, String code, String newPassword, String previousPassword) {
		if (email == null) return Mono.error(new ForbiddenException());
		return userRepo.findByEmail(email.toLowerCase())
		.flatMap(user -> {
			if (user.getPassword() != null && !user.getPassword().equals(previousPassword))
				return Mono.error(new ForbiddenException());
			return verificationCodeService.check(code, "change_password", email, String.class)
			.flatMap(check -> {
				user.setPassword(TrailenceUtils.hashPassword(newPassword));
				user.setInvalidAttempts(0);
				return userRepo.save(user);
			});
		})
		.then();
	}

	
}
