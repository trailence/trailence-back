package org.trailence.verificationcode;

import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.trailence.global.TrailenceUtils;
import org.trailence.global.exceptions.BadRequestException;
import org.trailence.verificationcode.db.VerificationCodeEntity;
import org.trailence.verificationcode.db.VerificationCodeRepository;

import com.fasterxml.jackson.core.JsonProcessingException;

import io.r2dbc.postgresql.codec.Json;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class VerificationCodeService {

	private final VerificationCodeRepository repo;
	private final R2dbcEntityTemplate r2dbc;
	private final SecureRandom random;
	
	public enum Spec {
		DIGITS
	}
	
	public Mono<String> generate(String key, String type, long expiration, Object data, int length, Spec codeSpec, int maxCurrentCodes) {
		return Mono.defer(() -> {
			VerificationCodeEntity entity = new VerificationCodeEntity();
			entity.setCode(generateCode(length, codeSpec));
			entity.setType(type);
			entity.setKey(key);
			entity.setExpiresAt(System.currentTimeMillis() + expiration);
			try {
				entity.setData(Json.of(TrailenceUtils.mapper.writeValueAsString(data)));
			} catch (JsonProcessingException e) {
				return Mono.error(e);
			}
			return r2dbc.insert(entity)
			.then(removeCodes(key, type, maxCurrentCodes))
			.thenReturn(entity.getCode());
		});
	}
	
	private String generateCode(int length, Spec spec) {
		StringBuilder s = new StringBuilder(length);
		while (s.length() < length) s.append(generateChar(spec));
		return s.toString();
	}
	
	@SuppressWarnings("java:S1301") // switch instead of if
	private char generateChar(Spec spec) {
		switch (spec) {
		case DIGITS: return (char)('0' + random.nextInt(10));
		default: return (char)(32 + random.nextInt(127 - 32));
		}
	}
	
	private Mono<Void> removeCodes(String key, String type, int maxCurrentCodes) {
		if (maxCurrentCodes <= 0) return Mono.empty();
		return repo.findByTypeAndKey(type, key, PageRequest.of(1, maxCurrentCodes, Direction.DESC, "expiresAt"))
		.flatMap(repo::delete, 2, 4)
		.then();
	}
	
	public <T> Mono<T> check(String code, String type, String key, Class<T> clazz) {
		return repo.findByTypeAndKeyAndExpiresAtGreaterThan(type, key, System.currentTimeMillis())
		.collectList()
		.flatMap(codes -> {
			if (codes.isEmpty()) return Mono.error(new BadRequestException("invalid-code", "Invalid code"));
			var codeOpt = codes.stream().filter(entity -> entity.getCode().equals(code) && entity.getInvalidAttempts() < 3).findAny();
			if (codeOpt.isEmpty()) {
				return Flux.fromIterable(codes)
				.flatMap(entity -> {
					if (entity.getInvalidAttempts() < 3) {
						entity.setInvalidAttempts(entity.getInvalidAttempts() + 1);
						return repo.save(entity);
					}
					return repo.delete(entity);
				}, 2, 4).then(Mono.error(new BadRequestException("invalid-code", "Invalid code")));
			}
			var entity = codeOpt.get();
			T result;
			try {
				result = TrailenceUtils.mapper.readValue(entity.getData().asString(), clazz);
			} catch (Exception err) {
				return Mono.error(err);
			}
			return repo.delete(entity).thenReturn(result);
		});
	}
	
	public Mono<Void> cancelAll(String type, String key) {
		return repo.deleteAllByTypeAndKey(type, key);
	}
	
	@Scheduled(fixedRate = 60, timeUnit = TimeUnit.MINUTES, initialDelay = 5)
	public void clean() {
		log.info("Cleaning expired verification codes");
		repo.deleteAllByExpiresAtLessThan(System.currentTimeMillis())
		.checkpoint("Clean expired verification codes")
		.doOnNext(nb -> log.info("Verification codes removed: {}", nb))
		.subscribe();
	}
	
}
