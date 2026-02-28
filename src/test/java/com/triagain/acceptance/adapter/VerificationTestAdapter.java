package com.triagain.acceptance.adapter;

import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;

public class VerificationTestAdapter extends BaseTestAdapter {

    public VerificationTestAdapter(int port) {
        super(port);
    }

    /** 인증 생성 — POST /verifications */
    public ExtractableResponse<Response> createVerification(String userId, Object request) {
        return givenRequest()
                .header("X-User-Id", userId)
                .body(request)
                .when()
                .post("/verifications")
                .then()
                .log().ifError()
                .extract();
    }
}
