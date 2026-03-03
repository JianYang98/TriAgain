package com.triagain.moderation.domain.model;

import com.triagain.common.exception.BusinessException;
import com.triagain.common.exception.ErrorCode;
import com.triagain.moderation.domain.vo.ReportReason;
import com.triagain.moderation.domain.vo.ReportStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReportTest {

    @Nested
    @DisplayName("create — 신고 생성")
    class Create {

        @Test
        @DisplayName("유효한 값으로 PENDING 상태의 신고를 생성한다")
        void success() {
            // Given & When
            Report report = Report.create("vrfy1", "reporter1", ReportReason.SPAM, "스팸 인증입니다");

            // Then
            assertThat(report.getId()).startsWith("REPT");
            assertThat(report.getVerificationId()).isEqualTo("vrfy1");
            assertThat(report.getReporterId()).isEqualTo("reporter1");
            assertThat(report.getReason()).isEqualTo(ReportReason.SPAM);
            assertThat(report.getStatus()).isEqualTo(ReportStatus.PENDING);
            assertThat(report.getDescription()).isEqualTo("스팸 인증입니다");
        }

        @Test
        @DisplayName("verificationId가 null이면 예외가 발생한다")
        void verificationIdNull() {
            assertThatThrownBy(() -> Report.create(null, "reporter1", ReportReason.SPAM, "설명"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.VERIFICATION_ID_REQUIRED);
        }

        @Test
        @DisplayName("verificationId가 빈 문자열이면 예외가 발생한다")
        void verificationIdBlank() {
            assertThatThrownBy(() -> Report.create("  ", "reporter1", ReportReason.SPAM, "설명"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.VERIFICATION_ID_REQUIRED);
        }

        @Test
        @DisplayName("reporterId가 null이면 예외가 발생한다")
        void reporterIdNull() {
            assertThatThrownBy(() -> Report.create("vrfy1", null, ReportReason.SPAM, "설명"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.REPORTER_ID_REQUIRED);
        }

        @Test
        @DisplayName("reporterId가 빈 문자열이면 예외가 발생한다")
        void reporterIdBlank() {
            assertThatThrownBy(() -> Report.create("vrfy1", "  ", ReportReason.SPAM, "설명"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.REPORTER_ID_REQUIRED);
        }
    }

    @Nested
    @DisplayName("approve — 신고 승인")
    class Approve {

        @Test
        @DisplayName("PENDING → APPROVED 상태 전환에 성공한다")
        void success() {
            // Given
            Report report = pendingReport();

            // When
            report.approve();

            // Then
            assertThat(report.getStatus()).isEqualTo(ReportStatus.APPROVED);
        }

        @Test
        @DisplayName("APPROVED 상태에서 approve하면 예외가 발생한다")
        void alreadyApproved() {
            Report report = reportWithStatus(ReportStatus.APPROVED);

            assertThatThrownBy(report::approve)
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.REPORT_ALREADY_PROCESSED);
        }

        @Test
        @DisplayName("REJECTED 상태에서 approve하면 예외가 발생한다")
        void alreadyRejected() {
            Report report = reportWithStatus(ReportStatus.REJECTED);

            assertThatThrownBy(report::approve)
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.REPORT_ALREADY_PROCESSED);
        }
    }

    @Nested
    @DisplayName("reject — 신고 거절")
    class Reject {

        @Test
        @DisplayName("PENDING → REJECTED 상태 전환에 성공한다")
        void success() {
            // Given
            Report report = pendingReport();

            // When
            report.reject();

            // Then
            assertThat(report.getStatus()).isEqualTo(ReportStatus.REJECTED);
        }

        @Test
        @DisplayName("이미 처리된 신고를 reject하면 예외가 발생한다")
        void alreadyProcessed() {
            Report report = reportWithStatus(ReportStatus.APPROVED);

            assertThatThrownBy(report::reject)
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.REPORT_ALREADY_PROCESSED);
        }
    }

    @Nested
    @DisplayName("expire — 신고 만료")
    class Expire {

        @Test
        @DisplayName("PENDING → EXPIRED 상태 전환에 성공한다")
        void success() {
            // Given
            Report report = pendingReport();

            // When
            report.expire();

            // Then
            assertThat(report.getStatus()).isEqualTo(ReportStatus.EXPIRED);
        }

        @Test
        @DisplayName("이미 처리된 신고를 expire하면 예외가 발생한다")
        void alreadyProcessed() {
            Report report = reportWithStatus(ReportStatus.REJECTED);

            assertThatThrownBy(report::expire)
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.REPORT_ALREADY_PROCESSED);
        }
    }

    // --- 헬퍼 메서드 ---

    private Report pendingReport() {
        return reportWithStatus(ReportStatus.PENDING);
    }

    private Report reportWithStatus(ReportStatus status) {
        return Report.of("REPT-1", "vrfy1", "reporter1",
                ReportReason.SPAM, status, "스팸 인증", LocalDateTime.now());
    }
}
