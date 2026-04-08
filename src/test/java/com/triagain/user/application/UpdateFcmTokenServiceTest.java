package com.triagain.user.application;

import com.triagain.common.exception.BusinessException;
import com.triagain.common.exception.ErrorCode;
import com.triagain.user.domain.model.User;
import com.triagain.user.port.out.UserRepositoryPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UpdateFcmTokenServiceTest {

    @Mock
    private UserRepositoryPort userRepositoryPort;

    @InjectMocks
    private UpdateFcmTokenService updateFcmTokenService;

    private static final String USER_ID = "user-1";

    @DisplayName("유저가 존재하면 FCM 토큰이 업데이트되고 save가 호출된다")
    @Test
    void updateFcmToken_success() {
        // given
        User user = User.of(USER_ID, "KAKAO", "test@test.com", "테스트유저",
                null, null, null, null, null, null, 0);
        given(userRepositoryPort.findById(USER_ID)).willReturn(Optional.of(user));

        String newToken = "new-fcm-token-123";

        // when
        updateFcmTokenService.updateFcmToken(USER_ID, newToken);

        // then
        assertThat(user.getFcmToken()).isEqualTo(newToken);
        verify(userRepositoryPort).save(user);
    }

    @DisplayName("유저가 존재하지 않으면 USER_NOT_FOUND 예외가 발생한다")
    @Test
    void updateFcmToken_userNotFound_throwsException() {
        // given
        given(userRepositoryPort.findById(USER_ID)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> updateFcmTokenService.updateFcmToken(USER_ID, "token"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }
}
