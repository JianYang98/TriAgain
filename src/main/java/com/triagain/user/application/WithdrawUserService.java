package com.triagain.user.application;

import com.triagain.common.exception.BusinessException;
import com.triagain.common.exception.ErrorCode;
import com.triagain.user.domain.model.User;
import com.triagain.user.port.in.WithdrawUserUseCase;
import com.triagain.user.port.out.AppleOAuthPort;
import com.triagain.user.port.out.CrewMembershipPort;
import com.triagain.user.port.out.CrewMembershipPort.MembershipInfo;
import com.triagain.user.port.out.UserRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class WithdrawUserService implements WithdrawUserUseCase {

	private static final Logger log = LoggerFactory.getLogger(WithdrawUserService.class);
	private static final String PROVIDER_APPLE = "APPLE";

	private final UserRepositoryPort userRepositoryPort;
	private final CrewMembershipPort crewMembershipPort;
	private final AppleOAuthPort appleOAuthPort;
	private final WithdrawUserService self;

	public WithdrawUserService(
			UserRepositoryPort userRepositoryPort,
			CrewMembershipPort crewMembershipPort,
			AppleOAuthPort appleOAuthPort,
			@Lazy WithdrawUserService self
	) {
		this.userRepositoryPort = userRepositoryPort;
		this.crewMembershipPort = crewMembershipPort;
		this.appleOAuthPort = appleOAuthPort;
		this.self = self;
	}

	/**
	 * 회원탈퇴 진입점 — 검증 → (Apple) revoke 호출(트랜잭션 밖) → 트랜잭션 안에서 크루 정리/익명화/토큰 무효화.
	 * Apple revoke는 실패해도 탈퇴는 graceful 진행 (App Store 5.1.1(v) 요건은 "성실한 시도").
	 */
	@Override
	public void withdraw(String userId) {
		User user = userRepositoryPort.findById(userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

		if (user.isWithdrawn()) {
			throw new BusinessException(ErrorCode.USER_WITHDRAWN);
		}

		// Apple 사용자: revoke 호출 (트랜잭션 밖, graceful — 어댑터가 graceful이지만 서비스에서도 방어적으로 wrap)
		if (PROVIDER_APPLE.equals(user.getProvider()) && user.getAppleRefreshToken() != null) {
			try {
				appleOAuthPort.revokeRefreshToken(user.getAppleRefreshToken());
			} catch (Exception e) {
				log.warn("Apple revoke 호출 중 예외 발생 (탈퇴는 계속 진행): userId={}, message={}", userId, e.getMessage());
			}
		}

		self.completeWithdraw(userId);
	}

	/** 회원탈퇴 트랜잭션 영역 — 크루 정리 + 개인정보 초기화 + 토큰 무효화 */
	@Transactional
	public void completeWithdraw(String userId) {
		User user = userRepositoryPort.findById(userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

		List<MembershipInfo> memberships = crewMembershipPort.findAllByUserId(userId);
		for (MembershipInfo membership : memberships) {
			// 진행 중 챌린지 종료
			crewMembershipPort.endActiveChallenges(userId, membership.crewId());

			if ("LEADER".equals(membership.role()) && membership.memberCount() == 1) {
				// LEADER + 혼자 → 크루 + 연관 데이터 하드 삭제
				crewMembershipPort.deleteCrewWithAllData(membership.crewId());
			} else if ("LEADER".equals(membership.role()) && membership.memberCount() > 1) {
				// LEADER + 다른 멤버 → 가장 오래된 멤버에게 자동 위임 후 본인 제거
				crewMembershipPort.transferLeaderToOldestMember(membership.crewId(), userId);
				crewMembershipPort.removeMember(membership.crewId(), userId);
			} else {
				// MEMBER → 멤버 제거
				crewMembershipPort.removeMember(membership.crewId(), userId);
			}
		}

		user.withdraw();
		userRepositoryPort.save(user);
	}
}