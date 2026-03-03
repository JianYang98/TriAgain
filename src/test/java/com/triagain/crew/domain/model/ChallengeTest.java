package com.triagain.crew.domain.model;

import com.triagain.common.exception.BusinessException;
import com.triagain.common.exception.ErrorCode;
import com.triagain.crew.domain.vo.ChallengeStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChallengeTest {

    private static final LocalDate TODAY = LocalDate.now();
    private static final LocalDateTime FUTURE_DEADLINE = LocalDateTime.now().plusDays(3);

    @Nested
    @DisplayName("createFirst — 첫 번째 챌린지 생성")
    class CreateFirst {

        @Test
        @DisplayName("사이클 1, targetDays 3, completedDays 0, IN_PROGRESS로 생성된다")
        void success() {
            // Given & When
            Challenge challenge = Challenge.createFirst("user1", "crew1", TODAY, FUTURE_DEADLINE);

            // Then
            assertThat(challenge.getId()).startsWith("CHAL");
            assertThat(challenge.getUserId()).isEqualTo("user1");
            assertThat(challenge.getCrewId()).isEqualTo("crew1");
            assertThat(challenge.getCycleNumber()).isEqualTo(1);
            assertThat(challenge.getTargetDays()).isEqualTo(3);
            assertThat(challenge.getCompletedDays()).isEqualTo(0);
            assertThat(challenge.getStatus()).isEqualTo(ChallengeStatus.IN_PROGRESS);
            assertThat(challenge.getStartDate()).isEqualTo(TODAY);
            assertThat(challenge.getDeadline()).isEqualTo(FUTURE_DEADLINE);
        }
    }

    @Nested
    @DisplayName("createNext — 다음 사이클 챌린지 생성")
    class CreateNext {

        @Test
        @DisplayName("이전 사이클 번호 + 1로 생성된다")
        void success() {
            // Given & When
            Challenge challenge = Challenge.createNext("user1", "crew1", 3, TODAY, FUTURE_DEADLINE);

            // Then
            assertThat(challenge.getCycleNumber()).isEqualTo(4);
            assertThat(challenge.getCompletedDays()).isEqualTo(0);
            assertThat(challenge.getStatus()).isEqualTo(ChallengeStatus.IN_PROGRESS);
        }
    }

    @Nested
    @DisplayName("recordCompletion — 인증 완료 기록")
    class RecordCompletion {

        @Test
        @DisplayName("completedDays가 1 증가하고 IN_PROGRESS를 유지한다")
        void incrementStaysInProgress() {
            // Given
            Challenge challenge = inProgressChallenge(0);

            // When
            challenge.recordCompletion();

            // Then
            assertThat(challenge.getCompletedDays()).isEqualTo(1);
            assertThat(challenge.getStatus()).isEqualTo(ChallengeStatus.IN_PROGRESS);
        }

        @Test
        @DisplayName("completedDays가 targetDays에 도달하면 SUCCESS로 전환된다")
        void completionTriggersSuccess() {
            // Given — 2/3 완료 상태
            Challenge challenge = inProgressChallenge(2);

            // When
            challenge.recordCompletion();

            // Then
            assertThat(challenge.getCompletedDays()).isEqualTo(3);
            assertThat(challenge.getStatus()).isEqualTo(ChallengeStatus.SUCCESS);
        }

        @Test
        @DisplayName("SUCCESS 상태에서 호출하면 예외가 발생한다")
        void alreadySuccess() {
            Challenge challenge = challengeWithStatus(ChallengeStatus.SUCCESS, 3);

            assertThatThrownBy(challenge::recordCompletion)
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.CHALLENGE_NOT_IN_PROGRESS);
        }

        @Test
        @DisplayName("FAILED 상태에서 호출하면 예외가 발생한다")
        void alreadyFailed() {
            Challenge challenge = challengeWithStatus(ChallengeStatus.FAILED, 1);

            assertThatThrownBy(challenge::recordCompletion)
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.CHALLENGE_NOT_IN_PROGRESS);
        }
    }

    @Nested
    @DisplayName("fail — 챌린지 실패 처리")
    class Fail {

        @Test
        @DisplayName("IN_PROGRESS → FAILED 상태 전환에 성공한다")
        void success() {
            // Given
            Challenge challenge = inProgressChallenge(1);

            // When
            challenge.fail();

            // Then
            assertThat(challenge.getStatus()).isEqualTo(ChallengeStatus.FAILED);
        }

        @Test
        @DisplayName("SUCCESS 상태에서 fail하면 예외가 발생한다")
        void alreadySuccess() {
            Challenge challenge = challengeWithStatus(ChallengeStatus.SUCCESS, 3);

            assertThatThrownBy(challenge::fail)
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.CHALLENGE_NOT_IN_PROGRESS);
        }

        @Test
        @DisplayName("FAILED 상태에서 fail하면 예외가 발생한다")
        void alreadyFailed() {
            Challenge challenge = challengeWithStatus(ChallengeStatus.FAILED, 1);

            assertThatThrownBy(challenge::fail)
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.CHALLENGE_NOT_IN_PROGRESS);
        }
    }

    @Nested
    @DisplayName("isDeadlineExceeded — 마감 초과 여부")
    class IsDeadlineExceeded {

        @Test
        @DisplayName("마감일이 미래면 false")
        void notExceeded() {
            Challenge challenge = Challenge.of("CHAL-1", "user1", "crew1", 1, 3, 0,
                    ChallengeStatus.IN_PROGRESS, TODAY,
                    LocalDateTime.now().plusDays(1), LocalDateTime.now());

            assertThat(challenge.isDeadlineExceeded()).isFalse();
        }

        @Test
        @DisplayName("마감일이 과거면 true")
        void exceeded() {
            Challenge challenge = Challenge.of("CHAL-1", "user1", "crew1", 1, 3, 0,
                    ChallengeStatus.IN_PROGRESS, TODAY,
                    LocalDateTime.now().minusDays(1), LocalDateTime.now());

            assertThat(challenge.isDeadlineExceeded()).isTrue();
        }
    }

    // --- 헬퍼 메서드 ---

    private Challenge inProgressChallenge(int completedDays) {
        return Challenge.of("CHAL-1", "user1", "crew1", 1, 3, completedDays,
                ChallengeStatus.IN_PROGRESS, TODAY, FUTURE_DEADLINE, LocalDateTime.now());
    }

    private Challenge challengeWithStatus(ChallengeStatus status, int completedDays) {
        return Challenge.of("CHAL-1", "user1", "crew1", 1, 3, completedDays,
                status, TODAY, FUTURE_DEADLINE, LocalDateTime.now());
    }
}
