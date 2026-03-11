package com.triagain.common.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class InternalApiKeyFilterTest {

    private static final String VALID_KEY = "test-secret-key";
    private final InternalApiKeyFilter filter = new InternalApiKeyFilter(VALID_KEY);
    private final FilterChain filterChain = mock(FilterChain.class);

    @Test
    @DisplayName("유효한 API Key → 요청 통과")
    void validApiKey_passes() throws ServletException, IOException {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/internal/upload-sessions/complete");
        request.addHeader("X-Internal-Api-Key", VALID_KEY);
        MockHttpServletResponse response = new MockHttpServletResponse();

        // When
        filter.doFilter(request, response, filterChain);

        // Then
        verify(filterChain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("잘못된 API Key → 403 Forbidden")
    void invalidApiKey_returns403() throws ServletException, IOException {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/internal/upload-sessions/complete");
        request.addHeader("X-Internal-Api-Key", "wrong-key");
        MockHttpServletResponse response = new MockHttpServletResponse();

        // When
        filter.doFilter(request, response, filterChain);

        // Then
        verify(filterChain, never()).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("API Key 헤더 없음 → 403 Forbidden")
    void missingApiKey_returns403() throws ServletException, IOException {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/internal/upload-sessions/complete");
        MockHttpServletResponse response = new MockHttpServletResponse();

        // When
        filter.doFilter(request, response, filterChain);

        // Then
        verify(filterChain, never()).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("/internal/ 아닌 경로 → 필터 스킵")
    void nonInternalPath_skipsFilter() throws ServletException, IOException {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/auth/login");
        MockHttpServletResponse response = new MockHttpServletResponse();

        // When
        filter.doFilter(request, response, filterChain);

        // Then — API Key 없어도 통과 (필터 자체를 건너뜀)
        verify(filterChain).doFilter(request, response);
    }
}
