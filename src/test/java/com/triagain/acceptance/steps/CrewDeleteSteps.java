package com.triagain.acceptance.steps;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

import com.triagain.acceptance.ScenarioContext;
import com.triagain.acceptance.adapter.CrewTestAdapter;

import io.cucumber.java.Before;
import io.cucumber.java.ko.만일;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;

public class CrewDeleteSteps {

	@LocalServerPort
	private int port;

	@Autowired
	private ScenarioContext scenarioContext;

	private CrewTestAdapter crewAdapter;

	@Before
	public void setUp() {
		crewAdapter = new CrewTestAdapter(port);
	}

	@만일("크루를 삭제한다")
	public void 크루를_삭제한다() {
		ExtractableResponse<Response> response = crewAdapter.deleteCrew(
				scenarioContext.getUserId(), scenarioContext.getCrewId());
		scenarioContext.setResponse(response);
	}

	@만일("사용자 {string}이/가 크루를 삭제한다")
	public void 특정_사용자가_크루를_삭제한다(String userId) {
		ExtractableResponse<Response> response = crewAdapter.deleteCrew(
				userId, scenarioContext.getCrewId());
		scenarioContext.setResponse(response);
	}
}
