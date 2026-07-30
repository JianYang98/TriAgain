package com.triagain.user.port.out;

import java.util.List;

/** 회원탈퇴 시 크루 멤버십 정리 — User Context에서 Crew Context 데이터 조회/삭제에 사용 */
public interface CrewMembershipPort {

	/** 유저의 전체 크루 멤버십 조회 */
	List<MembershipInfo> findAllByUserId(String userId);

	/** 크루에서 특정 멤버 제거 */
	void removeMember(String crewId, String userId);

	/** 크루 + 연관 데이터 하드 삭제 — 리더가 혼자인 크루 삭제 시 사용 */
	void deleteCrewWithAllData(String crewId);

	/** 유저의 특정 크루 내 활성 챌린지 종료 처리 */
	void endActiveChallenges(String userId, String crewId);

	/** 가장 오래된 MEMBER에게 리더 자동 위임 — 회원탈퇴 시 LEADER+멤버 있는 크루 처리 */
	void transferLeaderToOldestMember(String crewId, String currentLeaderId);

	record MembershipInfo(String crewId, String role, String crewStatus, int memberCount) {}
}
