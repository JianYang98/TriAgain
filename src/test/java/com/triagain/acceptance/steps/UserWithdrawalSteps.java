package com.triagain.acceptance.steps;

import com.triagain.acceptance.ScenarioContext;
import com.triagain.acceptance.adapter.CrewTestAdapter;
import com.triagain.acceptance.adapter.UserTestAdapter;
import io.cucumber.java.Before;
import io.cucumber.java.ko.만일;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

public class UserWithdrawalSteps {

    @LocalServerPort
    private int port;

    @Autowired
    private ScenarioContext scenarioContext;

    private UserTestAdapter userAdapter;
    private CrewTestAdapter crewAdapter;

    @Before
    public void setUp() {
        userAdapter = new UserTestAdapter(port);
        crewAdapter = new CrewTestAdapter(port);
    }

    @만일("사용자 {string}이/가 회원탈퇴를 요청한다")
    public void 사용자가_회원탈퇴를_요청한다(String userId) {
        ExtractableResponse<Response> response =
                userAdapter.withdraw(userId);
        scenarioContext.setResponse(response);
    }

    @만일("{string}가/이 해당 크루를 조회한다")
    public void 해당_크루를_조회한다(String userId) {
        ExtractableResponse<Response> response =
                crewAdapter.getCrew(userId, scenarioContext.getCrewId());
        scenarioContext.setResponse(response);
    }
}
