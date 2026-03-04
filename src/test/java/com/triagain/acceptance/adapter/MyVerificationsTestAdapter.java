package com.triagain.acceptance.adapter;

import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;

public class MyVerificationsTestAdapter extends BaseTestAdapter {

    public MyVerificationsTestAdapter(int port) {
        super(port);
    }

    /** 내 인증 현황 조회 — GET /crews/{crewId}/my-verifications */
    public ExtractableResponse<Response> getMyVerifications(String userId, String crewId) {
        return givenAuthRequest(userId)
                .when()
                .get("/crews/" + crewId + "/my-verifications")
                .then()
                .log().ifError()
                .extract();
    }
}
