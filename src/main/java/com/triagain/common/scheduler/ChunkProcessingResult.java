package com.triagain.common.scheduler;

import java.util.List;

/** 청크 처리 결과 — 성공/실패 건수와 실패 아이템 목록 */
public record ChunkProcessingResult<T>(int successCount, int failedCount, List<FailedItem<T>> failedItems, List<T> successItems) {
}
