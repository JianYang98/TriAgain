package com.triagain.verification.infra;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.triagain.verification.domain.vo.VerificationStatus;

public interface VerificationJpaRepository extends JpaRepository<VerificationJpaEntity, String> {

	/** 유효(비CANCELLED) 인증 존재 여부 — 하루 1건 중복 검사(V003)에 사용 */
	boolean existsByUserIdAndCrewIdAndTargetDateAndStatusNot(
			String userId, String crewId, LocalDate targetDate, VerificationStatus status);

	/** 슬롯의 유효(비CANCELLED) 인증 조회 — 오늘 슬롯 현황(todaySlot) 노출에 사용 */
	Optional<VerificationJpaEntity> findByUserIdAndCrewIdAndTargetDateAndStatusNot(
			String userId, String crewId, LocalDate targetDate, VerificationStatus status);

	/** 슬롯의 최대 제출 회차 — 행이 없으면 0 */
	@Query("SELECT COALESCE(MAX(v.slotAttempt), 0) FROM VerificationJpaEntity v "
			+ "WHERE v.userId = :userId AND v.crewId = :crewId AND v.targetDate = :targetDate")
	int findMaxSlotAttempt(
			@Param("userId") String userId,
			@Param("crewId") String crewId,
			@Param("targetDate") LocalDate targetDate);

	/** 조건부 원자적 취소 — APPROVED일 때만 CANCELLED 전이, 영향 행 수 반환(더블탭·순차 재요청 멱등 처리) */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query(value = "UPDATE verifications SET status = 'CANCELLED' "
			+ "WHERE id = :id AND status = 'APPROVED'", nativeQuery = true)
	int cancelIfApproved(@Param("id") String id);

	/** APPROVED 인증 날짜 조회 — 크루 기간 범위 내, ASC 정렬 */
	@Query("SELECT v.targetDate FROM VerificationJpaEntity v " +
		"WHERE v.userId = :userId AND v.crewId = :crewId " +
		"AND v.status = 'APPROVED' " +
		"AND v.targetDate BETWEEN :startDate AND :endDate " +
		"ORDER BY v.targetDate ASC")
	List<LocalDate> findApprovedDatesByUserIdAndCrewId(
			@Param("userId") String userId,
			@Param("crewId") String crewId,
			@Param("startDate") LocalDate startDate,
			@Param("endDate") LocalDate endDate);

	/** 오늘 인증 완료한 크루 ID 배치 조회 — N+1 방지 */
	@Query("SELECT DISTINCT v.crewId FROM VerificationJpaEntity v " +
		"WHERE v.userId = :userId AND v.crewId IN :crewIds AND v.targetDate = :targetDate " +
		"AND v.status = 'APPROVED'")
	List<String> findVerifiedCrewIds(
			@Param("userId") String userId,
			@Param("crewIds") List<String> crewIds,
			@Param("targetDate") LocalDate targetDate);

	/** 유저·크루 묶음별 APPROVED 인증 일수 배치 조회 — crewId→일수 Map 반환 */
	@Query("SELECT v.crewId, COUNT(v) FROM VerificationJpaEntity v "
			+ "WHERE v.userId = :userId AND v.crewId IN :crewIds AND v.status = 'APPROVED' "
			+ "GROUP BY v.crewId")
	List<Object[]> countApprovedDaysGroupByCrewId(
			@Param("userId") String userId,
			@Param("crewIds") List<String> crewIds);

	/** 오늘 해당 크루 인증 건수 — 첫 인증(count==1) 판정용 */
	long countByCrewIdAndTargetDate(String crewId, LocalDate targetDate);
}
