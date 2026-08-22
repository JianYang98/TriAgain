package com.triagain.verification.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.triagain.e2e.E2eTestBase;
import com.triagain.verification.domain.model.UploadSession;
import com.triagain.verification.domain.vo.UploadSessionStatus;
import com.triagain.verification.infra.SseEmitterAdapter;
import com.triagain.verification.port.out.SsePort;
import com.triagain.verification.port.out.UploadSessionRepositoryPort;

/**
 * SSE 구독 인증·소유권 검증 + 등록 '후' 재조회 — 실제 HTTP·실제 SseEmitterAdapter로 검증한다(⓪ 격리가 선행 조건, G-3).
 * JDK {@link HttpClient}로 연결한다 — RestAssured는 스트림을 서버가 닫을 때까지 블록해 각 스트림 케이스가
 * emitter 타임아웃(60초)만큼 걸린다(impl-guards ⒝ 참조).
 */
class UploadSessionSseApiTest extends E2eTestBase {

	@Autowired
	private SsePort ssePort;

	@Autowired
	private UploadSessionRepositoryPort uploadSessionRepositoryPort;

	private final HttpClient httpClient = HttpClient.newHttpClient();

	@Test
	@DisplayName("SSE 포트는 실제 SseEmitterAdapter로 주입된다 — 가짜 빈 위에서 초록 나는 것 방지(G-3)")
	void ssePort_isRealAdapter() {
		assertThat(ssePort).isInstanceOf(SseEmitterAdapter.class);
	}

	@Test
	@DisplayName("A1: 토큰 없이 구독하면 401 A003을 받는다")
	void subscribe_withoutAuth_returns401() {
		var response = givenRequest()
				.when().get("/upload-sessions/{id}/events", 1L)
				.then().extract();

		assertThat(response.statusCode()).isEqualTo(401);
		assertThat(response.jsonPath().getString("error.code")).isEqualTo("A003");
	}

	@Test
	@DisplayName("A2: 타인 소유 세션을 구독하면 404 V004를 받는다")
	void subscribe_notOwner_returns404() {
		UploadSession session = createSession("owner", UploadSessionStatus.PENDING);

		var response = givenAuthRequest("intruder")
				.when().get("/upload-sessions/{id}/events", session.getId())
				.then().extract();

		assertThat(response.statusCode()).isEqualTo(404);
		assertThat(response.jsonPath().getString("error.code")).isEqualTo("V004");
	}

	@Test
	@DisplayName("A3: 본인 세션을 구독하면 200 text/event-stream을 받는다")
	void subscribe_owner_returns200EventStream() throws Exception {
		// 실측: PENDING 세션은 emitter.send()가 한 번도 안 불리면 헤더조차 클라이언트에 flush되지 않는다
		// (Tomcat/Spring 비동기 SSE — 응답이 첫 write와 함께 커밋된다). 그래서 구독 직후 완료 콜백으로
		// 최초 write를 발생시켜 200 + text/event-stream을 관찰한다. 프레임 내용 자체는 T1·T2가 검증한다.
		UploadSession session = createSession("owner", UploadSessionStatus.PENDING);
		CompletableFuture<HttpResponse<Stream<String>>> future = openStream("owner", session.getId());

		try {
			completeSession(session);
			HttpResponse<Stream<String>> response = future.get(5, TimeUnit.SECONDS);
			assertThat(response.statusCode()).isEqualTo(200);
			assertThat(response.headers().firstValue("content-type").orElse(""))
					.contains("text/event-stream");
		} finally {
			future.cancel(true);
		}
	}

	@Test
	@DisplayName("T1: 이미 COMPLETED인 세션을 구독하면 즉시 완료 이벤트를 1회 받는다 — R1 판정 지점")
	void subscribe_alreadyCompleted_immediatelyReceivesEventOnce() throws Exception {
		UploadSession session = createSession("owner", UploadSessionStatus.COMPLETED);
		CompletableFuture<HttpResponse<Stream<String>>> future = openStream("owner", session.getId());

		try {
			HttpResponse<Stream<String>> response = future.get(5, TimeUnit.SECONDS);
			assertThat(response.statusCode()).isEqualTo(200);

			List<String> lines = readFirstFrame(response);
			assertThat(lines).containsExactly("event:upload-complete", "data:COMPLETED");
		} finally {
			future.cancel(true);
		}
	}

	@Test
	@DisplayName("T2: 구독 성립 후 완료 콜백이 오면 완료 이벤트를 1회 받는다")
	void subscribe_thenComplete_receivesEventOnce() throws Exception {
		UploadSession session = createSession("owner", UploadSessionStatus.PENDING);
		CompletableFuture<HttpResponse<Stream<String>>> future = openStream("owner", session.getId());

		try {
			// A3와 같은 이유로 헤더를 먼저 기다릴 수 없다 — 완료 콜백을 먼저 보낸다.
			// 구독 등록과 완료 콜백 중 어느 쪽이 먼저 도착해도 ③의 등록 '후' 재조회(G-2)가 배달을 보장한다:
			// 콜백이 먼저면 재조회가 즉시 잡고(T1과 동일 경로), 구독이 먼저면 afterCommit이 등록된 emitter를 찾는다.
			// 정확한 순서 분기는 ⒞(SubscribeUploadSessionServiceTest)가 모의 포트로 결정적으로 검증한다.
			completeSession(session);
			HttpResponse<Stream<String>> response = future.get(5, TimeUnit.SECONDS);
			assertThat(response.statusCode()).isEqualTo(200);

			List<String> lines = readFirstFrame(response);
			assertThat(lines).containsExactly("event:upload-complete", "data:COMPLETED");
		} finally {
			future.cancel(true);
		}
	}

	/** 세션 픽스처 — 크루/습관 무관 단독 세션(SSE 인증·소유권 검증만 확인하면 되므로 crewId·habitId는 null) */
	private UploadSession createSession(String userId, UploadSessionStatus status) {
		LocalDateTime now = LocalDateTime.now();
		UploadSession session = UploadSession.of(
				null, userId, null, "e2e-sse-" + System.nanoTime() + ".jpg", "image/jpeg", status, now, now);
		return uploadSessionRepositoryPort.save(session);
	}

	/** Lambda 콜백 — PUT /internal/upload-sessions/complete?imageKey=... */
	private void completeSession(UploadSession session) {
		givenRequest()
				.queryParam("imageKey", session.getImageKey())
				.when().put("/internal/upload-sessions/complete")
				.then().statusCode(200);
	}

	private CompletableFuture<HttpResponse<Stream<String>>> openStream(String userId, Long id) {
		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create("http://localhost:" + port + "/upload-sessions/" + id + "/events"))
				.header("X-User-Id", userId)
				.GET()
				.build();
		return httpClient.sendAsync(request, BodyHandlers.ofLines());
	}

	/** 스트림에서 첫 두 줄(event:/data:)을 5초 안에 읽는다 — 못 읽으면 TimeoutException으로 실패한다 */
	private List<String> readFirstFrame(HttpResponse<Stream<String>> response) throws Exception {
		return CompletableFuture.supplyAsync(() -> response.body().limit(2).toList())
				.get(5, TimeUnit.SECONDS);
	}
}
