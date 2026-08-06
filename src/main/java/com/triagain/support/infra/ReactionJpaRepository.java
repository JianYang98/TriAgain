package com.triagain.support.infra;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.triagain.support.port.out.ReactionRepositoryPort.ReactionRow;

public interface ReactionJpaRepository extends JpaRepository<ReactionJpaEntity, String> {

	/** 리액션이 있는지·현재 이모지가 무엇인지 확인 — 실 DB 레인 테스트 전용(포트에는 없음, 직접 단언용) */
	Optional<ReactionJpaEntity> findByVerificationIdAndUserId(String verificationId, String userId);

	/** 인증의 리액션 총 개수 — 실 DB 레인 테스트 전용(포트에는 없음, 직접 단언용) */
	long countByVerificationId(String verificationId);

	/**
	 * 리액션 upsert — 인증이 APPROVED일 때만 삽입/교체. created_at은 갱신하지 않는다(최초 리액션 시각 고정,
	 * ReactionJpaEntity의 updatable=false와 정합).
	 * <p>
	 * 코드베이스 첫 {@code ON CONFLICT}·첫 {@code INSERT … SELECT}다(src/main 전수 0건). 파라미터 타입
	 * 추론이 실패하면 42P18(could not determine data type of parameter)이 날 수 있다 — 선제적으로 CAST를
	 * 두르지 않고, 실제로 터지면 그때 {@code CAST(:now AS timestamp)}를 넣는다.
	 */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	// @checkstyle:off RegexpSinglelineJava — 텍스트 블록 인덴트는 문자열 내용 (rules.xml Suppression 주석 참조)
	@Query(value = """
			INSERT INTO reactions (id, verification_id, user_id, emoji, created_at)
			SELECT :id, :verificationId, :userId, :emoji, :now
			WHERE EXISTS (
			  SELECT 1
			  FROM verifications v
			  WHERE v.id = :verificationId
			    AND v.status = 'APPROVED'
			)
			ON CONFLICT (verification_id, user_id)
			DO UPDATE SET emoji = EXCLUDED.emoji
			""", nativeQuery = true)
	// @checkstyle:on RegexpSinglelineJava
	int upsertIfVerificationActive(
			@Param("id") String id,
			@Param("verificationId") String verificationId,
			@Param("userId") String userId,
			@Param("emoji") String emoji,
			@Param("now") LocalDateTime now);

	/** 내 리액션 제거 — derived delete, 0행이든 1행이든 정상(멱등) */
	long deleteByVerificationIdAndUserId(String verificationId, String userId);

	/**
	 * 인증 다건의 리액션 요약 원본 행 — APPROVED 인증만(E1-b를 이 쿼리 1곳에서 구조적으로 강제),
	 * 정렬은 {@code created_at, user_id} 고정(Postgres는 순서를 보장하지 않으므로 표시 순서 안정화 목적).
	 */
	// @checkstyle:off RegexpSinglelineJava — 텍스트 블록 인덴트는 문자열 내용 (rules.xml Suppression 주석 참조)
	@Query(value = """
			SELECT r.verification_id AS verificationId,
			       r.user_id         AS userId,
			       u.nickname        AS nickname,
			       r.emoji           AS emoji
			FROM reactions r
			JOIN verifications v
			   ON r.verification_id = v.id
			  AND v.status = 'APPROVED'
			JOIN users u
			   ON u.id = r.user_id
			WHERE r.verification_id IN (:verificationIds)
			ORDER BY r.created_at, r.user_id
			""", nativeQuery = true)
	// @checkstyle:on RegexpSinglelineJava
	List<ReactionRow> findRowsByVerificationIdIn(@Param("verificationIds") List<String> verificationIds);
}
