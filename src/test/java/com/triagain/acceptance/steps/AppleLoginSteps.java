package com.triagain.acceptance.steps;

import com.triagain.acceptance.ScenarioContext;
import com.triagain.acceptance.adapter.AuthTestAdapter;
import com.triagain.acceptance.adapter.StubAppleTokenVerifierPort;
import io.cucumber.java.Before;
import io.cucumber.java.ko.그리고;
import io.cucumber.java.ko.만일;
import io.cucumber.java.ko.조건;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

import static org.assertj.core.api.Assertions.assertThat;

public class AppleLoginSteps {

    @LocalServerPort
    private int port;

    @Autowired
    private ScenarioContext scenarioContext;

    @Autowired
    private StubAppleTokenVerifierPort stubAppleTokenVerifier;

    private AuthTestAdapter authAdapter;
    private String appleId;

    @Before
    public void setUp() {
        authAdapter = new AuthTestAdapter(port);
    }

    @조건("Apple 토큰 검증 스텁이 설정되어 있다")
    public void apple_토큰_검증_스텁이_설정되어_있다() {
        // StubAppleTokenVerifierPort가 @Primary로 주입되어 자동 활성화
    }

    @만일("Apple Identity Token으로 로그인을 요청한다")
    public void apple_로그인을_요청한다() {
        var response = authAdapter.appleLogin("stub-identity-token");
        scenarioContext.setResponse(response);
        appleId = response.jsonPath().getString("data.appleId");
    }

    @그리고("Apple 회원가입을 요청한다")
    public void apple_회원가입을_요청한다() {
        String id = appleId != null ? appleId : stubAppleTokenVerifier.getStubSub();
        scenarioContext.setResponse(
                authAdapter.appleSignup("stub-identity-token", id, "애플유저", true)
        );
    }

    @그리고("약관 미동의로 Apple 회원가입을 요청한다")
    public void 약관_미동의로_apple_회원가입을_요청한다() {
        String id = appleId != null ? appleId : stubAppleTokenVerifier.getStubSub();
        scenarioContext.setResponse(
                authAdapter.appleSignup("stub-identity-token", id, "애플유저", false)
        );
    }

    @그리고("잘못된 닉네임으로 Apple 회원가입을 요청한다")
    public void 잘못된_닉네임으로_apple_회원가입을_요청한다() {
        String id = appleId != null ? appleId : stubAppleTokenVerifier.getStubSub();
        scenarioContext.setResponse(
                authAdapter.appleSignup("stub-identity-token", id, "!", true)
        );
    }

    @그리고("응답의 isNewUser는 {word}이다")
    public void 응답의_isNewUser는_이다(String expected) {
        boolean actual = scenarioContext.getResponse().jsonPath().getBoolean("data.isNewUser");
        assertThat(actual).isEqualTo(Boolean.parseBoolean(expected));
    }

    @그리고("응답에 appleId가 존재한다")
    public void 응답에_appleId가_존재한다() {
        String appleIdValue = scenarioContext.getResponse().jsonPath().getString("data.appleId");
        assertThat(appleIdValue).isNotNull();
    }
}
