package com.triagain.acceptance.steps;

import com.triagain.acceptance.ScenarioContext;
import com.triagain.acceptance.adapter.CrewTestAdapter;
import com.triagain.crew.api.EditCrewRequest;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.Before;
import io.cucumber.java.ko.그리고;
import io.cucumber.java.ko.만일;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class CrewEditSteps {

	@LocalServerPort
	private int port;

	@Autowired
	private ScenarioContext scenarioContext;

	private CrewTestAdapter crewAdapter;

	@Before
	public void setUp() {
		crewAdapter = new CrewTestAdapter(port);
	}

	@만일("크루 정보를 수정한다:")
	public void 크루_정보를_수정한다(DataTable dataTable) {
		Map<String, String> data = dataTable.asMap(String.class, String.class);
		EditCrewRequest request = new EditCrewRequest(
				data.getOrDefault("이름", null),
				data.getOrDefault("목표", null),
				data.getOrDefault("인증내용", null),
				null, null
		);

		ExtractableResponse<Response> response = crewAdapter.editCrew(
				scenarioContext.getUserId(), scenarioContext.getCrewId(), request);
		scenarioContext.setResponse(response);
	}

	@만일("사용자 {string}이/가 크루 정보를 수정한다:")
	public void 특정_사용자가_크루_정보를_수정한다(String userId, DataTable dataTable) {
		Map<String, String> data = dataTable.asMap(String.class, String.class);
		EditCrewRequest request = new EditCrewRequest(
				data.getOrDefault("이름", null),
				data.getOrDefault("목표", null),
				data.getOrDefault("인증내용", null),
				null, null
		);

		ExtractableResponse<Response> response = crewAdapter.editCrew(
				userId, scenarioContext.getCrewId(), request);
		scenarioContext.setResponse(response);
	}

	@만일("빈 body로 크루를 수정한다")
	public void 빈_body로_크루를_수정한다() {
		EditCrewRequest request = new EditCrewRequest(null, null, null, null, null);
		ExtractableResponse<Response> response = crewAdapter.editCrew(
				scenarioContext.getUserId(), scenarioContext.getCrewId(), request);
		scenarioContext.setResponse(response);
	}

	@만일("빈 문자열로 크루 이름을 수정한다")
	public void 빈_문자열로_크루_이름을_수정한다() {
		Map<String, String> request = new HashMap<>();
		request.put("name", "");
		ExtractableResponse<Response> response = crewAdapter.editCrew(
				scenarioContext.getUserId(), scenarioContext.getCrewId(), request);
		scenarioContext.setResponse(response);
	}

	@그리고("응답의 크루 이름은 {string}이다")
	public void 응답의_크루_이름은_이다(String expected) {
		String actual = scenarioContext.getResponse().jsonPath().getString("data.name");
		assertThat(actual).isEqualTo(expected);
	}

	@그리고("응답의 크루 목표는 {string}이다")
	public void 응답의_크루_목표는_이다(String expected) {
		String actual = scenarioContext.getResponse().jsonPath().getString("data.goal");
		assertThat(actual).isEqualTo(expected);
	}

	@그리고("응답의 인증 내용은 {string}이다")
	public void 응답의_인증_내용은_이다(String expected) {
		String actual = scenarioContext.getResponse().jsonPath().getString("data.verificationContent");
		assertThat(actual).isEqualTo(expected);
	}
}
