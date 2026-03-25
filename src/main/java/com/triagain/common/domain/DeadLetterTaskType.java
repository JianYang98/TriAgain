package com.triagain.common.domain;

/** 배치 처리 실패 유형 — Dead Letter 분류에 사용 */
public enum DeadLetterTaskType {
    CHALLENGE_FAIL,
    CREW_ACTIVATE,
    CREW_COMPLETE,
    SESSION_EXPIRE
}
