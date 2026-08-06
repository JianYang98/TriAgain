package com.triagain.support.infra;

import org.springframework.stereotype.Component;

import com.triagain.crew.port.in.CrewMembershipQueryUseCase;
import com.triagain.support.port.out.CrewMembershipPort;

import lombok.RequiredArgsConstructor;

/**
 * support 컨텍스트의 {@link CrewMembershipPort} 구현 — crew {@code CrewMembershipQueryUseCase}에 위임만 한다
 * (선례: {@code verification/infra/CrewMembershipAdapter.java:20-21}).
 * <p>
 * 클래스명은 "CrewMembershipAdapter"가 아니라 "SupportCrewMembershipAdapter"다 — verification 패키지에
 * 동명의 {@code @Component} 클래스가 이미 있어, 같은 이름을 쓰면 컴포넌트 스캔 시 빈 이름이 충돌한다
 * (선례: {@code user.port.out.CrewMembershipPort}를 구현하는 {@code crew.infra.adapter.UserCrewMembershipAdapter}도
 * 같은 이유로 컨텍스트 접두어를 붙였다).
 */
@Component
@RequiredArgsConstructor
public class SupportCrewMembershipAdapter implements CrewMembershipPort {

	private final CrewMembershipQueryUseCase crewMembershipQueryUseCase;

	// 의도적 중복: ①NO(위임 시그니처가 어긋나면 컴파일 에러로 즉시 드러난다, 한쪽만 고쳐 조용히 깨지는 버그가 아니다)
	// ②NO(crew 쪽 계약이 바뀌면 여기도 같이 바뀌어야 하나 컴파일러가 강제한다) ③NO(계약이 아니라 배선 코드)
	// — 판정 3문 전부 NO (verification/infra/CrewMembershipAdapter.java:20-21과 동형의 위임)
	/** 크루 멤버십 검증 — crew CrewMembershipQueryUseCase에 위임 */
	@Override
	public void validateMembership(String crewId, String userId) {
		crewMembershipQueryUseCase.validateMembership(crewId, userId);
	}
}
