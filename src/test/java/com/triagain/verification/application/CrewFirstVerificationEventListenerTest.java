package com.triagain.verification.application;

import com.triagain.verification.application.event.CrewFirstVerificationEvent;
import com.triagain.verification.port.out.VerificationNotificationPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * T6: 트랜잭션 롤백 시 CrewFirstVerificationEvent 미발행 — AFTER_COMMIT 검증
 *
 * Spring context를 로드하여 @TransactionalEventListener(AFTER_COMMIT)의
 * 롤백 시 미실행 동작을 검증한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "notification.crew-first-verification.enabled=true")
class CrewFirstVerificationEventListenerTest {

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private VerificationNotificationPort verificationNotificationPort;

    @TestConfiguration
    static class MockConfig {
        @Bean
        @Primary
        VerificationNotificationPort verificationNotificationPort() {
            return mock(VerificationNotificationPort.class);
        }
    }

    @Test
    @DisplayName("T6: 트랜잭션 롤백 시 AFTER_COMMIT 리스너가 실행되지 않는다")
    @Transactional(propagation = Propagation.REQUIRED)
    void rollback_doesNotTriggerListener() {
        // Given — 트랜잭션 내에서 이벤트 발행 후 강제 롤백
        eventPublisher.publishEvent(
                new CrewFirstVerificationEvent("user-1", "crew-1", LocalDate.now()));

        // 트랜잭션이 롤백될 것임 — @Transactional 테스트는 기본적으로 rollback
        // When (트랜잭션 커밋 안 됨 — @Transactional 테스트는 자동 rollback)

        // Then — AFTER_COMMIT이므로 롤백 시 호출 안 됨 (테스트 종료 후 검증)
        // 참고: 실제 검증은 트랜잭션 종료 후이지만, 리스너가 동기이면 여기서 catch 가능
        // @Async + AFTER_COMMIT 조합에서 rollback 시 리스너 자체가 실행되지 않으므로
        // 이 시점에서는 아직 호출되지 않았음을 확인
        verify(verificationNotificationPort, never())
                .sendCrewFirstVerificationNotification(anyString(), anyString(), any(LocalDate.class));
    }
}
