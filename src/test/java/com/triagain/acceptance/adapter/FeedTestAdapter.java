package com.triagain.acceptance.adapter;

import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;

public class FeedTestAdapter extends BaseTestAdapter {

    public FeedTestAdapter(int port) {
        super(port);
    }

    /** 크루 피드 조회 — GET /crews/{crewId}/feed */
    public ExtractableResponse<Response> getFeed(String userId, String crewId) {
        return givenRequest()
                .header("X-User-Id", userId)
                .when()
                .get("/crews/" + crewId + "/feed")
                .then()
                .log().ifError()
                .extract();
    }

    /** 크루 피드 페이지 조회 — GET /crews/{crewId}/feed?page={page} */
    public ExtractableResponse<Response> getFeed(String userId, String crewId, int page) {
        return givenRequest()
                .header("X-User-Id", userId)
                .queryParam("page", page)
                .when()
                .get("/crews/" + crewId + "/feed")
                .then()
                .log().ifError()
                .extract();
    }
}
