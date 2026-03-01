package com.triagain.acceptance.steps;

import com.triagain.acceptance.ScenarioContext;
import com.triagain.acceptance.adapter.CrewTestAdapter;
import com.triagain.crew.api.CreateCrewRequest;
import com.triagain.crew.domain.vo.VerificationType;
import io.cucumber.java.Before;
import io.cucumber.java.ko.그리고;
import io.cucumber.java.ko.만일;
import io.cucumber.java.ko.조건;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class CrewListSteps {

    @LocalServerPort
    private int port;

    @Autowired
    private ScenarioContext scenarioContext;

    private CrewTestAdapter crewAdapter;

    @Before
    public void setUp() {
        crewAdapter = new CrewTestAdapter(port);
    }

    @조건("사용자 {string}이/가 크루 {int}개를 생성했다")
    public void 사용자가_크루_N개를_생성했다(String userId, int count) {
        scenarioContext.setUserId(userId);
        scenarioContext.setCreatorId(userId);
        for (int i = 0; i < count; i++) {
            CreateCrewRequest request = new CreateCrewRequest(
                    "크루 " + (i + 1), "목표 " + (i + 1), VerificationType.TEXT,
                    10, LocalDate.now().plusDays(1), LocalDate.now().plusDays(14), true
            );
            crewAdapter.createCrew(userId, request);
        }
    }

    @만일("{string}이/가 내 크루 목록을 조회한다")
    public void 내_크루_목록을_조회한다(String userId) {
        ExtractableResponse<Response> response = crewAdapter.getMyCrews(userId);
        scenarioContext.setResponse(response);
    }

    @그리고("크루 목록에 {int}개의 크루가 포함된다")
    public void 크루_목록에_N개의_크루가_포함된다(int expectedCount) {
        List<?> crews = scenarioContext.getResponse().jsonPath().getList("data");
        assertThat(crews).hasSize(expectedCount);
    }
}
