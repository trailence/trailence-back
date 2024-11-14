package org.trailence.global.rest;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import org.trailence.global.TrailenceUtils;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.JWTVerifier;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

@Component
@Slf4j
public class JwtAuthenticationManager implements ReactiveAuthenticationManager, InitializingBean {

	@Value("${trailence.jwt.secret}")
	private String secret;
	@Value("${trailence.jwt.validity:60m}")
	private Duration tokenValidity;
	
	private Algorithm algo;
	private JWTVerifier verifier;
	
	@Override
	public void afterPropertiesSet() throws Exception {
		algo = Algorithm.HMAC512(secret);
		verifier = JWT.require(algo).build();
	}
	
	@Override
	public Mono<Authentication> authenticate(Authentication authentication) {
		return Mono.defer(() -> {
			String token = authentication.getCredentials().toString();
			DecodedJWT decoded = JWT.decode(token);
			try {
				verifier.verify(token);
			} catch (Exception e) {
				log.info("Invalid token: {}", e.getMessage());
				return Mono.empty();
			}
			Boolean isComplete = decoded.getClaim("complete").asBoolean();
			List<GrantedAuthority> authorities = Boolean.TRUE.equals(isComplete) ? List.of(new SimpleGrantedAuthority(TrailenceUtils.AUTHORITY_COMPLETE_USER)) : List.of();
			return Mono.just(new UsernamePasswordAuthenticationToken(decoded.getSubject(), token, authorities));
		});
	}
	
	public Tuple2<String, Instant> generateToken(String email, boolean isComplete) {
		var expires = Instant.now().plus(tokenValidity);
		var token = JWT.create()
			.withSubject(email)
			.withExpiresAt(expires)
			.withClaim("complete", isComplete)
			.sign(algo);
		return Tuples.of(token, expires);
	}
	
}
