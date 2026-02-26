package com.triagain.crew.infra;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CrewMemberJpaRepository extends JpaRepository<CrewMemberJpaEntity, CrewMemberId> {

    /** 크루 ID로 멤버 목록 조회 — 크루 상세에서 멤버 로딩 시 사용 */
    List<CrewMemberJpaEntity> findByCrewId(String crewId);

    /** 유저 ID로 소속 멤버 목록 조회 — 내 크루 목록에 사용 */
    List<CrewMemberJpaEntity> findByUserId(String userId);
}
