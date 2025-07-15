package org.trailence.global.rest;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedList;
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
	
	private static final String CLAIM_COMPLETE = "cpl";
	private static final String CLAIM_ADMIN = "adm";
	private static final String CLAIM_ROLES = "rl";
	
	@Override
	public void afterPropertiesSet() throws Exception {
		algo = Algorithm.HMAC512(secret);
		verifier = JWT.require(algo).build();
	}
	
	@Override
	public Mono<Authentication> authenticate(Authentication authentication) {
		return Mono.defer(() -> {
			String token = authentication.getCredentials().toString();
			DecodedJWT decoded;
			try {
				decoded = JWT.decode(token);
				verifier.verify(token);
			} catch (Exception e) {
				log.info("Invalid token: {}", e.getMessage());
				return Mono.empty();
			}
			Integer isComplete = decoded.getClaim(CLAIM_COMPLETE).asInt();
			Integer isAdmin = decoded.getClaim(CLAIM_ADMIN).asInt();
			String[] roles = decoded.getClaim(CLAIM_ROLES).asArray(String.class);
			List<GrantedAuthority> authorities = new LinkedList<>();
			if (Integer.valueOf(1).equals(isComplete)) authorities.add(new SimpleGrantedAuthority(TrailenceUtils.AUTHORITY_COMPLETE_USER));
			if (Integer.valueOf(1).equals(isAdmin)) authorities.add(new SimpleGrantedAuthority(TrailenceUtils.AUTHORITY_ADMIN_USER));
			for (String role : roles) authorities.add(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()));
			return Mono.just(new UsernamePasswordAuthenticationToken(decoded.getSubject(), token, authorities));
		});
	}
	
	public Tuple2<String, Instant> generateToken(String email, boolean isComplete, boolean isAdmin, List<String> roles) {
		var expires = Instant.now().plus(tokenValidity);
		var token = JWT.create()
			.withSubject(email)
			.withExpiresAt(expires)
			.withClaim(CLAIM_COMPLETE, isComplete ? 1 : 0)
			.withClaim(CLAIM_ADMIN, isAdmin ? 1 : 0)
			.withArrayClaim(CLAIM_ROLES, roles.toArray(new String[roles.size()]))
			.sign(algo);
		return Tuples.of(token, expires);
	}
	
}
