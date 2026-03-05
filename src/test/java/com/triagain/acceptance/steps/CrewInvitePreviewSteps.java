package com.triagain.acceptance.steps;

import com.triagain.acceptance.ScenarioContext;
import com.triagain.acceptance.adapter.CrewTestAdapter;
import io.cucumber.java.Before;
import io.cucumber.java.ko.그리고;
import io.cucumber.java.ko.만일;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

import static org.assertj.core.api.Assertions.assertThat;

public class CrewInvitePreviewSteps {

    @LocalServerPort
    private int port;

    @Autowired
    private ScenarioContext scenarioContext;

    private CrewTestAdapter crewAdapter;

    @Before
    public void setUp() {
        crewAdapter = new CrewTestAdapter(port);
    }

    @만일("초대코드로 크루 미리보기를 요청한다")
    public void 초대코드로_크루_미리보기를_요청한다() {
        ExtractableResponse<Response> response = crewAdapter.getCrewByInviteCode(
                scenarioContext.getUserId(),
                scenarioContext.getInviteCode()
        );
        scenarioContext.setResponse(response);
    }

    @만일("{string}가/이 초대코드로 크루 미리보기를 요청한다")
    public void 사용자가_초대코드로_크루_미리보기를_요청한다(String userId) {
        ExtractableResponse<Response> response = crewAdapter.getCrewByInviteCode(
                userId,
                scenarioContext.getInviteCode()
        );
        scenarioContext.setResponse(response);
    }

    @만일("초대코드 {string}로 크루 미리보기를 요청한다")
    public void 잘못된_초대코드로_크루_미리보기를_요청한다(String inviteCode) {
        ExtractableResponse<Response> response = crewAdapter.getCrewByInviteCode(
                scenarioContext.getUserId(),
                inviteCode
        );
        scenarioContext.setResponse(response);
    }

    @그리고("미리보기 응답에 크루 이름이 존재한다")
    public void 미리보기_응답에_크루_이름이_존재한다() {
        String name = scenarioContext.getResponse().jsonPath().getString("data.name");
        assertThat(name).isNotNull();
    }

    @그리고("가입 가능 여부는 {word}이다")
    public void 가입_가능_여부는_이다(String expected) {
        boolean joinable = scenarioContext.getResponse().jsonPath().getBoolean("data.joinable");
        assertThat(joinable).isEqualTo(Boolean.parseBoolean(expected));
    }

    @그리고("가입 차단 사유는 없다")
    public void 가입_차단_사유는_없다() {
        Object reason = scenarioContext.getResponse().jsonPath().get("data.joinBlockedReason");
        assertThat(reason).isNull();
    }

    @그리고("가입 차단 사유는 {string}이다")
    public void 가입_차단_사유는_이다(String expectedReason) {
        String reason = scenarioContext.getResponse().jsonPath().getString("data.joinBlockedReason");
        assertThat(reason).isEqualTo(expectedReason);
    }
}
