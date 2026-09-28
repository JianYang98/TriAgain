package com.triagain.crew.port.out;

import java.time.OffsetDateTime;

/** Redis 선착순 가입 승인 Port — 승인과 persistence task 등록을 한 연산으로 표현. Redis 자료형·key·JSON은 노출하지 않는다 */
public interface CrewJoinRedisPort {

	/**
	 * 공개 크루 가입 승인 — 중복·정원 판단과 순번·멤버·pending 기록을 원자 실행.
	 * 연결·명령·직렬화·예상 밖 반환은 Status가 아니라 예외(IllegalStateException)로 전달한다.
	 */
	Approval approve(String crewId, String userId, OffsetDateTime confirmedAt);

	enum Status { JOIN_SUCCESS, ALREADY_JOINED, CREW_FULL, NOT_INITIALIZED, INVALID_STATE }

	/** currentMembers는 JOIN_SUCCESS에서만 유효(승인 후 인원), 나머지는 0 */
	record Approval(Status status, int currentMembers) {
	}
}
