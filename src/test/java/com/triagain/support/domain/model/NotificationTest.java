package com.triagain.support.domain.model;

import com.triagain.support.domain.vo.NotificationTargetType;
import com.triagain.support.domain.vo.NotificationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationTest {

    @DisplayName("타겟 없는 알림 생성 시 isRead=false이고 targetType/targetId가 null이다")
    @Test
    void create_withoutTarget_success() {
        // given
        String userId = "user-1";

        // when
        Notification notification = Notification.create(
                userId, NotificationType.REMINDER, "제목", "내용");

        // then
        assertThat(notification.getId()).startsWith("NTFY");
        assertThat(notification.getUserId()).isEqualTo(userId);
        assertThat(notification.getType()).isEqualTo(NotificationType.REMINDER);
        assertThat(notification.getTitle()).isEqualTo("제목");
        assertThat(notification.getContent()).isEqualTo("내용");
        assertThat(notification.isRead()).isFalse();
        assertThat(notification.getTargetType()).isNull();
        assertThat(notification.getTargetId()).isNull();
        assertThat(notification.getCreatedAt()).isNotNull();
    }

    @DisplayName("타겟이 있는 알림 생성 시 targetType과 targetId가 설정된다")
    @Test
    void create_withTarget_success() {
        // given
        String userId = "user-1";
        String crewId = "crew-1";

        // when
        Notification notification = Notification.create(
                userId, NotificationType.CREW_STARTED, "크루 시작!", "내용",
                NotificationTargetType.CREW, crewId);

        // then
        assertThat(notification.isRead()).isFalse();
        assertThat(notification.getTargetType()).isEqualTo(NotificationTargetType.CREW);
        assertThat(notification.getTargetId()).isEqualTo(crewId);
    }

    @DisplayName("markAsRead 호출 시 isRead가 false에서 true로 변경된다")
    @Test
    void markAsRead_changesIsReadToTrue() {
        // given
        Notification notification = Notification.create(
                "user-1", NotificationType.REMINDER, "제목", "내용");
        assertThat(notification.isRead()).isFalse();

        // when
        notification.markAsRead();

        // then
        assertThat(notification.isRead()).isTrue();
    }

    @DisplayName("이미 읽은 알림에 markAsRead를 호출해도 에러 없이 멱등 동작한다")
    @Test
    void markAsRead_alreadyRead_idempotent() {
        // given
        Notification notification = Notification.create(
                "user-1", NotificationType.REMINDER, "제목", "내용");
        notification.markAsRead();
        assertThat(notification.isRead()).isTrue();

        // when
        notification.markAsRead();

        // then
        assertThat(notification.isRead()).isTrue();
    }
}
