package com.triagain.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import com.triagain.common.auth.JwtProvider;

@Configuration
public class JwtConfig {

	/** prod 프로필용 JwtProvider */
	@Bean("prodJwtProvider")
	@Profile("prod")
	public JwtProvider serviceJwtProvider(
			@Value("${jwt.secret}") String secret,
			@Value("${jwt.access-token-expiration}") long accessTokenExpiration,
			@Value("${jwt.refresh-token-expiration}") long refreshTokenExpiration
	) {
		return new JwtProvider(secret, accessTokenExpiration, refreshTokenExpiration);
	}

	/** prod 외 프로필용 JwtProvider */
	@Bean("testJwtProvider")
	@Profile("!prod")
	public JwtProvider nonProdJwtProvider(
			@Value("${jwt.secret}") String secret,
			@Value("${jwt.access-token-expiration}") long accessExpiration,
			@Value("${jwt.refresh-token-expiration}") long refreshExpiration
	) {
		return new JwtProvider(secret, accessExpiration, refreshExpiration);
	}
}
