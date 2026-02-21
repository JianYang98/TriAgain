package com.triagain.crew.infra;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CrewJpaRepository extends JpaRepository<CrewJpaEntity, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM CrewJpaEntity c WHERE c.id = :id")
    Optional<CrewJpaEntity> findByIdWithLock(@Param("id") String id);

    Optional<CrewJpaEntity> findByInviteCode(String inviteCode);
}
