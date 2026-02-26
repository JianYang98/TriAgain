package com.triagain.crew.infra;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CrewJpaRepository extends JpaRepository<CrewJpaEntity, String> {

    /** 비관적 락으로 크루 조회 — 동시 참여 시 정원 초과 방지 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM CrewJpaEntity c WHERE c.id = :id")
    Optional<CrewJpaEntity> findByIdWithLock(@Param("id") String id);

    /** 초대코드로 크루 조회 */
    Optional<CrewJpaEntity> findByInviteCode(String inviteCode);
}
