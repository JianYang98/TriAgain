package com.triagain.acceptance.adapter;

import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;

public class UploadSessionTestAdapter extends BaseTestAdapter {

    public UploadSessionTestAdapter(int port) {
        super(port);
    }

    public ExtractableResponse<Response> createUploadSession(String userId, Object request) {
        return givenRequest()
                .header("X-User-Id", userId)
                .body(request)
                .when()
                .post("/upload-sessions")
                .then()
                .log().ifError()
                .extract();
    }

    /** Lambda 콜백 — PUT /internal/upload-sessions/{id}/complete */
    public ExtractableResponse<Response> completeUploadSession(Long id) {
        return givenRequest()
                .when()
                .put("/internal/upload-sessions/" + id + "/complete")
                .then()
                .log().ifError()
                .extract();
    }
}
