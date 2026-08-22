package com.triagain.common.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import lombok.RequiredArgsConstructor;

/** 개발/테스트 환경 보안 설정 — JWT 또는 X-User-Id 헤더로 인증 */
@Configuration
@EnableWebSecurity
@Profile("!prod")
@RequiredArgsConstructor
public class DevSecurityConfig {

	private final JwtAuthenticationFilter jwtAuthenticationFilter;
	private final AuthEntryPoint authEntryPoint;

	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
		return http
				.csrf(csrf -> csrf.disable())
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.exceptionHandling(exception -> exception.authenticationEntryPoint(authEntryPoint))
				.authorizeHttpRequests(auth -> auth
						.requestMatchers("/auth/**").permitAll()
						.requestMatchers("/health", "/actuator/health").permitAll()
						.requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
						.requestMatchers("/internal/**").permitAll()
						.requestMatchers("/crews/search").permitAll()
						.requestMatchers("/invite/**", "/images/**", "/css/**", "/feedback").permitAll()
						.anyRequest().authenticated()
				)
				.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
				.addFilterAfter(new XUserIdAuthenticationFilter(), JwtAuthenticationFilter.class)
				.build();
	}
}
