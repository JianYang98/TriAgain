package com.triagain.support.port.out;

public interface CrewMembershipPort {

	/** 크루 멤버십 검증 — 크루 미존재 404(CREW_NOT_FOUND) / 비멤버 403(CREW_ACCESS_DENIED) */
	void validateMembership(String crewId, String userId);
}
