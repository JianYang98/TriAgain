package com.triagain.support.domain.vo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.triagain.support.domain.vo.NotificationMessageTemplate.NotificationMessage;

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

	@DisplayName("챌린지 성공 메시지 title 포맷과 content가 정의된 메시지 중 하나이다")
	@Test
	void challengeSuccess_titleFormatAndContentFromPool() {
		// given
		String crewName = "운동 크루";

		// when
		NotificationMessage message = NotificationMessageTemplate.challengeSuccess(crewName);

		// then
		assertThat(message.title()).startsWith(crewName + " | ").endsWith("작심삼일 달성!");
		assertThat(message.content()).isIn(
				"3일 연속 인증 성공! 대단해요!",
				"작심삼일 달성! 다음 사이클도 도전해보세요!");
	}

	@DisplayName("챌린지 실패 메시지 title 포맷과 content가 정의된 메시지 중 하나이다")
	@Test
	void challengeFailed_titleFormatAndContentFromPool() {
		// given
		String crewName = "독서 모임";

		// when
		NotificationMessage message = NotificationMessageTemplate.challengeFailed(crewName);

		// then
		assertThat(message.title()).startsWith(crewName + " | ").endsWith("다시 도전!");
		assertThat(message.content()).isIn(
				"아쉽지만 다음에 다시 도전해요!",
				"괜찮아요, 다시 시작하면 돼요!");
	}
}
