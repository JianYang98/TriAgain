package com.triagain.common.infra;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.triagain.common.domain.DeadLetterStatus;
import com.triagain.common.domain.DeadLetterTaskType;

public interface DeadLetterJpaRepository extends JpaRepository<DeadLetterJpaEntity, String> {

	/** PENDING 상태 + 재시도 시각이 된 Dead Letter 조회 */
	List<DeadLetterJpaEntity> findByStatusAndNextRetryAtBefore(DeadLetterStatus status, LocalDateTime now);

	/** 태스크 유형별 조회 */
	List<DeadLetterJpaEntity> findByTaskType(DeadLetterTaskType taskType);
}
