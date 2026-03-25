package com.triagain.common.scheduler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class ChunkProcessorTest {

    @Mock
    private TransactionTemplate transactionTemplate;

    private ChunkProcessor chunkProcessor;

    @BeforeEach
    void setUp() {
        lenient().doAnswer(invocation -> {
            invocation.<Consumer<TransactionStatus>>getArgument(0).accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());

        chunkProcessor = new ChunkProcessor(transactionTemplate);
    }

    @Test
    @DisplayName("청크 실패 후 rehydrator로 fresh 인스턴스를 재조회하여 건별 재시도 성공한다")
    void chunkFailure_rehydratesAndRetries() {
        // Given — 상태 변이를 시뮬레이션하는 가변 아이템
        MutableItem item1 = new MutableItem("id-1", "PENDING");
        MutableItem item2 = new MutableItem("id-2", "PENDING");

        AtomicInteger chunkCallCount = new AtomicInteger(0);

        // 청크 트랜잭션: 첫 호출에서 item1 상태 변이 후 item2에서 예외 발생
        // per-item retry: rehydrator가 fresh 인스턴스 반환 → 정상 처리
        doAnswer(invocation -> {
            Consumer<TransactionStatus> action = invocation.getArgument(0);
            int call = chunkCallCount.incrementAndGet();
            if (call == 1) {
                // 첫 번째 호출: 청크 트랜잭션 — 예외 발생시켜 롤백 시뮬레이션
                try {
                    action.accept(null);
                } catch (Exception e) {
                    throw e;
                }
            } else {
                // 이후 호출: per-item retry
                action.accept(null);
            }
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());

        // processor: PENDING이 아니면 예외 (상태 가드 시뮬레이션)
        Consumer<MutableItem> processor = item -> {
            if (!"PENDING".equals(item.status)) {
                throw new IllegalStateException("상태가 PENDING이 아닙니다: " + item.status);
            }
            item.status = "DONE";
        };

        // 첫 청크 트랜잭션에서 item1은 DONE으로 변이되고, item2에서 예외 → 청크 롤백
        // DB는 원복되지만 인메모리 item1.status는 이미 "DONE"
        // rehydrator 없이는 per-item retry 시 item1이 상태 가드에 걸림
        // rehydrator가 fresh PENDING 인스턴스를 반환하므로 성공

        // When
        ChunkProcessingResult<MutableItem> result = chunkProcessor.execute(
                List.of(item1, item2), 50, processor,
                stale -> new MutableItem(stale.id, "PENDING")  // DB에서 fresh 조회 시뮬레이션
        );

        // Then — rehydrator 덕분에 2건 모두 성공
        assertThat(result.successCount()).isEqualTo(2);
        assertThat(result.failedCount()).isEqualTo(0);
        assertThat(result.failedItems()).isEmpty();
    }

    @Test
    @DisplayName("rehydrator 없는 오버로드는 동일 인스턴스로 재시도한다")
    void withoutRehydrator_retryUseSameInstance() {
        // Given
        AtomicInteger processCount = new AtomicInteger(0);

        // 첫 호출(청크): 예외, 이후(per-item): 성공
        AtomicInteger txCallCount = new AtomicInteger(0);
        doAnswer(invocation -> {
            Consumer<TransactionStatus> action = invocation.getArgument(0);
            int call = txCallCount.incrementAndGet();
            if (call == 1) {
                try {
                    action.accept(null);
                } catch (Exception e) {
                    throw e;
                }
            } else {
                action.accept(null);
            }
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());

        // processor: 첫 호출에서 예외, 이후 성공
        Consumer<String> processor = item -> {
            if (processCount.incrementAndGet() == 1) {
                throw new RuntimeException("transient error");
            }
        };

        // When — 3-arg 오버로드 (rehydrator 없음)
        ChunkProcessingResult<String> result = chunkProcessor.execute(
                List.of("item-1"), 50, processor
        );

        // Then — per-item retry에서 동일 인스턴스로 재시도
        assertThat(result.successCount()).isEqualTo(1);
        assertThat(result.failedCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("청크 성공 시 rehydrator는 호출되지 않는다")
    void chunkSuccess_rehydratorNotCalled() {
        // Given
        AtomicInteger rehydrateCount = new AtomicInteger(0);

        // When
        ChunkProcessingResult<String> result = chunkProcessor.execute(
                List.of("a", "b", "c"), 50,
                item -> {},
                stale -> {
                    rehydrateCount.incrementAndGet();
                    return stale;
                }
        );

        // Then
        assertThat(result.successCount()).isEqualTo(3);
        assertThat(rehydrateCount.get()).isEqualTo(0);
    }

    // --- 테스트 헬퍼 ---

    private static class MutableItem {
        final String id;
        String status;

        MutableItem(String id, String status) {
            this.id = id;
            this.status = status;
        }
    }
}
