package com.triagain.acceptance.steps;

import com.triagain.acceptance.DatabaseCleanup;
import com.triagain.acceptance.ScenarioContext;
import com.triagain.common.exception.ErrorCode;
import io.cucumber.java.Before;
import io.cucumber.java.ko.그러면;
import io.cucumber.java.ko.그리고;
import io.cucumber.java.ko.조건;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

public class CommonSteps {

    @Autowired
    private ScenarioContext scenarioContext;

    @Autowired
    private DatabaseCleanup databaseCleanup;

    @Before(order = 0)
    public void cleanupDatabase() {
        databaseCleanup.execute();
    }

    @조건("사용자 {string}이/가 로그인되어 있다")
    public void 사용자가_로그인되어_있다(String userId) {
        scenarioContext.setUserId(userId);
    }

    @그러면("응답 코드는 {int}이다")
    public void 응답_코드는_이다(int statusCode) {
        assertThat(scenarioContext.getResponse().statusCode()).isEqualTo(statusCode);
    }

    @그리고("^응답에 (.+)[이가] 존재한다$")
    public void 응답에_필드가_존재한다(String field) {
        Object value = scenarioContext.getResponse().jsonPath().get("data." + field);
        assertThat(value).isNotNull();
    }

    @그리고("에러 코드는 {string}이다")
    public void 에러_코드는_이다(String errorCodeName) {
        String expectedCode = ErrorCode.valueOf(errorCodeName).getCode();
        String actualCode = scenarioContext.getResponse().jsonPath().getString("error.code");
        assertThat(actualCode).isEqualTo(expectedCode);
    }

    @그리고("현재 인원은 {int}이다")
    public void 현재_인원은_이다(int expected) {
        int actual = scenarioContext.getResponse().jsonPath().getInt("data.currentMembers");
        assertThat(actual).isEqualTo(expected);
    }
}
