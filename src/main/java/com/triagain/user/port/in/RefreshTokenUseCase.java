package com.triagain.user.port.in;

public interface RefreshTokenUseCase {

    /** Refresh Token → 새 Access Token 발급 */
    RefreshResult refresh(RefreshCommand command);

    record RefreshCommand(String refreshToken) {
    }

    record RefreshResult(String accessToken, long accessTokenExpiresIn) {
    }
}
