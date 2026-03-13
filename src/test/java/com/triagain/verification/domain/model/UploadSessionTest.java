package com.triagain.verification.domain.model;

import com.triagain.common.exception.BusinessException;
import com.triagain.common.exception.ErrorCode;
import com.triagain.verification.domain.vo.UploadSessionStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UploadSessionTest {

    @Nested
    @DisplayName("create — 업로드 세션 생성")
    class Create {

        @Test
        @DisplayName("유효한 값으로 PENDING 상태의 세션을 생성한다")
        void success() {
            // Given & When
            UploadSession session = UploadSession.create("user1", "crew1", "images/photo.jpg", "image/jpeg");

            // Then
            assertThat(session.getId()).isNull();
            assertThat(session.getUserId()).isEqualTo("user1");
            assertThat(session.getImageKey()).isEqualTo("images/photo.jpg");
            assertThat(session.getContentType()).isEqualTo("image/jpeg");
            assertThat(session.getStatus()).isEqualTo(UploadSessionStatus.PENDING);
        }

        @Test
        @DisplayName("userId가 null이면 예외가 발생한다")
        void userIdNull() {
            assertThatThrownBy(() -> UploadSession.create(null, "crew1", "key", "image/jpeg"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.USER_ID_REQUIRED);
        }

        @Test
        @DisplayName("userId가 빈 문자열이면 예외가 발생한다")
        void userIdBlank() {
            assertThatThrownBy(() -> UploadSession.create("  ", "crew1", "key", "image/jpeg"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.USER_ID_REQUIRED);
        }

        @Test
        @DisplayName("imageKey가 null이면 예외가 발생한다")
        void imageKeyNull() {
            assertThatThrownBy(() -> UploadSession.create("user1", "crew1", null, "image/jpeg"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.IMAGE_KEY_REQUIRED);
        }

        @Test
        @DisplayName("imageKey가 빈 문자열이면 예외가 발생한다")
        void imageKeyBlank() {
            assertThatThrownBy(() -> UploadSession.create("user1", "crew1", "  ", "image/jpeg"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.IMAGE_KEY_REQUIRED);
        }
    }

    @Nested
    @DisplayName("complete — 세션 완료 처리")
    class Complete {

        @Test
        @DisplayName("PENDING → COMPLETED 상태 전환에 성공한다")
        void success() {
            // Given
            UploadSession session = pendingSession();

            // When
            session.complete();

            // Then
            assertThat(session.getStatus()).isEqualTo(UploadSessionStatus.COMPLETED);
        }

        @Test
        @DisplayName("이미 COMPLETED이면 멱등 처리로 상태를 유지한다")
        void idempotent() {
            // Given
            UploadSession session = sessionWithStatus(UploadSessionStatus.COMPLETED);

            // When
            session.complete();

            // Then
            assertThat(session.getStatus()).isEqualTo(UploadSessionStatus.COMPLETED);
        }

        @Test
        @DisplayName("EXPIRED 상태에서 complete하면 예외가 발생한다")
        void expired() {
            UploadSession session = sessionWithStatus(UploadSessionStatus.EXPIRED);

            assertThatThrownBy(session::complete)
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.UPLOAD_SESSION_NOT_PENDING);
        }
    }

    @Nested
    @DisplayName("expire — 세션 만료 처리")
    class Expire {

        @Test
        @DisplayName("PENDING → EXPIRED 상태 전환에 성공한다")
        void success() {
            // Given
            UploadSession session = pendingSession();

            // When
            session.expire();

            // Then
            assertThat(session.getStatus()).isEqualTo(UploadSessionStatus.EXPIRED);
        }

        @Test
        @DisplayName("COMPLETED 상태에서 expire하면 예외가 발생한다")
        void completed() {
            UploadSession session = sessionWithStatus(UploadSessionStatus.COMPLETED);

            assertThatThrownBy(session::expire)
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.UPLOAD_SESSION_NOT_PENDING);
        }

        @Test
        @DisplayName("EXPIRED 상태에서 expire하면 예외가 발생한다")
        void alreadyExpired() {
            UploadSession session = sessionWithStatus(UploadSessionStatus.EXPIRED);

            assertThatThrownBy(session::expire)
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.UPLOAD_SESSION_NOT_PENDING);
        }
    }

    @Nested
    @DisplayName("상태 확인 메서드")
    class StatusChecks {

        @Test
        @DisplayName("PENDING이면 isPending=true, isCompleted=false")
        void pending() {
            UploadSession session = pendingSession();
            assertThat(session.isPending()).isTrue();
            assertThat(session.isCompleted()).isFalse();
        }

        @Test
        @DisplayName("COMPLETED이면 isPending=false, isCompleted=true")
        void completed() {
            UploadSession session = sessionWithStatus(UploadSessionStatus.COMPLETED);
            assertThat(session.isPending()).isFalse();
            assertThat(session.isCompleted()).isTrue();
        }

        @Test
        @DisplayName("EXPIRED이면 isPending=false, isCompleted=false")
        void expired() {
            UploadSession session = sessionWithStatus(UploadSessionStatus.EXPIRED);
            assertThat(session.isPending()).isFalse();
            assertThat(session.isCompleted()).isFalse();
        }
    }

    // --- 헬퍼 메서드 ---

    private UploadSession pendingSession() {
        return sessionWithStatus(UploadSessionStatus.PENDING);
    }

    private UploadSession sessionWithStatus(UploadSessionStatus status) {
        LocalDateTime now = LocalDateTime.now();
        return UploadSession.of(1L, "user1", "crew1", "images/photo.jpg", "image/jpeg", status, now, now);
    }
}
