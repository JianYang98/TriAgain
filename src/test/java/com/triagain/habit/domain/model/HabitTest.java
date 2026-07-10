package com.triagain.habit.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.triagain.common.exception.BusinessException;
import com.triagain.common.exception.ErrorCode;
import com.triagain.habit.domain.vo.HabitStatus;
import com.triagain.habit.domain.vo.HabitVerificationType;

class HabitTest {

	@Nested
	@DisplayName("create — 습관 등록")
	class Create {

		@Test
		@DisplayName("status=ACTIVE, endedAt=null로 생성된다")
		void success() {
			// Given & When
			Habit habit = Habit.create("user-1", "매일 물 2L", HabitVerificationType.TEXT, LocalTime.of(22, 0, 0));

			// Then
			assertThat(habit.getId()).startsWith("HBIT");
			assertThat(habit.getUserId()).isEqualTo("user-1");
			assertThat(habit.getName()).isEqualTo("매일 물 2L");
			assertThat(habit.getVerificationType()).isEqualTo(HabitVerificationType.TEXT);
			assertThat(habit.getDeadlineTime()).isEqualTo(LocalTime.of(22, 0, 0));
			assertThat(habit.getStatus()).isEqualTo(HabitStatus.ACTIVE);
			assertThat(habit.getEndedAt()).isNull();
		}

		@Test
		@DisplayName("deadlineTime 미지정 시 기본값 23:59:59가 적용된다")
		void defaultDeadlineTime() {
			// Given & When
			Habit habit = Habit.create("user-1", "매일 물 2L", HabitVerificationType.TEXT, null);

			// Then
			assertThat(habit.getDeadlineTime()).isEqualTo(LocalTime.of(23, 59, 59));
		}

		@Test
		@DisplayName("이름이 공백이면 INVALID_INPUT 예외가 발생한다")
		void blankName_throws() {
			assertThatThrownBy(() -> Habit.create("user-1", "  ", HabitVerificationType.TEXT, null))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.INVALID_INPUT);
		}

		@Test
		@DisplayName("이름이 50자를 초과하면 INVALID_INPUT 예외가 발생한다")
		void tooLongName_throws() {
			String tooLong = "a".repeat(51);

			assertThatThrownBy(() -> Habit.create("user-1", tooLong, HabitVerificationType.TEXT, null))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.INVALID_INPUT);
		}
	}

	@Nested
	@DisplayName("rename — 습관 이름 수정")
	class Rename {

		@Test
		@DisplayName("이름이 변경된다")
		void success() {
			Habit habit = activeHabit();

			habit.rename("새 이름");

			assertThat(habit.getName()).isEqualTo("새 이름");
		}

		@Test
		@DisplayName("공백 이름으로 변경 시도 시 INVALID_INPUT 예외가 발생한다")
		void blankName_throws() {
			Habit habit = activeHabit();

			assertThatThrownBy(() -> habit.rename(" "))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.INVALID_INPUT);
		}
	}

	@Nested
	@DisplayName("pause/resume — 멈춤·재개")
	class PauseResume {

		@Test
		@DisplayName("pause() 호출 시 PAUSED로 전환된다")
		void pause_setsPaused() {
			Habit habit = activeHabit();

			habit.pause();

			assertThat(habit.getStatus()).isEqualTo(HabitStatus.PAUSED);
		}

		@Test
		@DisplayName("resume() 호출 시 ACTIVE로 전환된다")
		void resume_setsActive() {
			Habit habit = activeHabit();
			habit.pause();

			habit.resume();

			assertThat(habit.getStatus()).isEqualTo(HabitStatus.ACTIVE);
		}

		@Test
		@DisplayName("ACTIVE에서 resume() 호출해도 그대로 ACTIVE(no-op)")
		void resumeWhileActive_noop() {
			Habit habit = activeHabit();

			habit.resume();

			assertThat(habit.getStatus()).isEqualTo(HabitStatus.ACTIVE);
		}
	}

	@Nested
	@DisplayName("end — 습관 종료")
	class End {

		@Test
		@DisplayName("status=ENDED, endedAt이 set된다(터미널)")
		void success() {
			Habit habit = activeHabit();

			habit.end();

			assertThat(habit.getStatus()).isEqualTo(HabitStatus.ENDED);
			assertThat(habit.getEndedAt()).isNotNull();
		}
	}

	private Habit activeHabit() {
		return Habit.create("user-1", "매일 물 2L", HabitVerificationType.TEXT, LocalTime.of(23, 59, 59));
	}
}
