package com.triagain.acceptance.steps;

import com.triagain.acceptance.ScenarioContext;
import com.triagain.acceptance.adapter.CrewTestAdapter;
import com.triagain.crew.port.in.ActivateCrewUseCase;
import io.cucumber.java.Before;
import io.cucumber.java.ko.만일;
import io.cucumber.java.ko.조건;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

public class CrewDetailSteps {

    @LocalServerPort
    private int port;

    @Autowired
    private ScenarioContext scenarioContext;

    @Autowired
    private ActivateCrewUseCase activateCrewUseCase;

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
        activateCrewUseCase.activateCrew(scenarioContext.getCrewId(), scenarioContext.getCreatorId());
    }
}
