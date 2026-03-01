package com.triagain.acceptance.steps;

import com.triagain.acceptance.ScenarioContext;
import com.triagain.acceptance.adapter.CrewTestAdapter;
import com.triagain.crew.api.CreateCrewRequest;
import com.triagain.crew.domain.vo.VerificationType;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.Before;
import io.cucumber.java.ko.그러면;
import io.cucumber.java.ko.그리고;
import io.cucumber.java.ko.만일;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class CrewCreationSteps {

    @LocalServerPort
    private int port;

    @Autowired
    private ScenarioContext scenarioContext;

    private CrewTestAdapter crewAdapter;

    @Before
    public void setUp() {
        crewAdapter = new CrewTestAdapter(port);
    }

    @만일("다음 정보로 크루를 생성한다")
    public void 다음_정보로_크루를_생성한다(DataTable dataTable) {
        Map<String, String> data = dataTable.asMap(String.class, String.class);

        LocalDate startDate = resolveDate(data.getOrDefault("시작일", "내일"));
        LocalDate endDate = resolveDate(data.getOrDefault("종료일", "14일 후"));

        CreateCrewRequest request = new CreateCrewRequest(
                data.getOrDefault("이름", "테스트 크루"),
                data.getOrDefault("목표", "테스트 목표"),
                VerificationType.valueOf(data.getOrDefault("인증방식", "TEXT")),
                Integer.parseInt(data.getOrDefault("최대인원", "10")),
                startDate,
                endDate,
                "허용".equals(data.getOrDefault("중간가입", "허용"))
        );

        ExtractableResponse<Response> response = crewAdapter.createCrew(scenarioContext.getUserId(), request);
        scenarioContext.setResponse(response);
        storeCrewContext(response);
    }

    @만일("인증방식 {string}로 크루를 생성한다")
    public void 인증방식으로_크루를_생성한다(String verificationType) {
        CreateCrewRequest request = defaultRequest(VerificationType.valueOf(verificationType));
        ExtractableResponse<Response> response = crewAdapter.createCrew(scenarioContext.getUserId(), request);
        scenarioContext.setResponse(response);
        storeCrewContext(response);
    }

    @만일("크루를 생성한다")
    public void 크루를_생성한다() {
        CreateCrewRequest request = defaultRequest(VerificationType.TEXT);
        ExtractableResponse<Response> response = crewAdapter.createCrew(scenarioContext.getUserId(), request);
        scenarioContext.setResponse(response);
        storeCrewContext(response);
    }

    @만일("시작일을 오늘로 설정하여 크루를 생성한다")
    public void 시작일을_오늘로_설정하여_크루를_생성한다() {
        CreateCrewRequest request = new CreateCrewRequest(
                "테스트 크루", "테스트 목표", VerificationType.TEXT,
                10, LocalDate.now(), LocalDate.now().plusDays(14), true
        );
        ExtractableResponse<Response> response = crewAdapter.createCrew(scenarioContext.getUserId(), request);
        scenarioContext.setResponse(response);
    }

    @만일("종료일을 시작일보다 이전으로 설정한다")
    public void 종료일을_시작일보다_이전으로_설정한다() {
        LocalDate startDate = LocalDate.now().plusDays(7);
        CreateCrewRequest request = new CreateCrewRequest(
                "테스트 크루", "테스트 목표", VerificationType.TEXT,
                10, startDate, startDate.minusDays(1), true
        );
        ExtractableResponse<Response> response = crewAdapter.createCrew(scenarioContext.getUserId(), request);
        scenarioContext.setResponse(response);
    }

    @만일("크루 이름 없이 크루를 생성한다")
    public void 크루_이름_없이_크루를_생성한다() {
        CreateCrewRequest request = new CreateCrewRequest(
                "", "테스트 목표", VerificationType.TEXT,
                10, LocalDate.now().plusDays(1), LocalDate.now().plusDays(14), true
        );
        ExtractableResponse<Response> response = crewAdapter.createCrew(scenarioContext.getUserId(), request);
        scenarioContext.setResponse(response);
    }

    @만일("최대인원 {int}으로 크루 생성을 요청한다")
    public void 최대인원으로_크루_생성을_요청한다(int maxMembers) {
        CreateCrewRequest request = new CreateCrewRequest(
                "테스트 크루", "테스트 목표", VerificationType.TEXT,
                maxMembers, LocalDate.now().plusDays(1), LocalDate.now().plusDays(14), true
        );
        ExtractableResponse<Response> response = crewAdapter.createCrew(scenarioContext.getUserId(), request);
        scenarioContext.setResponse(response);
    }

    @그리고("크루 상태는 {string}이다")
    public void 크루_상태는_이다(String expectedStatus) {
        String status = scenarioContext.getResponse().jsonPath().getString("data.status");
        assertThat(status).isEqualTo(expectedStatus);
    }

    @그리고("인증방식은 {string}이다")
    public void 인증방식은_이다(String expectedType) {
        String type = scenarioContext.getResponse().jsonPath().getString("data.verificationType");
        assertThat(type).isEqualTo(expectedType);
    }

    @그러면("초대코드는 6자리이다")
    public void 초대코드는_6자리이다() {
        String inviteCode = scenarioContext.getInviteCode();
        assertThat(inviteCode).hasSize(6);
    }

    @그리고("초대코드에 {string}, {string}, {string}, {string} 문자가 포함되지 않는다")
    public void 초대코드에_혼동_문자가_포함되지_않는다(String c1, String c2, String c3, String c4) {
        String inviteCode = scenarioContext.getInviteCode();
        assertThat(inviteCode).doesNotContain(c1, c2, c3, c4);
    }

    @그러면("크루 상세를 조회했을 때 {string}의 역할은 {string}이다")
    public void 크루_상세를_조회했을_때_역할은_이다(String userId, String expectedRole) {
        ExtractableResponse<Response> detailResponse = crewAdapter.getCrew(
                userId, scenarioContext.getCrewId()
        );

        String role = detailResponse.jsonPath().getString(
                "data.members.find { it.userId == '" + userId + "' }.role"
        );
        assertThat(role).isEqualTo(expectedRole);
    }

    private void storeCrewContext(ExtractableResponse<Response> response) {
        if (response.statusCode() == 201) {
            scenarioContext.setCrewId(response.jsonPath().getString("data.crewId"));
            scenarioContext.setInviteCode(response.jsonPath().getString("data.inviteCode"));
            scenarioContext.setCreatorId(scenarioContext.getUserId());
        }
    }

    private CreateCrewRequest defaultRequest(VerificationType verificationType) {
        return new CreateCrewRequest(
                "테스트 크루", "테스트 목표", verificationType,
                10, LocalDate.now().plusDays(1), LocalDate.now().plusDays(14), true
        );
    }

    private LocalDate resolveDate(String label) {
        return switch (label) {
            case "오늘" -> LocalDate.now();
            case "내일" -> LocalDate.now().plusDays(1);
            case "14일 후" -> LocalDate.now().plusDays(14);
            default -> {
                if (label.matches("\\d+일 후")) {
                    int days = Integer.parseInt(label.replaceAll("[^0-9]", ""));
                    yield LocalDate.now().plusDays(days);
                }
                yield LocalDate.parse(label);
            }
        };
    }
}
