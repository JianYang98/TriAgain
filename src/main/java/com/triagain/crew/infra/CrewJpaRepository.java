package com.triagain.crew.infra;

import com.triagain.crew.domain.vo.CrewStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface CrewJpaRepository extends JpaRepository<CrewJpaEntity, String> {

    /** 비관적 락으로 크루 조회 — 동시 참여 시 정원 초과 방지 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM CrewJpaEntity c WHERE c.id = :id")
    Optional<CrewJpaEntity> findByIdWithLock(@Param("id") String id);

    /** 초대코드로 크루 조회 */
    Optional<CrewJpaEntity> findByInviteCode(String inviteCode);

    /** 기간 만료된 특정 상태 크루 조회 — 크루 종료 스케줄러에서 사용 */
    List<CrewJpaEntity> findAllByStatusAndEndDateBefore(CrewStatus status, LocalDate date);

    /** 시작일 도래한 특정 상태 크루 조회 — 서버 시작 시 활성화 보정에 사용 */
    List<CrewJpaEntity> findAllByStatusAndStartDateLessThanEqual(CrewStatus status, LocalDate date);
}
