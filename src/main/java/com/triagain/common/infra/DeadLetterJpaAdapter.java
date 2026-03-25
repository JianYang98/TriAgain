package com.triagain.common.infra;

import com.triagain.common.domain.DeadLetter;
import com.triagain.common.domain.DeadLetterStatus;
import com.triagain.common.domain.DeadLetterTaskType;
import com.triagain.common.port.out.DeadLetterRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class DeadLetterJpaAdapter implements DeadLetterRepositoryPort {

    private final DeadLetterJpaRepository deadLetterJpaRepository;

    /** Dead Letter 저장 */
    @Override
    public DeadLetter save(DeadLetter deadLetter) {
        DeadLetterJpaEntity entity = DeadLetterJpaEntity.fromDomain(deadLetter);
        return deadLetterJpaRepository.save(entity).toDomain();
    }

    /** 재시도 대상 조회 — PENDING + next_retry_at 이전인 건 */
    @Override
    public List<DeadLetter> findRetryable(LocalDateTime now) {
        return deadLetterJpaRepository.findByStatusAndNextRetryAtBefore(DeadLetterStatus.PENDING, now)
                .stream()
                .map(DeadLetterJpaEntity::toDomain)
                .toList();
    }

    /** 태스크 유형별 Dead Letter 조회 */
    @Override
    public List<DeadLetter> findByTaskType(DeadLetterTaskType taskType) {
        return deadLetterJpaRepository.findByTaskType(taskType)
                .stream()
                .map(DeadLetterJpaEntity::toDomain)
                .toList();
    }
}
