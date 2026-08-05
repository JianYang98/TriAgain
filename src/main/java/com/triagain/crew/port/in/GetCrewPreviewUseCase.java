package com.triagain.crew.port.in;

import com.triagain.crew.port.in.GetCrewByInviteCodeUseCase.CrewInvitePreviewResult;

public interface GetCrewPreviewUseCase {

	/** 크루 ID로 공개 크루 미리보기 — 검색 결과에서 상세 확인할 때 사용 */
	CrewInvitePreviewResult getCrewPreview(String crewId, String userId);
}
