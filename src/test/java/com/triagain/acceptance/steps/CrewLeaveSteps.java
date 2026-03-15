package com.triagain.acceptance.steps;

import com.triagain.acceptance.ScenarioContext;
import com.triagain.acceptance.adapter.CrewTestAdapter;
import io.cucumber.java.Before;
import io.cucumber.java.ko.만일;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

public class CrewLeaveSteps {

    @LocalServerPort
    private int port;

    @Autowired
    private ScenarioContext scenarioContext;

    private CrewTestAdapter crewAdapter;

    @Before
    public void setUp() {
        crewAdapter = new CrewTestAdapter(port);
    }

    @만일("사용자 {string}이/가 크루에서 탈퇴한다")
    public void 사용자가_크루에서_탈퇴한다(String userId) {
        ExtractableResponse<Response> response = crewAdapter.leaveCrew(
                userId, scenarioContext.getCrewId());
        scenarioContext.setResponse(response);
    }
}
