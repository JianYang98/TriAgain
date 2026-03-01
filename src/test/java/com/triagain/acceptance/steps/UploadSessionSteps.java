package com.triagain.acceptance.steps;

import com.triagain.acceptance.ScenarioContext;
import com.triagain.acceptance.adapter.CapturingSsePort;
import com.triagain.acceptance.adapter.UploadSessionTestAdapter;
import com.triagain.verification.application.ExpireUploadSessionScheduler;
import com.triagain.verification.domain.model.UploadSession;
import com.triagain.verification.domain.vo.UploadSessionStatus;
import com.triagain.verification.port.out.UploadSessionRepositoryPort;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.Before;
import io.cucumber.java.ko.그리고;
import io.cucumber.java.ko.만일;
import io.cucumber.java.ko.조건;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class UploadSessionSteps {

    @LocalServerPort
    private int port;

    @Autowired
    private ScenarioContext scenarioContext;

    @Autowired
    private UploadSessionRepositoryPort uploadSessionRepositoryPort;

    @Autowired
    private ExpireUploadSessionScheduler expireScheduler;

    @Autowired
    private CapturingSsePort capturingSsePort;

    private UploadSessionTestAdapter uploadSessionAdapter;

    @Before
    public void setUp() {
        uploadSessionAdapter = new UploadSessionTestAdapter(port);
        capturingSsePort.clear();
    }

    // ===== 만일 (When) =====

    @만일("다음 정보로 업로드 세션을 생성한다")
    public void 다음_정보로_업로드_세션을_생성한다(DataTable dataTable) {
        Map<String, String> data = dataTable.asMap(String.class, String.class);
        Map<String, Object> request = new HashMap<>();
        request.put("challengeId", scenarioContext.getChallengeId());
        request.put("fileName", data.get("fileName"));
        request.put("fileType", data.get("fileType"));
        request.put("fileSize", Long.parseLong(data.get("fileSize")));

        scenarioContext.setResponse(
                uploadSessionAdapter.createUploadSession(scenarioContext.getUserId(), request));
    }

    @만일("파일 타입 {string}로 업로드 세션을 생성한다")
    public void 파일_타입으로_업로드_세션을_생성한다(String fileType) {
        Map<String, Object> request = new HashMap<>();
        request.put("challengeId", scenarioContext.getChallengeId());
        request.put("fileName", "test.jpg");
        request.put("fileType", fileType);
        request.put("fileSize", 1024L);

        scenarioContext.setResponse(
                uploadSessionAdapter.createUploadSession(scenarioContext.getUserId(), request));
    }

    @만일("파일 크기 {long}으로 업로드 세션을 생성한다")
    public void 파일_크기로_업로드_세션을_생성한다(long fileSize) {
        Map<String, Object> request = new HashMap<>();
        request.put("challengeId", scenarioContext.getChallengeId());
        request.put("fileName", "test.jpg");
        request.put("fileType", "image/jpeg");
        request.put("fileSize", fileSize);

        scenarioContext.setResponse(
                uploadSessionAdapter.createUploadSession(scenarioContext.getUserId(), request));
    }

    @만일("업로드 세션을 생성한다")
    public void 업로드_세션을_생성한다() {
        Map<String, Object> request = new HashMap<>();
        request.put("challengeId", scenarioContext.getChallengeId());
        request.put("fileName", "test.jpg");
        request.put("fileType", "image/jpeg");
        request.put("fileSize", 1024L);

        scenarioContext.setResponse(
                uploadSessionAdapter.createUploadSession(scenarioContext.getUserId(), request));
    }

    @만일("만료 스케줄러가 실행된다")
    public void 만료_스케줄러가_실행된다() {
        expireScheduler.expirePendingSessions();
    }

    @만일("Lambda가 업로드 완료를 알린다")
    public void Lambda가_업로드_완료를_알린다() {
        Long sessionId = scenarioContext.getUploadSessionId();
        scenarioContext.setResponse(uploadSessionAdapter.completeUploadSession(sessionId));
    }

    // ===== 조건 (Given) =====

    @조건("업로드 세션이 {int}분 전에 생성되었다")
    public void 업로드_세션이_N분_전에_생성되었다(int minutes) {
        LocalDateTime createdAt = LocalDateTime.now().minusMinutes(minutes).minusSeconds(1);
        UploadSession session = UploadSession.of(
                null, scenarioContext.getUserId(),
                "test-image-key-" + System.nanoTime() + ".jpg",
                "image/jpeg", UploadSessionStatus.PENDING, createdAt, createdAt
        );
        UploadSession saved = uploadSessionRepositoryPort.save(session);
        scenarioContext.setUploadSessionId(saved.getId());
    }

    @조건("세션 상태가 {string}이다")
    public void 세션_상태가_이다(String status) {
        // no-op — 이미 해당 상태로 저장됨
    }

    @조건("업로드 세션이 이미 {string} 상태이다")
    public void 업로드_세션이_이미_상태이다(String status) {
        UploadSession session = UploadSession.of(
                null, scenarioContext.getUserId(),
                "test-image-key-" + System.nanoTime() + ".jpg",
                "image/jpeg", UploadSessionStatus.valueOf(status),
                LocalDateTime.now(), LocalDateTime.now()
        );
        UploadSession saved = uploadSessionRepositoryPort.save(session);
        scenarioContext.setUploadSessionId(saved.getId());
    }

    @조건("SSE로 세션 이벤트를 구독 중이다")
    public void SSE로_세션_이벤트를_구독_중이다() {
        // no-op — CapturingSsePort가 자동 캡처
    }

    // ===== 그리고/그러면 (Then) =====

    @그리고("세션 상태는 {string}이다")
    public void 세션_상태는_이다(String expectedStatus) {
        Long sessionId = scenarioContext.getUploadSessionId();
        UploadSession session = uploadSessionRepositoryPort.findById(sessionId).orElseThrow();
        assertThat(session.getStatus().name()).isEqualTo(expectedStatus);
    }

    @그리고("SSE로 {string} 이벤트를 수신한다")
    public void SSE로_이벤트를_수신한다(String expectedEvent) {
        Long sessionId = scenarioContext.getUploadSessionId();
        List<String> events = capturingSsePort.getCapturedEvents(sessionId);
        assertThat(events).contains(expectedEvent);
    }
}
