package com.triagain.crew.application;

import java.util.List;
import java.util.Map;

import com.triagain.crew.domain.model.Crew;
import com.triagain.crew.domain.vo.CrewStatus;
import com.triagain.crew.port.in.GetCrewByInviteCodeUseCase.CrewInvitePreviewResult;
import com.triagain.crew.port.in.GetCrewByInviteCodeUseCase.MemberSummary;
import com.triagain.crew.port.out.UserPort;
import com.triagain.crew.port.out.UserPort.UserProfile;

/** 크루 미리보기 응답 조립 — 초대코드/크루ID 미리보기에서 공통으로 사용 */
class CrewPreviewAssembler {

	private CrewPreviewAssembler() {
	}

	/** 크루 + 유저 프로필로 미리보기 응답 조립 */
	static CrewInvitePreviewResult toPreviewResult(Crew crew, String userId, UserPort userPort) {
		List<String> memberUserIds = crew.getMembers().stream()
				.map(m -> m.getUserId())
				.toList();
		Map<String, UserProfile> profiles = userPort.findProfilesByIds(memberUserIds);

		List<MemberSummary> members = crew.getMembers().stream()
				.map(m -> {
					UserProfile profile = profiles.get(m.getUserId());
					return new MemberSummary(
							m.getUserId(),
							profile != null ? profile.nickname() : null,
							profile != null ? profile.profileImageUrl() : null,
							m.getRole(),
							m.getJoinedAt()
					);
				})
				.toList();

		String joinBlockedReason = calculateJoinBlockedReason(crew, userId);
		return new CrewInvitePreviewResult(
				crew.getId(),
				crew.getCreatorId(),
				crew.getName(),
				crew.getGoal(),
				crew.getVerificationContent(),
				crew.getVerificationType(),
				crew.getMaxMembers(),
				crew.getCurrentMembers(),
				crew.getStatus(),
				crew.getStartDate(),
				crew.getEndDate(),
				crew.isAllowLateJoin(),
				crew.getDeadlineTime(),
				crew.getCreatedAt(),
				crew.getCategory(),
				crew.getVisibility(),
				members,
				joinBlockedReason == null,
				joinBlockedReason
		);
	}

	/** 가입 차단 사유 계산 — null이면 가입 가능 */
	static String calculateJoinBlockedReason(Crew crew, String userId) {
		if (crew.getMembers().stream().anyMatch(m -> m.getUserId().equals(userId))) {
			return "ALREADY_MEMBER";
		}
		if (crew.getStatus() == CrewStatus.COMPLETED) {
			return "CREW_ENDED";
		}
		if (crew.isFull()) {
			return "CREW_FULL";
		}
		if (crew.getStatus() == CrewStatus.ACTIVE && !crew.isAllowLateJoin()) {
			return "LATE_JOIN_NOT_ALLOWED";
		}
		if (crew.isJoinDeadlinePassed()) {
			return "CREW_JOIN_DEADLINE_PASSED";
		}
		return null;
	}
}
