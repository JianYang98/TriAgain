package com.triagain.moderation.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ReportPolicyTest {

    @Nested
    @DisplayName("shouldTriggerReview — 리뷰 트리거 판단")
    class ShouldTriggerReview {

        @Test
        @DisplayName("신고 0건이면 false")
        void zeroReports() {
            assertThat(ReportPolicy.shouldTriggerReview(0)).isFalse();
        }

        @Test
        @DisplayName("신고 2건이면 false")
        void belowThreshold() {
            assertThat(ReportPolicy.shouldTriggerReview(2)).isFalse();
        }

        @Test
        @DisplayName("신고 3건(임계값)이면 true")
        void atThreshold() {
            assertThat(ReportPolicy.shouldTriggerReview(3)).isTrue();
        }

        @Test
        @DisplayName("신고 10건이면 true")
        void aboveThreshold() {
            assertThat(ReportPolicy.shouldTriggerReview(10)).isTrue();
        }
    }

    @Nested
    @DisplayName("isExpired — 리뷰 만료 판단")
    class IsExpired {

        @Test
        @DisplayName("생성 후 6일이면 false")
        void notExpired() {
            LocalDateTime sixDaysAgo = LocalDateTime.now().minusDays(6);
            assertThat(ReportPolicy.isExpired(sixDaysAgo)).isFalse();
        }

        @Test
        @DisplayName("생성 후 8일이면 true")
        void expired() {
            LocalDateTime eightDaysAgo = LocalDateTime.now().minusDays(8);
            assertThat(ReportPolicy.isExpired(eightDaysAgo)).isTrue();
        }
    }
}
