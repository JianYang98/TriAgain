package com.triagain.acceptance.adapter;

import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;

import java.util.Map;

public class AuthTestAdapter extends BaseTestAdapter {

    public AuthTestAdapter(int port) {
        super(port);
    }

    /** Apple 로그인 — POST /auth/apple. authorizationCode는 옵셔널이므로 stub-auth-code 고정 전송 (backfill 동작 시뮬레이션) */
    public ExtractableResponse<Response> appleLogin(String identityToken) {
        return post("/auth/apple", Map.of(
                "identityToken", identityToken,
                "authorizationCode", "stub-auth-code"
        ));
    }

    /** Apple 회원가입 — POST /auth/apple-signup. authorizationCode는 @NotBlank 필수 */
    public ExtractableResponse<Response> appleSignup(String identityToken, String appleId,
                                                      String nickname, boolean termsAgreed) {
        return givenRequest()
                .body(Map.of(
                        "identityToken", identityToken,
                        "appleId", appleId,
                        "nickname", nickname,
                        "termsAgreed", termsAgreed,
                        "authorizationCode", "stub-auth-code"
                ))
                .when()
                .post("/auth/apple-signup")
                .then()
                .extract();
    }
}
