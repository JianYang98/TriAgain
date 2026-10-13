package com.triagain.crew.infra;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CrewMemberJpaRepository extends JpaRepository<CrewMemberJpaEntity, String> {

	/** Redis 가입 멱등 INSERT — crew/user 충돌만 0행, PK 등 다른 제약 오류는 전파 */
	@Modifying
	@Query(value = "INSERT INTO crew_members (id, crew_id, user_id, role, joined_at) "
		+ "VALUES (:id, :crewId, :userId, :role, :joinedAt) "
		+ "ON CONFLICT (crew_id, user_id) DO NOTHING", nativeQuery = true)
	int insertIfAbsent(@Param("id") String id, @Param("crewId") String crewId,
		@Param("userId") String userId, @Param("role") String role,
		@Param("joinedAt") LocalDateTime joinedAt);

	/** 크루 ID로 멤버 목록 조회 — 크루 상세에서 멤버 로딩 시 사용 */
	List<CrewMemberJpaEntity> findByCrewId(String crewId);

	/** 유저 ID로 소속 멤버 목록 조회 — 내 크루 목록에 사용 */
	List<CrewMemberJpaEntity> findByUserId(String userId);

	/** 크루의 전체 멤버 삭제 — 크루 삭제 시 사용 */
	void deleteByCrewId(String crewId);

	/** 특정 크루의 특정 멤버 삭제 — 크루 탈퇴 시 사용 */
	void deleteByCrewIdAndUserId(String crewId, String userId);
}
