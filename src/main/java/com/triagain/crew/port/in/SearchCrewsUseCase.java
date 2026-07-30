package com.triagain.crew.port.in;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.triagain.crew.domain.vo.CrewCategory;
import com.triagain.crew.domain.vo.CrewStatus;
import com.triagain.crew.domain.vo.VerificationType;

public interface SearchCrewsUseCase {

	/** 공개 크루 검색 — 비로그인 사용자도 조회 가능 */
	SearchCrewsResult searchCrews(SearchCrewsQuery query);

	record SearchCrewsQuery(
			String keyword,
			CrewCategory category,
			int page,
			int size
	) {
		public SearchCrewsQuery {
			if (page < 0) page = 0;
			if (size <= 0) size = 20;
			if (size > 50) size = 50;
		}
	}

	record SearchCrewsResult(
			List<CrewSearchItem> crews,
			boolean hasNext
	) {}

	record CrewSearchItem(
			String id,
			String name,
			String goal,
			String verificationContent,
			CrewCategory category,
			VerificationType verificationType,
			boolean allowLateJoin,
			int currentMembers,
			int maxMembers,
			CrewStatus status,
			LocalDate startDate,
			LocalDate endDate,
			LocalDateTime createdAt
	) {}
}
