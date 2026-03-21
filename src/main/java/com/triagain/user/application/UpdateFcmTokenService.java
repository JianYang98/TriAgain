package com.triagain.user.application;

import com.triagain.common.exception.BusinessException;
import com.triagain.common.exception.ErrorCode;
import com.triagain.user.domain.model.User;
import com.triagain.user.port.in.UpdateFcmTokenUseCase;
import com.triagain.user.port.out.UserRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UpdateFcmTokenService implements UpdateFcmTokenUseCase {

    private final UserRepositoryPort userRepositoryPort;

    /** FCM 토큰 갱신 — 앱 실행/로그인 시 클라이언트가 호출 */
    @Override
    @Transactional
    public void updateFcmToken(String userId, String fcmToken) {
        User user = userRepositoryPort.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        user.updateFcmToken(fcmToken);
        userRepositoryPort.save(user);
    }
}
