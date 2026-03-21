package com.triagain.acceptance.adapter;

import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;

public class NotificationTestAdapter extends BaseTestAdapter {

    public NotificationTestAdapter(int port) {
        super(port);
    }

    /** 내 알림 목록 조회 — GET /notifications */
    public ExtractableResponse<Response> getNotifications(String userId) {
        return givenAuthRequest(userId)
                .when()
                .get("/notifications")
                .then()
                .log().ifError()
                .extract();
    }

    /** 안 읽은 알림 수 조회 — GET /notifications/unread-count */
    public ExtractableResponse<Response> getUnreadCount(String userId) {
        return givenAuthRequest(userId)
                .when()
                .get("/notifications/unread-count")
                .then()
                .log().ifError()
                .extract();
    }

    /** 알림 읽음 처리 — PATCH /notifications/{id}/read */
    public ExtractableResponse<Response> markAsRead(String userId, String notificationId) {
        return givenAuthRequest(userId)
                .when()
                .patch("/notifications/" + notificationId + "/read")
                .then()
                .log().ifError()
                .extract();
    }

    /** FCM 토큰 등록/갱신 — PATCH /users/me/fcm-token */
    public ExtractableResponse<Response> updateFcmToken(String userId, Object request) {
        return givenAuthRequest(userId)
                .body(request)
                .when()
                .patch("/users/me/fcm-token")
                .then()
                .log().ifError()
                .extract();
    }

    /** 미인증 FCM 토큰 요청 — PATCH /users/me/fcm-token (인증 없이) */
    public ExtractableResponse<Response> updateFcmTokenWithoutAuth(Object request) {
        return givenRequest()
                .body(request)
                .when()
                .patch("/users/me/fcm-token")
                .then()
                .log().ifError()
                .extract();
    }
}
