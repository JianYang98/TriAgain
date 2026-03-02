package com.triagain.acceptance.adapter;

import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;

public class CrewTestAdapter extends BaseTestAdapter {

    public CrewTestAdapter(int port) {
        super(port);
    }

    /** 크루 생성 — POST /crews */
    public ExtractableResponse<Response> createCrew(String userId, Object request) {
        return givenRequest()
                .header("X-User-Id", userId)
                .body(request)
                .when()
                .post("/crews")
                .then()
                .log().ifError()
                .extract();
    }

    /** 초대코드로 크루 참여 — POST /crews/join */
    public ExtractableResponse<Response> joinByInviteCode(String userId, Object request) {
        return givenRequest()
                .header("X-User-Id", userId)
                .body(request)
                .when()
                .post("/crews/join")
                .then()
                .log().ifError()
                .extract();
    }

    /** 내 크루 목록 조회 — GET /crews */
    public ExtractableResponse<Response> getMyCrews(String userId) {
        return givenRequest()
                .header("X-User-Id", userId)
                .when()
                .get("/crews")
                .then()
                .log().ifError()
                .extract();
    }

    /** 크루 상세 조회 — GET /crews/{crewId} */
    public ExtractableResponse<Response> getCrew(String userId, String crewId) {
        return givenRequest()
                .header("X-User-Id", userId)
                .when()
                .get("/crews/" + crewId)
                .then()
                .log().ifError()
                .extract();
    }
}
