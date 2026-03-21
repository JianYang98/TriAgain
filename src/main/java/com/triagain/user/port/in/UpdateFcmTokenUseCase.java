package com.triagain.user.port.in;

public interface UpdateFcmTokenUseCase {

    /** FCM 토큰 갱신 — 앱 실행/로그인 시 클라이언트가 호출 */
    void updateFcmToken(String userId, String fcmToken);
}
