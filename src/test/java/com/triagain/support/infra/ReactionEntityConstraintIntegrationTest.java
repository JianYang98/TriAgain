package com.triagain.support.infra;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import com.triagain.e2e.E2eTestBase;

/**
 * 리액션 유니크 제약이 <b>엔티티 어노테이션</b> 쪽에도 살아 있는지 잠근다 — drift 방어.
 * <p>
 * {@link E2eTestBase} 는 {@code flyway.enabled=false} + {@code ddl-auto=create-drop} 이라
 * 스키마 출처가 마이그레이션이 아니라 <b>JPA 엔티티</b>다({@code ReactionJpaEntity} 의
 * {@code @UniqueConstraint}). 그 선언이 사라지면 인수·E2E 계층에서 upsert 의 {@code ON CONFLICT} 가
 * <b>42P10</b>(no unique or exclusion constraint matching)으로 죽는데, 마이그레이션만 보는 테스트는
 * 그걸 잡지 못한다.
 * <p>
 * 짝: {@code ReactionUniqueConstraintsIntegrationTest}(Flyway ON 레인)가 <b>V26</b> 쪽을 잠근다.
 * 결정 3의 그 테스트와 이 테스트는 <b>한 쌍</b>이다 — {@code ddl-auto=validate} 는 유니크 제약을
 * 대조하지 않아 엔티티↔V26 일치를 보증하지 못하기 때문이다.
 * <p>
 * ⚠️ 검증 대상은 <b>제약의 존재</b>뿐이다. 제약 <b>이름이 V26 과 같은지는 검증하지 않는다</b> —
 * {@code ON CONFLICT} 는 이름이 아니라 <b>컬럼</b>을 대상으로 하고 {@code CONSTRAINT_ERRORS} 매핑도
 * 쓰지 않기로 판정했으므로(2026-08 리액션 SDD) 이름이 갈라져도 동작 차이가 0이다.
 * 이름 일치 검증 테스트를 새로 만들지 마라.
 */
class ReactionEntityConstraintIntegrationTest extends E2eTestBase {

	@PersistenceContext
	private EntityManager entityManager;

	@Test
	@DisplayName("create-drop 스키마에서도 같은 (verification_id, user_id) 중복 INSERT 가 거부된다")
	@Transactional
	void duplicatePair_isRejectedInEntityDerivedSchema() {
		insertReaction("RCTN-drift-1", "VRFY-drift", "USER-drift", "LIKE");

		assertThatThrownBy(() -> insertReaction("RCTN-drift-2", "VRFY-drift", "USER-drift", "FIRE"))
				// raw EntityManager 경로는 Spring 예외 변환을 타지 않아 Hibernate 예외가 그대로 올라온다.
				// 잠그려는 건 "제약이 거부한다"이지 래퍼 타입이 아니므로 둘 다 허용한다.
				.isInstanceOfAny(DataIntegrityViolationException.class, ConstraintViolationException.class);
	}

	private void insertReaction(String id, String verificationId, String userId, String emoji) {
		entityManager.createNativeQuery(
						"INSERT INTO reactions (id, verification_id, user_id, emoji, created_at) "
								+ "VALUES (:id, :vid, :uid, :emoji, CURRENT_TIMESTAMP)")
				.setParameter("id", id)
				.setParameter("vid", verificationId)
				.setParameter("uid", userId)
				.setParameter("emoji", emoji)
				.executeUpdate();
		entityManager.flush();
	}
}
