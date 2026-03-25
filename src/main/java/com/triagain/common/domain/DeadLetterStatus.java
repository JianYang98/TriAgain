package com.triagain.common.domain;

/** Dead Letter 처리 상태 */
public enum DeadLetterStatus {
    PENDING,
    RESOLVED,
    ABANDONED
}
