package com.triagain.user.application;

import com.triagain.common.exception.BusinessException;
import com.triagain.common.exception.ErrorCode;
import com.triagain.user.domain.model.User;
import com.triagain.user.port.in.WithdrawUserUseCase;
import com.triagain.user.port.out.CrewMembershipPort;
import com.triagain.user.port.out.CrewMembershipPort.MembershipInfo;
import com.triagain.user.port.out.UserRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class WithdrawUserService implements WithdrawUserUseCase {

    private final UserRepositoryPort userRepositoryPort;
    private final CrewMembershipPort crewMembershipPort;

    /** 회원탈퇴 처리 — 크루 멤버십 정리 → 개인정보 초기화 → 토큰 무효화 */
    @Override
    @Transactional
    public void withdraw(String userId) {
        User user = userRepositoryPort.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (user.isWithdrawn()) {
            throw new BusinessException(ErrorCode.USER_WITHDRAWN);
        }

        List<MembershipInfo> memberships = crewMembershipPort.findAllByUserId(userId);

        // LEADER + 다른 멤버 있는 크루 존재 → 탈퇴 거부
        boolean hasLeaderCrewWithMembers = memberships.stream()
                .anyMatch(m -> "LEADER".equals(m.role()) && m.memberCount() > 1);
        if (hasLeaderCrewWithMembers) {
            throw new BusinessException(ErrorCode.LEADER_CANNOT_WITHDRAW);
        }

        for (MembershipInfo membership : memberships) {
            // 진행 중 챌린지 종료
            crewMembershipPort.endActiveChallenges(userId, membership.crewId());

            if ("LEADER".equals(membership.role()) && membership.memberCount() == 1) {
                // LEADER + 혼자 → 크루 + 연관 데이터 하드 삭제
                crewMembershipPort.deleteCrewWithAllData(membership.crewId());
            } else {
                // MEMBER → 멤버 제거
                crewMembershipPort.removeMember(membership.crewId(), userId);
            }
        }

        user.withdraw();
        userRepositoryPort.save(user);
    }
}