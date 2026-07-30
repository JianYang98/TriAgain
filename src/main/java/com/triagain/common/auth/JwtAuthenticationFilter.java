package com.triagain.common.auth;

import com.triagain.user.port.out.UserRepositoryPort;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private static final String AUTHORIZATION_HEADER = "Authorization";
	private static final String BEARER_PREFIX = "Bearer ";

	private final JwtProvider jwtProvider;
	private final UserRepositoryPort userRepositoryPort;

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
									FilterChain filterChain) throws ServletException, IOException {
		// 1) Authorization 헤더에서 토큰 꺼냄
		String token = resolveToken(request);

		if (token != null && jwtProvider.validateToken(token)
				&& "access".equals(jwtProvider.getTokenType(token))) {
			// 2) 토큰 검증 + userId 추출
			String userId = jwtProvider.getUserId(token);

			// 3) tokenVersion 검증 — DB와 불일치 시 인증 거부 (탈퇴/로그아웃 즉시 반영)
			int tokenVersion = jwtProvider.getTokenVersion(token);
			Optional<Integer> dbVersion = userRepositoryPort.findTokenVersionById(userId);
			if (dbVersion.isPresent() && dbVersion.get() == tokenVersion) {
				// 4) SecurityContext에 저장
				UsernamePasswordAuthenticationToken authentication =
						new UsernamePasswordAuthenticationToken(userId, null, List.of());
				SecurityContextHolder.getContext().setAuthentication(authentication);
			}
		}
		// 5) 다음 필터로 넘기기
		filterChain.doFilter(request, response);
	}

	private String resolveToken(HttpServletRequest request) {
		String bearer = request.getHeader(AUTHORIZATION_HEADER);
		if (bearer != null && bearer.startsWith(BEARER_PREFIX)) {
			return bearer.substring(BEARER_PREFIX.length());
		}
		return null;
	}
}
