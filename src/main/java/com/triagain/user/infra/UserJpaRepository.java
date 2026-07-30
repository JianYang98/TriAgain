package com.triagain.user.infra;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserJpaRepository extends JpaRepository<UserJpaEntity, String> {

	/** deleted_at IS NULL 조건으로 활성 유저만 조회 */
	Optional<UserJpaEntity> findByIdAndDeletedAtIsNull(String id);

	/** 토큰 버전만 경량 조회 — JWT 필터에서 매 요청 검증용 */
	@Query("SELECT u.tokenVersion FROM UserJpaEntity u WHERE u.id = :id AND u.deletedAt IS NULL")
	Optional<Integer> findTokenVersionById(@Param("id") String id);
}
