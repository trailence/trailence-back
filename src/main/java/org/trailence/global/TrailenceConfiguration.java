package org.trailence.global;

import java.security.SecureRandom;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity.CsrfSpec;
import org.springframework.security.config.web.server.ServerHttpSecurity.FormLoginSpec;
import org.springframework.security.config.web.server.ServerHttpSecurity.HttpBasicSpec;
import org.springframework.security.config.web.server.ServerHttpSecurity.LogoutSpec;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.web.server.session.WebSessionManager;
import org.trailence.global.rest.JwtFilter;

import reactor.core.publisher.Mono;

@Configuration
@EnableReactiveMethodSecurity
public class TrailenceConfiguration {

	@Bean
	WebSessionManager webSessionManager() {
		return exchange -> Mono.empty();
	}
	
	@Bean
	SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http, ReactiveAuthenticationManager authManager) {
		return http
		.csrf(CsrfSpec::disable)
		.formLogin(FormLoginSpec::disable)
		.httpBasic(HttpBasicSpec::disable)
		.logout(LogoutSpec::disable)
		.authorizeExchange(auth -> auth
			.pathMatchers(HttpMethod.GET, "/actuator/**").permitAll()
			.pathMatchers(HttpMethod.POST, "/api/auth/v1/login").permitAll()
			.pathMatchers(HttpMethod.POST, "/api/auth/v1/share").permitAll()
			.pathMatchers(HttpMethod.POST, "/api/auth/v1/init_renew").permitAll()
			.pathMatchers(HttpMethod.POST, "/api/auth/v1/renew").permitAll()
			.pathMatchers(HttpMethod.GET, "/api/ping").permitAll()
			.pathMatchers(HttpMethod.DELETE, "/api/user/v1/changePassword").permitAll()
			.pathMatchers("/**").authenticated()
		)
		.addFilterBefore(new JwtFilter(authManager), SecurityWebFiltersOrder.HTTP_BASIC)
		.securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
		.exceptionHandling(handling ->
			handling.authenticationEntryPoint(
				(exchange, error) -> {
					var response = exchange.getResponse();
				    response.setStatusCode(HttpStatus.UNAUTHORIZED);
				    exchange.mutate().response(response);
					return Mono.empty();
				}
			)
		)
		.build();
	}
	
	@Bean
	SecureRandom secureRandom() {
		return new SecureRandom();
	}
	
}
