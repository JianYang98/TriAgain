package com.triagain.acceptance.steps;

import com.triagain.acceptance.ScenarioContext;
import com.triagain.acceptance.adapter.CrewTestAdapter;
import com.triagain.acceptance.adapter.MyVerificationsTestAdapter;
import com.triagain.common.util.IdGenerator;
import com.triagain.crew.api.CreateCrewRequest;
import com.triagain.crew.domain.model.Challenge;
import com.triagain.crew.domain.model.Crew;
import com.triagain.crew.domain.vo.ChallengeStatus;
import com.triagain.crew.domain.vo.VerificationType;
import com.triagain.crew.port.out.ChallengeRepositoryPort;
import com.triagain.crew.port.out.CrewRepositoryPort;
import com.triagain.verification.domain.model.Verification;
import com.triagain.verification.domain.vo.ReviewStatus;
import com.triagain.verification.domain.vo.VerificationStatus;
import com.triagain.verification.port.out.VerificationRepositoryPort;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class MyVerificationsSteps {

    @LocalServerPort
    private int port;

    @Autowired
    private ScenarioContext scenarioContext;

    @Autowired
    private ChallengeRepositoryPort challengeRepositoryPort;

    @Autowired
    private VerificationRepositoryPort verificationRepositoryPort;

    @Autowired
    private CrewRepositoryPort crewRepositoryPort;

    private MyVerificationsTestAdapter myVerificationsAdapter;
    private CrewTestAdapter crewAdapter;

    @Before
    public void setUp() {
        myVerificationsAdapter = new MyVerificationsTestAdapter(port);
        crewAdapter = new CrewTestAdapter(port);
    }

    // ===== 조건 (Given) =====

    @조건("{string}이 크루 {string}에 {int}일 연속 인증을 완료했다")
    public void 사용자가_크루에_N일_연속_인증을_완료했다(String userId, String crewName, int days) {
        String crewId = scenarioContext.getCrewIdByName(crewName);
        adjustCrewStartDate(crewId, days);
        String challengeId = scenarioContext.getChallengeId();

        for (int i = days - 1; i >= 0; i--) {
            LocalDate targetDate = LocalDate.now().minusDays(i);
            Verification verification = Verification.of(
                    IdGenerator.generate("VRFY"), challengeId, userId, crewId,
                    null, null, (days - i) + "일차 인증",
                    VerificationStatus.APPROVED, 0, targetDate,
                    1, ReviewStatus.NOT_REQUIRED, LocalDateTime.now().minusDays(i)
            );
            verificationRepositoryPort.save(verification);
        }
    }

    @조건("{string}이 크루 {string}에 성공한 챌린지가 {int}개 있다")
    public void 사용자가_크루에_성공한_챌린지가_N개_있다(String userId, String crewName, int count) {
        String crewId = scenarioContext.getCrewIdByName(crewName);

        for (int i = 0; i < count; i++) {
            Challenge challenge = Challenge.of(
                    IdGenerator.generate("CHAL"), userId, crewId, i + 1,
                    3, 3, ChallengeStatus.SUCCESS,
                    LocalDate.now().minusDays((i + 1) * 3L), LocalDateTime.now(), LocalDateTime.now()
            );
            challengeRepositoryPort.save(challenge);
        }
    }

    @조건("{string}이 크루 {string}에 비연속 인증을 완료했다")
    public void 사용자가_크루에_비연속_인증을_완료했다(String userId, String crewName) {
        String crewId = scenarioContext.getCrewIdByName(crewName);
        adjustCrewStartDate(crewId, 7);
        String challengeId = scenarioContext.getChallengeId();

        // 비연속: [오늘-6, 오늘-5, 오늘-4, 오늘-1, 오늘] → streak = 2 (최근 연속 구간)
        List<Integer> daysAgo = List.of(6, 5, 4, 1, 0);
        for (int d : daysAgo) {
            LocalDate targetDate = LocalDate.now().minusDays(d);
            Verification verification = Verification.of(
                    IdGenerator.generate("VRFY"), challengeId, userId, crewId,
                    null, null, "인증",
                    VerificationStatus.APPROVED, 0, targetDate,
                    1, ReviewStatus.NOT_REQUIRED, LocalDateTime.now().minusDays(d)
            );
            verificationRepositoryPort.save(verification);
        }
    }

    // ===== 만일 (When) =====

    @만일("{string}이 크루 {string}의 내 인증 현황을 조회한다")
    public void 사용자가_크루의_내_인증_현황을_조회한다(String userId, String crewName) {
        String crewId = scenarioContext.getCrewIdByName(crewName);
        ExtractableResponse<Response> response = myVerificationsAdapter.getMyVerifications(userId, crewId);
        scenarioContext.setResponse(response);
    }

    @만일("기간이 {int}일인 크루를 생성한다")
    public void 기간이_N일인_크루를_생성한다(int days) {
        CreateCrewRequest request = new CreateCrewRequest(
                "테스트 크루", "테스트 목표", VerificationType.TEXT,
                10, LocalDate.now().plusDays(1), LocalDate.now().plusDays(1 + days),
                true, null
        );
        ExtractableResponse<Response> response = crewAdapter.createCrew(scenarioContext.getUserId(), request);
        scenarioContext.setResponse(response);
    }

    // ===== 그리고/그러면 (Then) =====

    @그리고("verifiedDates 개수는 {int}이다")
    public void verifiedDates_개수는_N이다(int expected) {
        List<?> dates = scenarioContext.getResponse().jsonPath().getList("data.verifiedDates");
        assertThat(dates).hasSize(expected);
    }

    @그리고("streakCount는 {int}이다")
    public void streakCount는_N이다(int expected) {
        int actual = scenarioContext.getResponse().jsonPath().getInt("data.streakCount");
        assertThat(actual).isEqualTo(expected);
    }

    @그리고("completedChallenges는 {int}이다")
    public void completedChallenges는_N이다(int expected) {
        int actual = scenarioContext.getResponse().jsonPath().getInt("data.completedChallenges");
        assertThat(actual).isEqualTo(expected);
    }

    @그리고("myProgress의 targetDays는 {int}이다")
    public void myProgress의_targetDays는_이다(int expected) {
        int actual = scenarioContext.getResponse().jsonPath().getInt("data.myProgress.targetDays");
        assertThat(actual).isEqualTo(expected);
    }

    // ===== Helper Methods =====

    /** 크루 시작일을 과거로 조정 — 인증 날짜가 크루 기간 내에 포함되도록 */
    private void adjustCrewStartDate(String crewId, int daysNeeded) {
        Crew crew = crewRepositoryPort.findById(crewId).orElseThrow();
        Crew updated = Crew.of(
                crew.getId(), crew.getCreatorId(), crew.getName(), crew.getGoal(),
                crew.getVerificationType(), crew.getMaxMembers(),
                crew.getCurrentMembers(), crew.getStatus(),
                LocalDate.now().minusDays(daysNeeded), crew.getEndDate(),
                crew.isAllowLateJoin(), crew.getInviteCode(),
                crew.getCreatedAt(), crew.getDeadlineTime(), crew.getMembers()
        );
        crewRepositoryPort.save(updated);
    }
}
