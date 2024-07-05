package org.trailence.global.rest;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.trailence.global.exceptions.UnauthorizedException;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.JWTVerifier;

import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

@Component
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
		return Mono.fromCallable(() -> {
			String token = authentication.getCredentials().toString();
			DecodedJWT decoded = JWT.decode(token);
			try {
				verifier.verify(token);
			} catch (Exception e) {
				throw new UnauthorizedException();
			}
			return new UsernamePasswordAuthenticationToken(decoded.getSubject(), token, List.of());
		});
	}
	
	public Tuple2<String, Instant> generateToken(String email) {
		var expires = Instant.now().plus(tokenValidity);
		var token = JWT.create()
			.withSubject(email)
			.withExpiresAt(expires)
			.sign(algo);
		return Tuples.of(token, expires);
	}
	
}
