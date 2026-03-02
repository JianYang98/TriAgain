package com.triagain.verification.domain.model;

import com.triagain.common.exception.BusinessException;
import com.triagain.common.exception.ErrorCode;
import com.triagain.verification.domain.vo.ReviewStatus;
import com.triagain.verification.domain.vo.VerificationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VerificationTest {

    private static final LocalDate TODAY = LocalDate.now();

    @Nested
    @DisplayName("createText — 텍스트 인증 생성")
    class CreateText {

        @Test
        @DisplayName("유효한 텍스트로 APPROVED 상태의 인증을 생성한다")
        void success() {
            // Given & When
            Verification v = Verification.createText("chal1", "user1", "crew1",
                    "오늘 30분 독서 완료!", TODAY, 1);

            // Then
            assertThat(v.getId()).startsWith("VRFY");
            assertThat(v.getTextContent()).isEqualTo("오늘 30분 독서 완료!");
            assertThat(v.getImageUrl()).isNull();
            assertThat(v.getUploadSessionId()).isNull();
            assertThat(v.getStatus()).isEqualTo(VerificationStatus.APPROVED);
            assertThat(v.getReviewStatus()).isEqualTo(ReviewStatus.NOT_REQUIRED);
            assertThat(v.getReportCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("textContent가 null이면 예외가 발생한다")
        void textNull() {
            assertThatThrownBy(() -> Verification.createText("chal1", "user1", "crew1",
                    null, TODAY, 1))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.TEXT_CONTENT_REQUIRED);
        }

        @Test
        @DisplayName("textContent가 빈 문자열이면 예외가 발생한다")
        void textBlank() {
            assertThatThrownBy(() -> Verification.createText("chal1", "user1", "crew1",
                    "  ", TODAY, 1))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.TEXT_CONTENT_REQUIRED);
        }
    }

    @Nested
    @DisplayName("createPhoto — 사진 인증 생성")
    class CreatePhoto {

        @Test
        @DisplayName("유효한 이미지 URL과 텍스트로 인증을 생성한다")
        void success() {
            // Given & When
            Verification v = Verification.createPhoto("chal1", "user1", "crew1",
                    1L, "https://s3.amazonaws.com/photo.jpg", "오늘 인증!", TODAY, 1);

            // Then
            assertThat(v.getId()).startsWith("VRFY");
            assertThat(v.getImageUrl()).isEqualTo("https://s3.amazonaws.com/photo.jpg");
            assertThat(v.getTextContent()).isEqualTo("오늘 인증!");
            assertThat(v.getUploadSessionId()).isEqualTo(1L);
            assertThat(v.getStatus()).isEqualTo(VerificationStatus.APPROVED);
            assertThat(v.getReviewStatus()).isEqualTo(ReviewStatus.NOT_REQUIRED);
        }

        @Test
        @DisplayName("textContent가 null이어도 사진 인증은 성공한다")
        void textNullAllowed() {
            Verification v = Verification.createPhoto("chal1", "user1", "crew1",
                    1L, "https://s3.amazonaws.com/photo.jpg", null, TODAY, 1);

            assertThat(v.getTextContent()).isNull();
            assertThat(v.getImageUrl()).isNotNull();
        }

        @Test
        @DisplayName("imageUrl이 null이면 예외가 발생한다")
        void imageUrlNull() {
            assertThatThrownBy(() -> Verification.createPhoto("chal1", "user1", "crew1",
                    1L, null, "텍스트", TODAY, 1))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.IMAGE_URL_REQUIRED);
        }

        @Test
        @DisplayName("imageUrl이 빈 문자열이면 예외가 발생한다")
        void imageUrlBlank() {
            assertThatThrownBy(() -> Verification.createPhoto("chal1", "user1", "crew1",
                    1L, "  ", "텍스트", TODAY, 1))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.IMAGE_URL_REQUIRED);
        }
    }

    @Nested
    @DisplayName("incrementReportCount — 신고 횟수 증가")
    class IncrementReportCount {

        @Test
        @DisplayName("신고 횟수가 1 증가한다")
        void increment() {
            Verification v = approvedVerification();

            v.incrementReportCount();

            assertThat(v.getReportCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("연속 3회 증가하면 3이 된다")
        void incrementThreeTimes() {
            Verification v = approvedVerification();

            v.incrementReportCount();
            v.incrementReportCount();
            v.incrementReportCount();

            assertThat(v.getReportCount()).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("hide — 인증 숨김 처리")
    class Hide {

        @Test
        @DisplayName("HIDDEN 상태로 전환되고 reviewStatus가 PENDING이 된다")
        void success() {
            // Given
            Verification v = approvedVerification();

            // When
            v.hide();

            // Then
            assertThat(v.getStatus()).isEqualTo(VerificationStatus.HIDDEN);
            assertThat(v.getReviewStatus()).isEqualTo(ReviewStatus.PENDING);
        }
    }

    @Nested
    @DisplayName("reject — 인증 거절 처리")
    class Reject {

        @Test
        @DisplayName("REJECTED 상태로 전환되고 reviewStatus가 COMPLETED가 된다")
        void success() {
            // Given
            Verification v = approvedVerification();

            // When
            v.reject();

            // Then
            assertThat(v.getStatus()).isEqualTo(VerificationStatus.REJECTED);
            assertThat(v.getReviewStatus()).isEqualTo(ReviewStatus.COMPLETED);
        }
    }

    @Nested
    @DisplayName("approve — 인증 승인 처리")
    class Approve {

        @Test
        @DisplayName("APPROVED 상태로 전환되고 reviewStatus가 COMPLETED가 된다")
        void success() {
            // Given — HIDDEN 상태에서 복원
            Verification v = hiddenVerification();

            // When
            v.approve();

            // Then
            assertThat(v.getStatus()).isEqualTo(VerificationStatus.APPROVED);
            assertThat(v.getReviewStatus()).isEqualTo(ReviewStatus.COMPLETED);
        }
    }

    // --- 헬퍼 메서드 ---

    private Verification approvedVerification() {
        return Verification.of("VRFY-1", "chal1", "user1", "crew1",
                null, null, "텍스트 인증",
                VerificationStatus.APPROVED, 0, TODAY, 1,
                ReviewStatus.NOT_REQUIRED, LocalDateTime.now());
    }

    private Verification hiddenVerification() {
        return Verification.of("VRFY-1", "chal1", "user1", "crew1",
                1L, "https://s3.amazonaws.com/photo.jpg", "텍스트",
                VerificationStatus.HIDDEN, 3, TODAY, 1,
                ReviewStatus.PENDING, LocalDateTime.now());
    }
}
