package com.triagain.support.application;

import com.triagain.common.exception.BusinessException;
import com.triagain.common.exception.ErrorCode;
import com.triagain.support.domain.model.Notification;
import com.triagain.support.domain.vo.NotificationType;
import com.triagain.support.port.out.NotificationRepositoryPort;
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

import org.mockito.ArgumentCaptor;

@ExtendWith(MockitoExtension.class)
class ReadNotificationServiceTest {

    @Mock
    private NotificationRepositoryPort notificationRepositoryPort;

    @InjectMocks
    private ReadNotificationService readNotificationService;

    private static final String USER_ID = "user-1";
    private static final String NOTIFICATION_ID = "NTFY-001";

    @DisplayName("본인 알림 읽음 처리 시 도메인 markAsRead 후 save가 호출된다")
    @Test
    void markAsRead_success() {
        // given
        Notification notification = Notification.create(USER_ID, NotificationType.REMINDER, "제목", "내용");
        given(notificationRepositoryPort.findById(NOTIFICATION_ID))
                .willReturn(Optional.of(notification));

        // when
        readNotificationService.markAsRead(NOTIFICATION_ID, USER_ID);

        // then
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepositoryPort).save(captor.capture());
        assertThat(captor.getValue().isRead()).isTrue();
    }

    @DisplayName("존재하지 않는 알림 읽음 처리 시 NOTIFICATION_NOT_FOUND 예외가 발생한다")
    @Test
    void markAsRead_notificationNotFound_throwsException() {
        // given
        given(notificationRepositoryPort.findById(NOTIFICATION_ID))
                .willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> readNotificationService.markAsRead(NOTIFICATION_ID, USER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOTIFICATION_NOT_FOUND);
    }

    @DisplayName("타인의 알림 읽음 처리 시 NOTIFICATION_NOT_FOUND 예외가 발생한다")
    @Test
    void markAsRead_otherUserNotification_throwsException() {
        // given
        Notification notification = Notification.create("other-user", NotificationType.REMINDER, "제목", "내용");
        given(notificationRepositoryPort.findById(NOTIFICATION_ID))
                .willReturn(Optional.of(notification));

        // when & then
        assertThatThrownBy(() -> readNotificationService.markAsRead(NOTIFICATION_ID, USER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOTIFICATION_NOT_FOUND);
    }
}
