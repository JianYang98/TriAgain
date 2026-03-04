package com.triagain.user.port.out;

public interface AppleTokenVerifierPort {

    /** Apple Identity Token 검증 — JWT 서명 + iss/aud/exp 확인 후 사용자 정보 반환 */
    AppleUserInfo verify(String identityToken);

    record AppleUserInfo(String sub, String email) {
    }
}
