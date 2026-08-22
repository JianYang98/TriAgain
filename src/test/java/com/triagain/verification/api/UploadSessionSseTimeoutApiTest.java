package com.triagain.verification.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.triagain.e2e.E2eTestBase;
import com.triagain.verification.domain.model.UploadSession;
import com.triagain.verification.domain.vo.UploadSessionStatus;
import com.triagain.verification.port.out.UploadSessionRepositoryPort;

/**
 * 자연 SSE 타임아웃 관찰 — 완료 콜백 없이 서버측 타임아웃(단축값)이 그대로 발화할 때 클라이언트가
 * 실제로 무엇을 받는지 확인한다(revisions 사실 2: emitter.complete() → ASYNC 재디스패치 →
 * AuthorizationFilter 재평가 → 401 가능성). 프로퍼티가 달라 별도 컨텍스트로 뜬다 — 의도적으로
 * {@link UploadSessionSseApiTest}와 분리한 클래스다.
 */
class UploadSessionSseTimeoutApiTest extends E2eTestBase {

	private static final long SHORT_TIMEOUT_MS = 300L;

	@Autowired
	private UploadSessionRepositoryPort uploadSessionRepositoryPort;

	private final HttpClient httpClient = HttpClient.newHttpClient();

	@DynamicPropertySource
	static void shortSseTimeout(DynamicPropertyRegistry registry) {
		registry.add("triagain.verification.sse-timeout-ms", () -> SHORT_TIMEOUT_MS);
	}

	@Test
	@DisplayName("관찰: 완료 콜백 없이 자연 타임아웃되면 클라이언트가 받는 응답")
	void naturalTimeout_observeClientResponse() throws Exception {
		UploadSession session = createSession("owner", UploadSessionStatus.PENDING);

		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create("http://localhost:" + port + "/upload-sessions/" + session.getId() + "/events"))
				.header("X-User-Id", "owner")
				.GET()
				.build();

		HttpResponse<Stream<String>> response = httpClient
				.sendAsync(request, BodyHandlers.ofLines())
				.get(5, TimeUnit.SECONDS);

		List<String> lines = response.body().toList();

		// 실측(2026-08-23): SecurityConfig에 dispatcherTypeMatchers(ASYNC, ERROR) permitAll 적용 전에는
		// emitter.complete() → ASYNC 재디스패치 → AuthorizationFilter 재평가로 401 A003이 관측됐다.
		// 적용 후에는 정상 종료(200, text/event-stream, 빈 본문) — AsyncRequestTimeoutException·500 없음.
		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.headers().firstValue("content-type").orElse("")).contains("text/event-stream");
		assertThat(lines).isEmpty();
	}

	private UploadSession createSession(String userId, UploadSessionStatus status) {
		LocalDateTime now = LocalDateTime.now();
		UploadSession session = UploadSession.of(
				null, userId, null, "e2e-sse-timeout-" + System.nanoTime() + ".jpg", "image/jpeg", status, now, now);
		return uploadSessionRepositoryPort.save(session);
	}
}
