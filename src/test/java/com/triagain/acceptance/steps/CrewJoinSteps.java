package com.triagain.acceptance.steps;

import com.triagain.acceptance.ScenarioContext;
import com.triagain.acceptance.adapter.CrewTestAdapter;
import com.triagain.crew.api.CreateCrewRequest;
import com.triagain.crew.domain.vo.ChallengeStatus;
import com.triagain.crew.domain.vo.VerificationType;
import com.triagain.crew.port.in.ActivateCrewUseCase;
import com.triagain.crew.port.out.ChallengeRepositoryPort;
import io.cucumber.java.Before;
import io.cucumber.java.ko.그리고;
import io.cucumber.java.ko.만일;
import io.cucumber.java.ko.조건;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class CrewJoinSteps {

    @LocalServerPort
    private int port;

    @Autowired
    private ScenarioContext scenarioContext;

    @Autowired
    private ActivateCrewUseCase activateCrewUseCase;

    @Autowired
    private ChallengeRepositoryPort challengeRepositoryPort;

    private CrewTestAdapter crewAdapter;

    @Before
    public void setUp() {
        crewAdapter = new CrewTestAdapter(port);
    }

    @조건("사용자 {string}이/가 크루를 생성했다")
    public void 사용자가_크루를_생성했다(String userId) {
        scenarioContext.setUserId(userId);
        CreateCrewRequest request = new CreateCrewRequest(
                "테스트 크루", "테스트 목표", VerificationType.TEXT,
                10, LocalDate.now().plusDays(1), LocalDate.now().plusDays(14), true
        );
        ExtractableResponse<Response> response = crewAdapter.createCrew(userId, request);
        scenarioContext.setCrewId(response.jsonPath().getString("data.crewId"));
        scenarioContext.setInviteCode(response.jsonPath().getString("data.inviteCode"));
        scenarioContext.setCreatorId(userId);
    }

    @조건("중간 가입이 허용된 크루가 이미 시작되었다")
    public void 중간_가입이_허용된_크루가_이미_시작되었다() {
        String creatorId = scenarioContext.getCreatorId();
        CreateCrewRequest request = new CreateCrewRequest(
                "활성 크루", "활성 크루 목표", VerificationType.TEXT,
                10, LocalDate.now().plusDays(1), LocalDate.now().plusDays(14), true
        );
        ExtractableResponse<Response> response = crewAdapter.createCrew(creatorId, request);
        String crewId = response.jsonPath().getString("data.crewId");
        scenarioContext.setCrewId(crewId);
        scenarioContext.setInviteCode(response.jsonPath().getString("data.inviteCode"));

        activateCrewUseCase.activateCrew(crewId, creatorId);
    }

    @조건("중간 가입이 불가한 크루가 이미 시작되었다")
    public void 중간_가입이_불가한_크루가_이미_시작되었다() {
        String creatorId = scenarioContext.getCreatorId();
        CreateCrewRequest request = new CreateCrewRequest(
                "활성 크루", "활성 크루 목표", VerificationType.TEXT,
                10, LocalDate.now().plusDays(1), LocalDate.now().plusDays(14), false
        );
        ExtractableResponse<Response> response = crewAdapter.createCrew(creatorId, request);
        String crewId = response.jsonPath().getString("data.crewId");
        scenarioContext.setCrewId(crewId);
        scenarioContext.setInviteCode(response.jsonPath().getString("data.inviteCode"));

        activateCrewUseCase.activateCrew(crewId, creatorId);
    }

    @조건("크루 정원이 가득 찼다")
    public void 크루_정원이_가득_찼다() {
        String creatorId = scenarioContext.getCreatorId();
        CreateCrewRequest request = new CreateCrewRequest(
                "소규모 크루", "소규모 목표", VerificationType.TEXT,
                2, LocalDate.now().plusDays(1), LocalDate.now().plusDays(14), true
        );
        ExtractableResponse<Response> response = crewAdapter.createCrew(creatorId, request);
        String crewId = response.jsonPath().getString("data.crewId");
        String inviteCode = response.jsonPath().getString("data.inviteCode");
        scenarioContext.setCrewId(crewId);
        scenarioContext.setInviteCode(inviteCode);

        crewAdapter.joinByInviteCode("filler_user", Map.of("inviteCode", inviteCode));
    }

    @조건("사용자 {string}가/이 크루에 참여했다")
    public void 사용자가_크루에_참여했다(String userId) {
        crewAdapter.joinByInviteCode(userId, Map.of("inviteCode", scenarioContext.getInviteCode()));
    }

    @조건("크루 종료일이 {int}일 남았다")
    public void 크루_종료일이_N일_남았다(int daysLeft) {
        String creatorId = scenarioContext.getCreatorId();
        CreateCrewRequest request = new CreateCrewRequest(
                "마감 임박 크루", "마감 목표", VerificationType.TEXT,
                10, LocalDate.now().plusDays(1), LocalDate.now().plusDays(daysLeft), true
        );
        ExtractableResponse<Response> response = crewAdapter.createCrew(creatorId, request);
        scenarioContext.setCrewId(response.jsonPath().getString("data.crewId"));
        scenarioContext.setInviteCode(response.jsonPath().getString("data.inviteCode"));
    }

    @만일("초대코드를 입력하여 크루에 참여한다")
    public void 초대코드를_입력하여_크루에_참여한다() {
        ExtractableResponse<Response> response = crewAdapter.joinByInviteCode(
                scenarioContext.getUserId(),
                Map.of("inviteCode", scenarioContext.getInviteCode())
        );
        scenarioContext.setResponse(response);
    }

    @만일("초대코드 {string}로 크루 참여를 요청한다")
    public void 초대코드로_크루_참여를_요청한다(String inviteCode) {
        ExtractableResponse<Response> response = crewAdapter.joinByInviteCode(
                scenarioContext.getUserId(),
                Map.of("inviteCode", inviteCode)
        );
        scenarioContext.setResponse(response);
    }

    @만일("{string}가/이 같은 크루에 다시 참여를 요청한다")
    public void 같은_크루에_다시_참여를_요청한다(String userId) {
        ExtractableResponse<Response> response = crewAdapter.joinByInviteCode(
                userId,
                Map.of("inviteCode", scenarioContext.getInviteCode())
        );
        scenarioContext.setResponse(response);
    }

    @그리고("{string}의 역할은 {string}이다")
    public void 역할은_이다(String userId, String expectedRole) {
        String role = scenarioContext.getResponse().jsonPath().getString("data.role");
        assertThat(role).isEqualTo(expectedRole);
    }

    @그리고("{string}의 챌린지가 자동 생성된다")
    public void 챌린지가_자동_생성된다(String userId) {
        assertThat(challengeRepositoryPort.findByUserIdAndCrewIdAndStatus(
                userId, scenarioContext.getCrewId(), ChallengeStatus.IN_PROGRESS
        )).isPresent();
    }
}
