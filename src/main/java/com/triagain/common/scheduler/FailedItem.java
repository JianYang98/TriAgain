package com.triagain.common.scheduler;

/** 청크 처리 중 실패한 개별 아이템 — 에러 메시지와 함께 수집 */
public record FailedItem<T>(T item, String errorMessage) {
}
