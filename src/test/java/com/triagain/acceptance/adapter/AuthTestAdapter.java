package com.triagain.acceptance.adapter;

import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;

import java.util.Map;

public class AuthTestAdapter extends BaseTestAdapter {

    public AuthTestAdapter(int port) {
        super(port);
    }

    /** Apple 로그인 — POST /auth/apple */
    public ExtractableResponse<Response> appleLogin(String identityToken) {
        return post("/auth/apple", Map.of("identityToken", identityToken));
    }

    /** Apple 회원가입 — POST /auth/apple-signup */
    public ExtractableResponse<Response> appleSignup(String identityToken, String appleId,
                                                      String nickname, boolean termsAgreed) {
        return givenRequest()
                .body(Map.of(
                        "identityToken", identityToken,
                        "appleId", appleId,
                        "nickname", nickname,
                        "termsAgreed", termsAgreed
                ))
                .when()
                .post("/auth/apple-signup")
                .then()
                .extract();
    }
}
