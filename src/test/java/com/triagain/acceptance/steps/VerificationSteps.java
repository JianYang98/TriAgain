package com.triagain.acceptance.steps;

import com.triagain.acceptance.ScenarioContext;
import com.triagain.acceptance.adapter.VerificationTestAdapter;
import com.triagain.common.util.IdGenerator;
import com.triagain.crew.domain.model.Challenge;
import com.triagain.crew.domain.model.Crew;
import com.triagain.crew.domain.vo.VerificationType;
import com.triagain.crew.port.out.ChallengeRepositoryPort;
import com.triagain.crew.port.out.CrewRepositoryPort;
import com.triagain.verification.domain.model.UploadSession;
import com.triagain.verification.domain.model.Verification;
import com.triagain.verification.domain.vo.ReviewStatus;
import com.triagain.verification.domain.vo.UploadSessionStatus;
import com.triagain.verification.domain.vo.VerificationStatus;
import com.triagain.verification.port.out.UploadSessionRepositoryPort;
import com.triagain.verification.port.out.VerificationRepositoryPort;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.Before;
import io.cucumber.java.ko.그리고;
import io.cucumber.java.ko.만일;
import io.cucumber.java.ko.조건;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class VerificationSteps {

    @LocalServerPort
    private int port;

    @Autowired
    private ScenarioContext scenarioContext;

    @Autowired
    private CrewRepositoryPort crewRepositoryPort;

    @Autowired
    private ChallengeRepositoryPort challengeRepositoryPort;

    @Autowired
    private UploadSessionRepositoryPort uploadSessionRepositoryPort;

    @Autowired
    private VerificationRepositoryPort verificationRepositoryPort;

    private VerificationTestAdapter verificationAdapter;
    private int initialCompletedDays;

    @Before
    public void setUp() {
        verificationAdapter = new VerificationTestAdapter(port);
    }

    // ===== 조건 (Given) =====

    @조건("크루 {string}의 인증방식이 {string}이다")
    public void 크루의_인증방식이_이다(String crewName, String type) {
        String crewId = scenarioContext.getCrewIdByName(crewName);
        Crew crew = crewRepositoryPort.findById(crewId).orElseThrow();
        Crew updated = Crew.of(
                crew.getId(), crew.getCreatorId(), crew.getName(), crew.getGoal(),
                VerificationType.valueOf(type), crew.getMaxMembers(),
                crew.getCurrentMembers(), crew.getStatus(), crew.getStartDate(),
                crew.getEndDate(), crew.isAllowLateJoin(), crew.getInviteCode(),
                crew.getCreatedAt(), crew.getMembers()
        );
        crewRepositoryPort.save(updated);
    }

    @조건("업로드 세션이 완료되었다")
    public void 업로드_세션이_완료되었다() {
        UploadSession session = createAndSaveSession(UploadSessionStatus.COMPLETED, LocalDateTime.now());
        scenarioContext.setUploadSessionId(session.getId());
    }

    @조건("{string}이 오늘 이미 인증을 완료했다")
    public void 사용자가_오늘_이미_인증을_완료했다(String userId) {
        String crewId = scenarioContext.getCrewIdByName("운동 크루");
        String challengeId = scenarioContext.getChallengeId();

        Verification verification = Verification.of(
                IdGenerator.generate("VRFY"), challengeId, userId, crewId,
                null, null, "오늘의 인증입니다",
                VerificationStatus.APPROVED, 0, LocalDate.now(),
                1, ReviewStatus.NOT_REQUIRED, LocalDateTime.now()
        );
        verificationRepositoryPort.save(verification);
    }

    @조건("인증 마감 시간이 지났다")
    public void 인증_마감_시간이_지났다() {
        String challengeId = scenarioContext.getChallengeId();
        Challenge challenge = challengeRepositoryPort.findById(challengeId).orElseThrow();

        Challenge updated = Challenge.of(
                challenge.getId(), challenge.getUserId(), challenge.getCrewId(),
                challenge.getCycleNumber(), challenge.getTargetDays(), challenge.getCompletedDays(),
                challenge.getStatus(), challenge.getStartDate(),
                LocalDateTime.now().minusHours(1),
                challenge.getCreatedAt()
        );
        challengeRepositoryPort.save(updated);
    }

    @조건("업로드 세션이 생성되어 PENDING 상태이다")
    public void 업로드_세션이_PENDING_상태이다() {
        UploadSession session = createAndSaveSession(UploadSessionStatus.PENDING, LocalDateTime.now());
        scenarioContext.setUploadSessionId(session.getId());
    }

    @조건("업로드 세션이 마감 직전에 요청되었다")
    public void 업로드_세션이_마감_직전에_요청되었다() {
        String challengeId = scenarioContext.getChallengeId();
        Challenge challenge = challengeRepositoryPort.findById(challengeId).orElseThrow();

        LocalDateTime requestedAt = challenge.getDeadline().minusMinutes(1);
        UploadSession session = createAndSaveSession(UploadSessionStatus.COMPLETED, requestedAt);
        scenarioContext.setUploadSessionId(session.getId());
    }

    @조건("마감 시간 이후 5분 이내이다")
    public void 마감_시간_이후_5분_이내이다() {
        // no-op — requestedAt가 이미 deadline 전이므로 grace period 내 통과
    }

    @조건("업로드 세션 요청 시각이 마감 시간 이후 5분을 초과했다")
    public void 업로드_세션_요청_시각이_마감_시간_이후_5분을_초과했다() {
        String challengeId = scenarioContext.getChallengeId();
        Challenge challenge = challengeRepositoryPort.findById(challengeId).orElseThrow();

        LocalDateTime requestedAt = challenge.getDeadline().plusMinutes(6);
        UploadSession session = createAndSaveSession(UploadSessionStatus.COMPLETED, requestedAt);
        scenarioContext.setUploadSessionId(session.getId());
    }

    // ===== 만일 (When) =====

    @만일("다음 정보로 인증을 생성한다")
    public void 다음_정보로_인증을_생성한다(DataTable dataTable) {
        Map<String, String> data = dataTable.asMap(String.class, String.class);
        Map<String, Object> request = new HashMap<>();
        request.put("challengeId", scenarioContext.getChallengeId());

        if (data.containsKey("uploadSessionId")) {
            String sessionIdValue = data.get("uploadSessionId");
            if ("{uploadSessionId}".equals(sessionIdValue)) {
                request.put("uploadSessionId", scenarioContext.getUploadSessionId());
            } else {
                request.put("uploadSessionId", Long.parseLong(sessionIdValue));
            }
        }
        if (data.containsKey("textContent")) {
            request.put("textContent", data.get("textContent"));
        }

        recordInitialCompletedDays();

        String userId = scenarioContext.getUserId();
        ExtractableResponse<Response> response = verificationAdapter.createVerification(userId, request);
        scenarioContext.setResponse(response);
    }

    @만일("텍스트 없이 사진만으로 인증을 생성한다")
    public void 텍스트_없이_사진만으로_인증을_생성한다() {
        Map<String, Object> request = new HashMap<>();
        request.put("challengeId", scenarioContext.getChallengeId());
        request.put("uploadSessionId", scenarioContext.getUploadSessionId());

        String userId = scenarioContext.getUserId();
        ExtractableResponse<Response> response = verificationAdapter.createVerification(userId, request);
        scenarioContext.setResponse(response);
    }

    @만일("다시 인증을 생성한다")
    public void 다시_인증을_생성한다() {
        인증을_생성한다();
    }

    @만일("인증을 생성한다")
    public void 인증을_생성한다() {
        Map<String, Object> request = new HashMap<>();
        request.put("challengeId", scenarioContext.getChallengeId());

        Long uploadSessionId = scenarioContext.getUploadSessionId();
        if (uploadSessionId != null) {
            request.put("uploadSessionId", uploadSessionId);
        } else {
            request.put("textContent", "오늘의 인증입니다");
        }

        recordInitialCompletedDays();

        String userId = scenarioContext.getUserId();
        ExtractableResponse<Response> response = verificationAdapter.createVerification(userId, request);
        scenarioContext.setResponse(response);
    }

    @만일("사진 없이 텍스트만으로 인증을 생성한다")
    public void 사진_없이_텍스트만으로_인증을_생성한다() {
        Map<String, Object> request = new HashMap<>();
        request.put("challengeId", scenarioContext.getChallengeId());
        request.put("textContent", "텍스트만으로 인증합니다");

        String userId = scenarioContext.getUserId();
        ExtractableResponse<Response> response = verificationAdapter.createVerification(userId, request);
        scenarioContext.setResponse(response);
    }

    @만일("만료된 uploadSessionId로 인증을 생성한다")
    public void 만료된_uploadSessionId로_인증을_생성한다() {
        UploadSession session = createAndSaveSession(UploadSessionStatus.EXPIRED, LocalDateTime.now());

        Map<String, Object> request = new HashMap<>();
        request.put("challengeId", scenarioContext.getChallengeId());
        request.put("uploadSessionId", session.getId());

        String userId = scenarioContext.getUserId();
        ExtractableResponse<Response> response = verificationAdapter.createVerification(userId, request);
        scenarioContext.setResponse(response);
    }

    @만일("PENDING 상태의 uploadSessionId로 인증을 생성한다")
    public void PENDING_상태의_uploadSessionId로_인증을_생성한다() {
        Map<String, Object> request = new HashMap<>();
        request.put("challengeId", scenarioContext.getChallengeId());
        request.put("uploadSessionId", scenarioContext.getUploadSessionId());

        String userId = scenarioContext.getUserId();
        ExtractableResponse<Response> response = verificationAdapter.createVerification(userId, request);
        scenarioContext.setResponse(response);
    }

    // ===== 그리고/그러면 (Then) =====

    @그리고("인증 상태는 {string}이다")
    public void 인증_상태는_이다(String expected) {
        String actual = scenarioContext.getResponse().jsonPath().getString("data.status");
        assertThat(actual).isEqualTo(expected);
    }

    @그리고("챌린지 completed_days가 {int} 증가한다")
    public void 챌린지_completedDays가_N_증가한다(int increment) {
        String challengeId = scenarioContext.getChallengeId();
        Challenge challenge = challengeRepositoryPort.findById(challengeId).orElseThrow();
        assertThat(challenge.getCompletedDays()).isEqualTo(initialCompletedDays + increment);
    }

    // ===== Helper Methods =====

    private UploadSession createAndSaveSession(UploadSessionStatus status, LocalDateTime requestedAt) {
        String userId = scenarioContext.getUserId();
        UploadSession session = UploadSession.of(
                null, userId, "test-image-key-" + System.nanoTime() + ".jpg",
                "image/jpeg", status, requestedAt, LocalDateTime.now()
        );
        return uploadSessionRepositoryPort.save(session);
    }

    private void recordInitialCompletedDays() {
        String challengeId = scenarioContext.getChallengeId();
        if (challengeId != null) {
            challengeRepositoryPort.findById(challengeId)
                    .ifPresent(c -> initialCompletedDays = c.getCompletedDays());
        }
    }
}
