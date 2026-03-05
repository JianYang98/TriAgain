package com.triagain.acceptance.steps;

import com.triagain.acceptance.ScenarioContext;
import com.triagain.acceptance.adapter.CrewTestAdapter;
import com.triagain.crew.domain.model.Challenge;
import com.triagain.crew.domain.model.Crew;
import com.triagain.crew.port.in.ActivateCrewUseCase;
import com.triagain.crew.port.out.ChallengeRepositoryPort;
import com.triagain.crew.port.out.CrewRepositoryPort;
import io.cucumber.java.Before;
import io.cucumber.java.ko.그리고;
import io.cucumber.java.ko.만일;
import io.cucumber.java.ko.조건;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

public class CrewDetailSteps {

    @LocalServerPort
    private int port;

    @Autowired
    private ScenarioContext scenarioContext;

    @Autowired
    private ActivateCrewUseCase activateCrewUseCase;

    @Autowired
    private CrewRepositoryPort crewRepositoryPort;

    @Autowired
    private ChallengeRepositoryPort challengeRepositoryPort;

    private CrewTestAdapter crewAdapter;

    @Before
    public void setUp() {
        crewAdapter = new CrewTestAdapter(port);
    }

    @만일("{string}가/이 크루 상세를 조회한다")
    public void 크루_상세를_조회한다(String userId) {
        ExtractableResponse<Response> response = crewAdapter.getCrew(userId, scenarioContext.getCrewId());
        scenarioContext.setResponse(response);
    }

    @만일("{string}가/이 존재하지 않는 크루를 조회한다")
    public void 존재하지_않는_크루를_조회한다(String userId) {
        ExtractableResponse<Response> response = crewAdapter.getCrew(userId, "nonexistent-crew-id");
        scenarioContext.setResponse(response);
    }

    @조건("크루가 활성화되어 챌린지가 시작되었다")
    public void 크루가_활성화되어_챌린지가_시작되었다() {
        String crewId = scenarioContext.getCrewId();
        activateCrewUseCase.activateCrew(crewId, scenarioContext.getCreatorId());

        Crew crew = crewRepositoryPort.findById(crewId).orElseThrow();
        LocalDateTime deadline = crew.getStartDate().plusDays(3).atTime(crew.getDeadlineTime());
        crew.getMembers().forEach(member -> {
            Challenge challenge = Challenge.createFirst(
                    member.getUserId(), crewId, crew.getStartDate(), deadline);
            challengeRepositoryPort.save(challenge);
        });
    }

    @그리고("멤버 {string}의 닉네임이 존재한다")
    public void 멤버의_닉네임이_존재한다(String userId) {
        String nickname = scenarioContext.getResponse().jsonPath()
                .get("data.members.find { it.userId == '" + userId + "' }.nickname");
        assertThat(nickname).isNotNull();
    }

    @그리고("멤버 {string}의 챌린지 진행도가 존재한다")
    public void 멤버의_챌린지_진행도가_존재한다(String userId) {
        Object progress = scenarioContext.getResponse().jsonPath()
                .get("data.members.find { it.userId == '" + userId + "' }.challengeProgress");
        assertThat(progress).isNotNull();
    }

    @그리고("멤버 {string}의 챌린지 상태는 {string}이다")
    public void 멤버의_챌린지_상태는_이다(String userId, String expectedStatus) {
        String status = scenarioContext.getResponse().jsonPath()
                .get("data.members.find { it.userId == '" + userId + "' }.challengeProgress.challengeStatus");
        assertThat(status).isEqualTo(expectedStatus);
    }

    @그리고("멤버 {string}의 완료일수는 {int}이다")
    public void 멤버의_완료일수는_이다(String userId, int expectedDays) {
        int completedDays = scenarioContext.getResponse().jsonPath()
                .get("data.members.find { it.userId == '" + userId + "' }.challengeProgress.completedDays");
        assertThat(completedDays).isEqualTo(expectedDays);
    }

    @그리고("멤버 {string}의 목표일수는 {int}이다")
    public void 멤버의_목표일수는_이다(String userId, int expectedDays) {
        int targetDays = scenarioContext.getResponse().jsonPath()
                .get("data.members.find { it.userId == '" + userId + "' }.challengeProgress.targetDays");
        assertThat(targetDays).isEqualTo(expectedDays);
    }
}