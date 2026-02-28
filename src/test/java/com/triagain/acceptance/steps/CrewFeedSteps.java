package com.triagain.acceptance.steps;

import com.triagain.acceptance.ScenarioContext;
import com.triagain.acceptance.adapter.FeedTestAdapter;
import com.triagain.common.util.IdGenerator;
import com.triagain.crew.domain.model.Challenge;
import com.triagain.crew.domain.model.Crew;
import com.triagain.crew.domain.model.CrewMember;
import com.triagain.crew.domain.vo.ChallengeStatus;
import com.triagain.crew.domain.vo.CrewStatus;
import com.triagain.crew.domain.vo.VerificationType;
import com.triagain.crew.port.out.ChallengeRepositoryPort;
import com.triagain.crew.port.out.CrewRepositoryPort;
import com.triagain.user.domain.model.User;
import com.triagain.user.port.out.UserRepositoryPort;
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
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class CrewFeedSteps {

    @LocalServerPort
    private int port;

    @Autowired
    private ScenarioContext scenarioContext;

    @Autowired
    private UserRepositoryPort userRepositoryPort;

    @Autowired
    private CrewRepositoryPort crewRepositoryPort;

    @Autowired
    private ChallengeRepositoryPort challengeRepositoryPort;

    @Autowired
    private VerificationRepositoryPort verificationRepositoryPort;

    private FeedTestAdapter feedAdapter;

    private final Set<String> savedUserIds = new HashSet<>();

    @Before
    public void setUp() {
        feedAdapter = new FeedTestAdapter(port);
        savedUserIds.clear();
    }

    // ===== 조건 (Given) =====

    @조건("사용자 {string}이 크루 {string}에 참여 중이다")
    public void 사용자가_크루에_참여_중이다(String userId, String crewName) {
        ensureUserExists(userId);
        scenarioContext.setUserId(userId);

        String crewId = scenarioContext.getCrewIdByName(crewName);
        if (crewId == null) {
            crewId = createActiveCrew(crewName, userId);
            scenarioContext.putCrewId(crewName, crewId);
        } else {
            crewRepositoryPort.saveMember(CrewMember.createMember(userId, crewId));
        }
        ensureChallengeExists(userId, crewId);
    }

    @조건("크루 {string}가 활성 상태이다")
    public void 크루가_활성_상태이다(String crewName) {
        // 이미 ACTIVE로 생성됨 — no-op
    }

    @조건("{string}가 크루 {string}에 참여 중이다")
    public void 다른_사용자가_크루에_참여_중이다(String userId, String crewName) {
        ensureUserExists(userId);
        String crewId = scenarioContext.getCrewIdByName(crewName);
        crewRepositoryPort.saveMember(CrewMember.createMember(userId, crewId));
    }

    @조건("{string}가 오늘 인증을 완료했다")
    public void 사용자가_오늘_인증을_완료했다(String userId) {
        String crewId = getFirstCrewId();
        ensureChallengeExists(userId, crewId);
        String challengeId = challengeRepositoryPort
                .findByUserIdAndCrewIdAndStatus(userId, crewId, ChallengeStatus.IN_PROGRESS)
                .map(Challenge::getId)
                .orElseThrow();

        Verification verification = Verification.of(
                IdGenerator.generate("VRFY"), challengeId, userId, crewId,
                null, null, "오늘의 인증입니다",
                VerificationStatus.APPROVED, 0, LocalDate.now(),
                1, ReviewStatus.NOT_REQUIRED, LocalDateTime.now()
        );
        verificationRepositoryPort.save(verification);
    }

    @조건("{string}의 챌린지가 진행 중이다")
    public void 사용자의_챌린지가_진행_중이다(String userId) {
        String crewId = getFirstCrewId();
        ensureChallengeExists(userId, crewId);

        challengeRepositoryPort.findByUserIdAndCrewIdAndStatus(
                userId, crewId, ChallengeStatus.IN_PROGRESS)
                .ifPresent(c -> scenarioContext.setChallengeId(c.getId()));
    }

    @조건("{string}이 {int}일차 인증을 완료했다")
    public void 사용자가_N일차_인증을_완료했다(String userId, int day) {
        String crewId = getFirstCrewId();
        Challenge challenge = challengeRepositoryPort
                .findByUserIdAndCrewIdAndStatus(userId, crewId, ChallengeStatus.IN_PROGRESS)
                .orElseThrow();

        challenge.recordCompletion();
        challengeRepositoryPort.save(challenge);

        Verification verification = Verification.of(
                IdGenerator.generate("VRFY"), challenge.getId(), userId, crewId,
                null, null, day + "일차 인증",
                VerificationStatus.APPROVED, 0, LocalDate.now(),
                1, ReviewStatus.NOT_REQUIRED, LocalDateTime.now()
        );
        verificationRepositoryPort.save(verification);
    }

    @조건("{string}가 어제 인증을 완료했다")
    public void 사용자가_어제_인증을_완료했다(String userId) {
        String crewId = getFirstCrewId();
        ensureChallengeExists(userId, crewId);
        String challengeId = challengeRepositoryPort
                .findByUserIdAndCrewIdAndStatus(userId, crewId, ChallengeStatus.IN_PROGRESS)
                .map(Challenge::getId)
                .orElseThrow();

        Verification verification = Verification.of(
                IdGenerator.generate("VRFY"), challengeId, userId, crewId,
                null, null, "어제 인증입니다",
                VerificationStatus.APPROVED, 0, LocalDate.now().minusDays(1),
                1, ReviewStatus.NOT_REQUIRED, LocalDateTime.now().minusDays(1)
        );
        verificationRepositoryPort.save(verification);
    }

    @조건("크루 {string}에 {int}개의 인증이 존재한다")
    public void 크루에_N개의_인증이_존재한다(String crewName, int count) {
        String crewId = scenarioContext.getCrewIdByName(crewName);
        int usersNeeded = 5;
        int daysPerUser = count / usersNeeded;

        for (int u = 2; u <= usersNeeded + 1; u++) {
            String userId = String.format("user_%03d", u);
            ensureUserExists(userId);
            crewRepositoryPort.saveMember(CrewMember.createMember(userId, crewId));
            ensureChallengeExists(userId, crewId);

            String challengeId = challengeRepositoryPort
                    .findByUserIdAndCrewIdAndStatus(userId, crewId, ChallengeStatus.IN_PROGRESS)
                    .map(Challenge::getId)
                    .orElseThrow();

            for (int d = 0; d < daysPerUser; d++) {
                Verification verification = Verification.of(
                        IdGenerator.generate("VRFY"), challengeId, userId, crewId,
                        null, null, "인증 " + u + "-" + d,
                        VerificationStatus.APPROVED, 0, LocalDate.now().minusDays(d),
                        1, ReviewStatus.NOT_REQUIRED, LocalDateTime.now().minusDays(d)
                );
                verificationRepositoryPort.save(verification);
            }
        }
    }

    @조건("크루 {string}가 존재한다")
    public void 크루가_존재한다(String crewName) {
        String crewId = createActiveCrew(crewName, "crew_creator_" + crewName.hashCode());
        scenarioContext.putCrewId(crewName, crewId);
    }

    @조건("{string}은/는 크루 {string}에 참여하지 않았다")
    public void 사용자는_크루에_참여하지_않았다(String userId, String crewName) {
        // no-op — 멤버 등록 안 함
    }

    // ===== 만일 (When) =====

    @만일("{string}이 크루 {string}의 피드를 조회한다")
    public void 사용자가_크루의_피드를_조회한다(String userId, String crewName) {
        String crewId = scenarioContext.getCrewIdByName(crewName);
        ExtractableResponse<Response> response = feedAdapter.getFeed(userId, crewId);
        scenarioContext.setResponse(response);
    }

    @만일("{string}이 크루 {string}의 피드를 page {int}으로 조회한다")
    public void 사용자가_크루의_피드를_페이지로_조회한다(String userId, String crewName, int page) {
        String crewId = scenarioContext.getCrewIdByName(crewName);
        ExtractableResponse<Response> response = feedAdapter.getFeed(userId, crewId, page);
        scenarioContext.setResponse(response);
    }

    @만일("{string}이 존재하지 않는 크루의 피드를 조회한다")
    public void 존재하지_않는_크루의_피드를_조회한다(String userId) {
        ExtractableResponse<Response> response = feedAdapter.getFeed(userId, "nonexistent-crew-id");
        scenarioContext.setResponse(response);
    }

    // ===== 그리고/그러면 (Then) =====

    @그리고("피드에 {int}개의 인증이 포함된다")
    public void 피드에_N개의_인증이_포함된다(int count) {
        int actual = scenarioContext.getResponse().jsonPath().getList("data.verifications").size();
        assertThat(actual).isEqualTo(count);
    }

    @그리고("응답에 hasNext가 true이다")
    public void 응답에_hasNext가_true이다() {
        boolean hasNext = scenarioContext.getResponse().jsonPath().getBoolean("data.hasNext");
        assertThat(hasNext).isTrue();
    }

    @그리고("myProgress의 completedDays는 {int}이다")
    public void myProgress의_completedDays는_N이다(int expected) {
        int actual = scenarioContext.getResponse().jsonPath().getInt("data.myProgress.completedDays");
        assertThat(actual).isEqualTo(expected);
    }

    @그리고("myProgress의 status는 {string}이다")
    public void myProgress의_status는_이다(String expected) {
        String actual = scenarioContext.getResponse().jsonPath().getString("data.myProgress.status");
        assertThat(actual).isEqualTo(expected);
    }

    @그리고("피드의 첫 번째 인증이 오늘 날짜이다")
    public void 피드의_첫_번째_인증이_오늘_날짜이다() {
        String targetDate = scenarioContext.getResponse().jsonPath().getString("data.verifications[0].targetDate");
        assertThat(targetDate).isEqualTo(LocalDate.now().toString());
    }

    // ===== Helper Methods =====

    private void ensureUserExists(String userId) {
        if (savedUserIds.contains(userId)) {
            return;
        }
        if (userRepositoryPort.findById(userId).isEmpty()) {
            User user = User.of(userId, userId + "@test.com", userId, null, LocalDateTime.now());
            userRepositoryPort.save(user);
        }
        savedUserIds.add(userId);
    }

    private String createActiveCrew(String crewName, String creatorId) {
        ensureUserExists(creatorId);
        String crewId = IdGenerator.generate("CREW");
        Crew crew = Crew.of(
                crewId, creatorId, crewName, crewName + " 목표",
                VerificationType.TEXT, 10, 1, CrewStatus.ACTIVE,
                LocalDate.now(), LocalDate.now().plusDays(14), true,
                generateInviteCode(), LocalDateTime.now(),
                java.util.List.of()
        );
        crewRepositoryPort.save(crew);
        crewRepositoryPort.saveMember(CrewMember.createLeader(creatorId, crewId));
        return crewId;
    }

    private void ensureChallengeExists(String userId, String crewId) {
        if (challengeRepositoryPort.findByUserIdAndCrewIdAndStatus(
                userId, crewId, ChallengeStatus.IN_PROGRESS).isEmpty()) {
            Challenge challenge = Challenge.of(
                    IdGenerator.generate("CHAL"), userId, crewId, 1,
                    3, 0, ChallengeStatus.IN_PROGRESS,
                    LocalDate.now(), LocalDateTime.now().plusDays(3), LocalDateTime.now()
            );
            challengeRepositoryPort.save(challenge);
        }
    }

    private String generateInviteCode() {
        String chars = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
        StringBuilder code = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            int index = (int) (Math.random() * chars.length());
            code.append(chars.charAt(index));
        }
        return code.toString();
    }

    private String getFirstCrewId() {
        String crewId = scenarioContext.getCrewIdByName("운동 크루");
        if (crewId == null) {
            crewId = scenarioContext.getCrewId();
        }
        return crewId;
    }
}
