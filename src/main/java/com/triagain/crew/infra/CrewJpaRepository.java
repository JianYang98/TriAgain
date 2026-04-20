package com.triagain.crew.infra;

import com.triagain.crew.domain.vo.CrewCategory;
import com.triagain.crew.domain.vo.CrewStatus;
import com.triagain.crew.domain.vo.CrewVisibility;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
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

    /** 낙관적 락 — version 일치 시만 current_members 갱신 + version 증가 */
    @Modifying
    @Query("UPDATE CrewJpaEntity c SET c.currentMembers = :currentMembers, c.version = c.version + 1 " +
            "WHERE c.id = :id AND c.version = :version")
    int updateCurrentMembersWithVersion(
            @Param("id") String id,
            @Param("currentMembers") int currentMembers,
            @Param("version") Long version);

    /** 공개 크루 검색 — 키워드/카테고리 필터 + 상태 조건 */
    @Query("SELECT c FROM CrewJpaEntity c WHERE c.visibility = :visibility " +
            "AND (c.status = 'RECRUITING' OR (c.status = 'ACTIVE' AND c.allowLateJoin = true AND c.endDate >= :minEndDate)) " +
            "AND (:keyword IS NULL OR LOWER(c.name) LIKE :keyword ESCAPE '\\' OR LOWER(c.goal) LIKE :keyword ESCAPE '\\') " +
            "AND (:category IS NULL OR c.category = :category) " +
            "ORDER BY c.createdAt DESC")
    Slice<CrewJpaEntity> searchPublicCrews(
            @Param("visibility") CrewVisibility visibility,
            @Param("keyword") String keyword,
            @Param("category") CrewCategory category,
            @Param("minEndDate") LocalDate minEndDate,
            Pageable pageable
    );
}
