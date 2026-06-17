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
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * T6: 트랜잭션 커밋/롤백에 따른 CrewFirstVerificationEvent 발행 여부 — AFTER_COMMIT 검증
 *
 * <p>커밋 케이스: TestTransaction.flagForCommit() → AFTER_COMMIT 리스너 @Async 발화
 * → CountDownLatch(await 5s) 로 동기화 후 mock 호출 1회 검증.
 * <p>롤백 케이스: 기본 롤백 → AFTER_COMMIT 미발화 → await(1s) 미만 → verify(never()) 검증.
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

        /** @Async 발화 동기화용 래치 — 각 테스트에서 직접 세팅 */
        static CountDownLatch latch;

        @Bean
        @Primary
        VerificationNotificationPort verificationNotificationPort() {
            VerificationNotificationPort mock = mock(VerificationNotificationPort.class);
            willAnswer(invocation -> {
                if (latch != null) {
                    latch.countDown();
                }
                return null;
            }).given(mock).sendCrewFirstVerificationNotification(anyString(), anyString(), any(LocalDate.class));
            return mock;
        }
    }

    @Test
    @DisplayName("T6-commit: 트랜잭션 커밋 시 AFTER_COMMIT 리스너가 실행된다")
    @Transactional(propagation = Propagation.REQUIRED)
    void commit_triggerListener() throws InterruptedException {
        // Given — 래치 세팅 후 커밋 플래그
        CountDownLatch latch = new CountDownLatch(1);
        MockConfig.latch = latch;
        TestTransaction.flagForCommit();

        // When
        eventPublisher.publishEvent(
                new CrewFirstVerificationEvent("user-1", "crew-1", LocalDate.now()));

        // @Async + AFTER_COMMIT: 트랜잭션 종료(커밋) 후 별도 스레드에서 발화
        // 트랜잭션은 @Transactional 테스트 메서드 종료 시 커밋됨 — 여기서는 아직 미발화
        // → TestTransaction.end()로 트랜잭션을 명시적으로 종료해 AFTER_COMMIT 발화 유도
        TestTransaction.end();

        // Then — 최대 5초 대기
        boolean fired = latch.await(5, TimeUnit.SECONDS);
        assertThat(fired).as("AFTER_COMMIT 리스너가 5초 내 발화되어야 함").isTrue();
        verify(verificationNotificationPort)
                .sendCrewFirstVerificationNotification(anyString(), anyString(), any(LocalDate.class));
    }

    @Test
    @DisplayName("T6-rollback: 트랜잭션 롤백 시 AFTER_COMMIT 리스너가 실행되지 않는다")
    @Transactional(propagation = Propagation.REQUIRED)
    void rollback_doesNotTriggerListener() throws InterruptedException {
        // Given — 롤백(기본값) — 래치 없이 미발화 확인
        MockConfig.latch = null;

        // When
        eventPublisher.publishEvent(
                new CrewFirstVerificationEvent("user-1", "crew-1", LocalDate.now()));

        // TestTransaction.end()로 롤백 트랜잭션 종료
        TestTransaction.end();

        // Then — 1초 대기 후 호출 없음 확인
        Thread.sleep(1_000);
        verify(verificationNotificationPort, never())
                .sendCrewFirstVerificationNotification(anyString(), anyString(), any(LocalDate.class));
    }
}
