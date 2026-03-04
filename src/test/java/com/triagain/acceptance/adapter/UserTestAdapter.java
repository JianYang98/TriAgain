package com.triagain.acceptance.adapter;

import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;

public class UserTestAdapter extends BaseTestAdapter {

    public UserTestAdapter(int port) {
        super(port);
    }

    /** 내 프로필 조회 — GET /users/me (인증) */
    public ExtractableResponse<Response> getMyProfile(String userId) {
        return givenAuthRequest(userId)
                .when()
                .get("/users/me")
                .then()
                .log().ifError()
                .extract();
    }

    /** 내 프로필 조회 — GET /users/me (미인증) */
    public ExtractableResponse<Response> getMyProfileWithoutAuth() {
        return givenRequest()
                .when()
                .get("/users/me")
                .then()
                .extract();
    }

    /** 닉네임 변경 — PATCH /users/me/nickname (인증) */
    public ExtractableResponse<Response> updateNickname(String userId, Object request) {
        return givenAuthRequest(userId)
                .body(request)
                .when()
                .patch("/users/me/nickname")
                .then()
                .log().ifError()
                .extract();
    }

    /** 닉네임 변경 — PATCH /users/me/nickname (미인증) */
    public ExtractableResponse<Response> updateNicknameWithoutAuth(Object request) {
        return givenRequest()
                .body(request)
                .when()
                .patch("/users/me/nickname")
                .then()
                .extract();
    }

    /** 로그아웃 — POST /auth/logout */
    public ExtractableResponse<Response> logout() {
        return givenRequest()
                .when()
                .post("/auth/logout")
                .then()
                .log().ifError()
                .extract();
    }
}