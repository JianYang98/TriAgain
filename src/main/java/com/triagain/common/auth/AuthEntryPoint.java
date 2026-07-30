package com.triagain.common.auth;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.triagain.common.exception.ErrorCode;
import com.triagain.common.response.ApiResponse;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class AuthEntryPoint implements AuthenticationEntryPoint {

	private final ObjectMapper objectMapper;

	/** 인증 실패 시 JSON 401 응답 */
	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response,
						AuthenticationException authException) throws IOException {
		response.setContentType("application/json;charset=UTF-8");
		response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);

		ApiResponse<Void> body = ApiResponse.fail(ErrorCode.UNAUTHORIZED, "인증이 필요합니다.");
		response.getWriter().write(objectMapper.writeValueAsString(body));
	}
}
