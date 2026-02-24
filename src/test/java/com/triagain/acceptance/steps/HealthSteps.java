package com.triagain.acceptance.steps;

import com.triagain.acceptance.ScenarioContext;
import com.triagain.acceptance.adapter.HealthTestAdapter;
import io.cucumber.java.Before;
import io.cucumber.java.ko.그러면;
import io.cucumber.java.ko.그리고;
import io.cucumber.java.ko.만일;
import io.cucumber.java.ko.조건;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

import static org.assertj.core.api.Assertions.assertThat;

public class HealthSteps {

    @LocalServerPort
    private int port;

    @Autowired
    private ScenarioContext scenarioContext;

    private HealthTestAdapter healthAdapter;

    @Before
    public void setUp() {
        healthAdapter = new HealthTestAdapter(port);
    }

    @조건("서버가 실행 중이다")
    public void 서버가_실행_중이다() {
        // SpringBootTest가 서버를 시작하므로 별도 작업 불필요
    }

    @만일("헬스 체크 API를 호출하면")
    public void 헬스_체크_API를_호출하면() {
        scenarioContext.setResponse(healthAdapter.getHealth());
    }

    @그러면("응답 코드는 {int}이다")
    public void 응답_코드는_이다(int statusCode) {
        assertThat(scenarioContext.getResponse().statusCode()).isEqualTo(statusCode);
    }

    @그리고("데이터베이스 상태는 {string}이다")
    public void 데이터베이스_상태는_이다(String expectedStatus) {
        String dbStatus = scenarioContext.getResponse().jsonPath().getString("data.database");
        assertThat(dbStatus).isEqualTo(expectedStatus);
    }
}
