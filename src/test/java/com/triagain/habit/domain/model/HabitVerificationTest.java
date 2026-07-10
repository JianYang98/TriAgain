package com.triagain.habit.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.triagain.common.exception.BusinessException;
import com.triagain.common.exception.ErrorCode;

class HabitVerificationTest {

	private static final LocalDate TARGET_DATE = LocalDate.now();

	@Nested
	@DisplayName("createText — 텍스트 인증 생성")
	class CreateText {

		@Test
		@DisplayName("정상 콘텐츠면 성공한다")
		void success() {
			HabitVerification verification = HabitVerification.createText(
					"cycle-1", "habit-1", "user-1", "오늘도 물 2L", TARGET_DATE, 1);

			assertThat(verification.getId()).startsWith("HVRF");
			assertThat(verification.getHabitCycleId()).isEqualTo("cycle-1");
			assertThat(verification.getHabitId()).isEqualTo("habit-1");
			assertThat(verification.getTextContent()).isEqualTo("오늘도 물 2L");
			assertThat(verification.getImageUrl()).isNull();
			assertThat(verification.getAttemptNumber()).isEqualTo(1);
		}

		@Test
		@DisplayName("콘텐츠가 공백이면 TEXT_CONTENT_REQUIRED 예외가 발생한다")
		void blankContent_throws() {
			assertThatThrownBy(() -> HabitVerification.createText(
					"cycle-1", "habit-1", "user-1", "  ", TARGET_DATE, 1))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.TEXT_CONTENT_REQUIRED);
		}
	}

	@Nested
	@DisplayName("createPhoto — 사진 인증 생성")
	class CreatePhoto {

		@Test
		@DisplayName("정상 이미지 URL이면 성공한다")
		void success() {
			HabitVerification verification = HabitVerification.createPhoto(
					"cycle-1", "habit-1", "user-1", 123L,
					"https://s3.example.com/image.jpg", null, TARGET_DATE, 2);

			assertThat(verification.getUploadSessionId()).isEqualTo(123L);
			assertThat(verification.getImageUrl()).isEqualTo("https://s3.example.com/image.jpg");
			assertThat(verification.getAttemptNumber()).isEqualTo(2);
		}

		@Test
		@DisplayName("이미지 URL이 없으면 IMAGE_URL_REQUIRED 예외가 발생한다")
		void blankImageUrl_throws() {
			assertThatThrownBy(() -> HabitVerification.createPhoto(
					"cycle-1", "habit-1", "user-1", 123L, "", null, TARGET_DATE, 1))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.IMAGE_URL_REQUIRED);
		}
	}
}
