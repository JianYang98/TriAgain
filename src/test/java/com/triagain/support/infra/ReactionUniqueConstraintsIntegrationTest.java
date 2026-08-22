package com.triagain.support.infra;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

/**
 * V26 유니크 제약(uk_reactions_verification_user) 실 DB 검증 — 마이그레이션 쪽을 잠근다.
 * <p>
 * {@link ReactionIntegrationTestBase}(Flyway ON, ddl-auto=validate)를 상속하므로 스키마 출처가
 * <b>마이그레이션</b>이다. 여기서 통과한다는 것은 <b>V26 이 제약을 만들었다</b>는 뜻이다.
 * <p>
 * 짝: {@code ReactionEntityConstraintIntegrationTest}(create-drop 레인)가 <b>엔티티 어노테이션</b> 쪽을
 * 잠근다. 둘은 한 쌍이며 하나만으로는 부족하다 — {@code ddl-auto=validate} 는 유니크 제약을 대조하지
 * 않으므로, 이 테스트가 그린이어도 엔티티 선언이 빠졌는지는 알 수 없다.
 * <p>
 * ⚠️ 검증 대상은 <b>제약의 존재</b>뿐이다. 제약 <b>이름</b>은 검증하지 않는다 — {@code ON CONFLICT} 는
 * 이름이 아니라 <b>컬럼</b>을 대상으로 하고 {@code CONSTRAINT_ERRORS} 매핑도 쓰지 않기로 판정했으므로
 * (2026-08 리액션 SDD) 이름이 갈라져도 동작 차이가 0이다.
 */
class ReactionUniqueConstraintsIntegrationTest extends ReactionIntegrationTestBase {

	@PersistenceContext
	private EntityManager entityManager;

	@Test
	@DisplayName("같은 (verification_id, user_id) 로 두 번 INSERT 하면 유니크 제약이 거부한다")
	@Transactional
	void duplicateVerificationUserPair_violatesUniqueConstraint() {
		insertReaction("RCTN-uk-1", "VRFY-uk", "USER-uk", "LIKE");

		assertThatThrownBy(() -> insertReaction("RCTN-uk-2", "VRFY-uk", "USER-uk", "FIRE"))
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
