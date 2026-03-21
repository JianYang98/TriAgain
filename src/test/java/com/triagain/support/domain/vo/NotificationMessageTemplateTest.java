package com.triagain.support.domain.vo;

import com.triagain.support.domain.vo.NotificationMessageTemplate.NotificationMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationMessageTemplateTest {

    @DisplayName("리마인더 메시지 title에 크루명이 prefix로 포함된다")
    @Test
    void reminder_titleContainsCrewNamePrefix() {
        // given
        String crewName = "운동 크루";

        // when
        NotificationMessage message = NotificationMessageTemplate.reminder(crewName);

        // then
        assertThat(message.title()).startsWith(crewName + " | ");
        assertThat(message.title()).endsWith("인증 마감 임박!");
    }

    @DisplayName("리마인더 메시지 content에 플레이스홀더가 없다")
    @Test
    void reminder_contentHasNoPlaceholder() {
        // when
        NotificationMessage message = NotificationMessageTemplate.reminder("운동 크루");

        // then
        assertThat(message.content()).doesNotContain("{crewName}");
        assertThat(message.content()).doesNotContain("{");
    }

    @DisplayName("크루 시작 메시지 title에 크루명이 prefix로 포함된다")
    @Test
    void crewStarted_titleContainsCrewNamePrefix() {
        // given
        String crewName = "독서 모임";

        // when
        NotificationMessage message = NotificationMessageTemplate.crewStarted(crewName);

        // then
        assertThat(message.title()).startsWith(crewName + " | ");
        assertThat(message.title()).endsWith("크루 시작!");
    }

    @DisplayName("크루 시작 메시지 content에 플레이스홀더가 없다")
    @Test
    void crewStarted_contentHasNoPlaceholder() {
        // when
        NotificationMessage message = NotificationMessageTemplate.crewStarted("독서 모임");

        // then
        assertThat(message.content()).doesNotContain("{crewName}");
        assertThat(message.content()).doesNotContain("{");
    }

    @DisplayName("리마인더 메시지의 title과 content가 모두 non-null, non-empty이다")
    @Test
    void reminder_returnsNonEmptyTitleAndContent() {
        // when
        NotificationMessage message = NotificationMessageTemplate.reminder("테스트 크루");

        // then
        assertThat(message.title()).isNotNull().isNotEmpty();
        assertThat(message.content()).isNotNull().isNotEmpty();
    }

    @DisplayName("크루 시작 메시지의 title과 content가 모두 non-null, non-empty이다")
    @Test
    void crewStarted_returnsNonEmptyTitleAndContent() {
        // when
        NotificationMessage message = NotificationMessageTemplate.crewStarted("테스트 크루");

        // then
        assertThat(message.title()).isNotNull().isNotEmpty();
        assertThat(message.content()).isNotNull().isNotEmpty();
    }
}
