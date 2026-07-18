package com.triagain.e2e;

import com.triagain.acceptance.DatabaseCleanup;
import com.triagain.acceptance.TestContainers;
import com.triagain.common.util.IdGenerator;
import com.triagain.crew.domain.model.Challenge;
import com.triagain.crew.domain.model.Crew;
import com.triagain.crew.domain.model.CrewMember;
import com.triagain.crew.domain.vo.ChallengeStatus;
import com.triagain.crew.domain.vo.CrewStatus;
import com.triagain.crew.domain.vo.CrewVisibility;
import com.triagain.crew.domain.vo.VerificationType;
import com.triagain.crew.port.out.ChallengeRepositoryPort;
import com.triagain.crew.port.out.CrewRepositoryPort;
import com.triagain.user.domain.model.User;
import com.triagain.user.port.out.UserRepositoryPort;
import io.restassured.RestAssured;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** E2E 테스트 공통 베이스 — TestContainers + DatabaseCleanup 재활용 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("e2e")
public abstract class E2eTestBase {

    @LocalServerPort
    protected int port;

    @Autowired
    protected DatabaseCleanup databaseCleanup;

    @Autowired
    protected UserRepositoryPort userRepositoryPort;

    @Autowired
    protected CrewRepositoryPort crewRepositoryPort;

    @Autowired
    protected ChallengeRepositoryPort challengeRepositoryPort;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", TestContainers::getJdbcUrl);
        registry.add("spring.datasource.username", TestContainers::getUsername);
        registry.add("spring.datasource.password", TestContainers::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.flyway.enabled", () -> "false");
    }

    @BeforeEach
    void cleanup() {
        databaseCleanup.execute();
    }

    // ===== HTTP 헬퍼 =====

    protected RequestSpecification givenRequest() {
        return RestAssured.given()
                .baseUri("http://localhost:" + port)
                .contentType("application/json")
                .log().ifValidationFails();
    }

    protected RequestSpecification givenAuthRequest(String userId) {
        return givenRequest().header("X-User-Id", userId);
    }

    protected ExtractableResponse<Response> authGet(String userId, String path) {
        return givenAuthRequest(userId)
                .when().get(path)
                .then().log().ifError().extract();
    }

    protected ExtractableResponse<Response> authPost(String userId, String path, Object body) {
        return givenAuthRequest(userId)
                .body(body)
                .when().post(path)
                .then().log().ifError().extract();
    }

    // ===== 데이터 세팅 헬퍼 =====

    /** 테스트 유저 생성 — DB 직접 저장 */
    protected User createUser(String userId) {
        User user = User.of(userId, "KAKAO", userId + "@test.com",
                userId, null, null, null, LocalDateTime.now(), LocalDateTime.now(), null, 0);
        return userRepositoryPort.save(user);
    }

    /** ACTIVE 크루 + LEADER 멤버 생성 — DB 직접 저장 */
    protected Crew createActiveCrew(String creatorId) {
        String crewId = IdGenerator.generate("CREW");
        Crew crew = Crew.of(
                crewId, creatorId, "E2E 테스트 크루", "E2E 목표",
                "인증 내용", VerificationType.TEXT, 10, 1, CrewStatus.ACTIVE,
                LocalDate.now(), LocalDate.now().plusDays(14), true,
                generateInviteCode(), LocalDateTime.now(),
                Crew.DEFAULT_DEADLINE_TIME, null, CrewVisibility.PRIVATE, 0L, List.of()
        );
        crewRepositoryPort.save(crew);
        crewRepositoryPort.saveMember(CrewMember.createLeader(creatorId, crewId));
        return crew;
    }

    /**
     * IN_PROGRESS 챌린지 생성 — completedDays 지정 가능.
     * startDate=오늘-completedDays로 맞춰 슬롯(startDate+completedDays)이 항상 "오늘"이 되도록 한다 —
     * grace-targetdate SDD로 targetDate가 슬롯 기준이 되면서, startDate=오늘 고정이면 completedDays>0일 때
     * 슬롯이 미래로 밀려 하한(V003)에 걸리는 문제가 있었다.
     */
    protected Challenge createChallenge(String userId, String crewId, int completedDays) {
        Challenge challenge = Challenge.of(
                IdGenerator.generate("CHAL"), userId, crewId, 1,
                3, completedDays, ChallengeStatus.IN_PROGRESS,
                LocalDate.now().minusDays(completedDays), LocalDateTime.now().plusDays(3), LocalDateTime.now()
        );
        return challengeRepositoryPort.save(challenge);
    }

    private String generateInviteCode() {
        String chars = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
        StringBuilder code = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            code.append(chars.charAt((int) (Math.random() * chars.length())));
        }
        return code.toString();
    }
}
