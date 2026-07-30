package com.triagain.user.port.out;

import com.triagain.user.domain.model.User;

import java.util.List;
import java.util.Optional;

public interface UserRepositoryPort {

	User save(User user);

	Optional<User> findById(String id);

	/** 유저 ID 목록으로 일괄 조회 — 크루 상세 참가자 현황에 사용 */
	List<User> findAllByIds(List<String> ids);

	/** 토큰 버전 경량 조회 — JWT 필터에서 매 요청 검증용 */
	Optional<Integer> findTokenVersionById(String userId);

	/** 탈퇴 포함 전체 유저 조회 — 재가입 시 기존 탈퇴 계정 확인용 */
	Optional<User> findByIdIncludingWithdrawn(String id);
}
