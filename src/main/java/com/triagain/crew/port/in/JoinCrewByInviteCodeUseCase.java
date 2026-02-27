package com.triagain.crew.port.in;

import com.triagain.crew.domain.vo.CrewRole;

import java.time.LocalDateTime;

public interface JoinCrewByInviteCodeUseCase {

    /** 초대코드로 크루 참여 — 공유 링크를 통해 참여할 때 사용 */
    JoinByInviteCodeResult joinByInviteCode(JoinByInviteCodeCommand command);

    record JoinByInviteCodeCommand(String userId, String inviteCode) {
    }

    record JoinByInviteCodeResult(String userId, String crewId, CrewRole role, int currentMembers, LocalDateTime joinedAt) {
    }
}
