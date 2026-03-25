package com.triagain.common.port.out;

import com.triagain.common.domain.DeadLetter;
import com.triagain.common.domain.DeadLetterTaskType;

import java.time.LocalDateTime;
import java.util.List;

public interface DeadLetterRepositoryPort {

    /** Dead Letter 저장 — 배치 처리 실패 기록에 사용 */
    DeadLetter save(DeadLetter deadLetter);

    /** 재시도 대상 조회 — PENDING + next_retry_at 이전인 건 */
    List<DeadLetter> findRetryable(LocalDateTime now);

    /** 태스크 유형별 Dead Letter 조회 — 운영 모니터링에 사용 */
    List<DeadLetter> findByTaskType(DeadLetterTaskType taskType);
}
