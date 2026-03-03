package com.triagain.acceptance.steps;

import com.triagain.acceptance.ScenarioContext;
import com.triagain.acceptance.adapter.UserTestAdapter;
import io.cucumber.java.Before;
import io.cucumber.java.ko.만일;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class MyProfileSteps {

    @LocalServerPort
    private int port;

    @Autowired
    private ScenarioContext scenarioContext;

    private UserTestAdapter userAdapter;

    @Before
    public void setUp() {
        userAdapter = new UserTestAdapter(port);
    }

    @만일("{string}이/가 내 프로필을 조회한다")
    public void 내_프로필을_조회한다(String userId) {
        scenarioContext.setResponse(userAdapter.getMyProfile(userId));
    }

    @만일("미인증 상태로 프로필을 조회한다")
    public void 미인증_상태로_프로필을_조회한다() {
        scenarioContext.setResponse(userAdapter.getMyProfileWithoutAuth());
    }

    @만일("{string}이/가 닉네임을 {string}으로 변경한다")
    public void 닉네임을_변경한다(String userId, String nickname) {
        scenarioContext.setResponse(
                userAdapter.updateNickname(userId, Map.of("nickname", nickname)));
    }

    @만일("미인증 상태로 닉네임을 변경한다")
    public void 미인증_상태로_닉네임을_변경한다() {
        scenarioContext.setResponse(
                userAdapter.updateNicknameWithoutAuth(Map.of("nickname", "테스트")));
    }

    @만일("로그아웃을 요청한다")
    public void 로그아웃을_요청한다() {
        scenarioContext.setResponse(userAdapter.logout());
    }

    @만일("응답의 닉네임은 {string}이다")
    public void 응답의_닉네임은_이다(String expectedNickname) {
        String actual = scenarioContext.getResponse().jsonPath().getString("data.nickname");
        assertThat(actual).isEqualTo(expectedNickname);
    }
}