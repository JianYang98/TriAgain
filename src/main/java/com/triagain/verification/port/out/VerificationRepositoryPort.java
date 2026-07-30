package com.triagain.verification.port.out;

import com.triagain.verification.domain.model.Verification;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public interface VerificationRepositoryPort {

	Verification save(Verification verification);

	/** 즉시 flush 저장 — 치환 시 옛 행 UPDATE가 새 행 INSERT보다 먼저 반영되도록 보장 */
	Verification saveAndFlush(Verification verification);

	Optional<Verification> findById(String id);

	/** 유효 인증 존재 여부 — CANCELLED 제외 (기존 existsByUserIdAndCrewIdAndTargetDate 대체) */
	boolean existsActiveByUserIdAndCrewIdAndTargetDate(String userId, String crewId, LocalDate targetDate);

	/** 슬롯의 유효(비CANCELLED) 인증 조회 — 오늘 슬롯 현황(todaySlot) 노출에 사용 */
	Optional<Verification> findActiveByUserIdAndCrewIdAndTargetDate(String userId, String crewId, LocalDate targetDate);

	/** 슬롯의 최대 제출 회차 — 없으면 0. 상한 검사·다음 slot_attempt 산출에 사용 */
	int findMaxSlotAttempt(String userId, String crewId, LocalDate targetDate);

	/** 조건부 취소 — APPROVED일 때만 CANCELLED 전이, 영향 행 수 반환(0이면 이미 취소됨) */
	int cancelIfApproved(String verificationId);

	/** APPROVED 인증 날짜 조회 — 크루 기간 범위 내, ASC 정렬 */
	List<LocalDate> findApprovedDatesByUserIdAndCrewId(
			String userId, String crewId, LocalDate startDate, LocalDate endDate);

	/** 오늘 인증 완료한 크루 ID 배치 조회 — N+1 방지용 */
	Set<String> findVerifiedCrewIds(String userId, List<String> crewIds, LocalDate targetDate);

	/** 유저·크루 묶음별 APPROVED 인증 일수 배치 조회 — 홈 완료 탭 verifiedDayCount 집계에 사용 */
	Map<String, Integer> findApprovedDayCountsByCrewIds(String userId, List<String> crewIds);

	/** 오늘 해당 크루 인증 건수 — 첫 인증(count==1) 판정용 */
	long countByCrewIdAndTargetDate(String crewId, LocalDate targetDate);
}
