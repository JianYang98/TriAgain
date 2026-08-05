package com.triagain.crew.application;

import java.time.LocalDate;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.triagain.crew.domain.model.Crew;
import com.triagain.crew.port.in.SearchCrewsUseCase;
import com.triagain.crew.port.out.CrewRepositoryPort;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SearchCrewsService implements SearchCrewsUseCase {

	private final CrewRepositoryPort crewRepositoryPort;

	@Value("${crew.search.min-remaining-days:6}")
	private int minRemainingDays;

	/** 공개 크루 검색 — 키워드/카테고리 LIKE 검색 */
	@Override
	@Transactional(readOnly = true)
	public SearchCrewsResult searchCrews(SearchCrewsQuery query) {
		return searchByKeyword(query);
	}

	/** 키워드/카테고리 검색 — PUBLIC 크루 + 상태 조건 + 페이지네이션 */
	private SearchCrewsResult searchByKeyword(SearchCrewsQuery query) {
		LocalDate minEndDate = LocalDate.now().plusDays(minRemainingDays);

		CrewRepositoryPort.CrewSearchPage result = crewRepositoryPort.searchPublicCrews(
				query.keyword(), query.category(), minEndDate,
				query.page(), query.size()
		);

		List<CrewSearchItem> items = result.crews().stream()
				.map(this::toSearchItem)
				.toList();

		return new SearchCrewsResult(items, result.hasNext());
	}

	private CrewSearchItem toSearchItem(Crew crew) {
		return new CrewSearchItem(
				crew.getId(),
				crew.getName(),
				crew.getGoal(),
				crew.getVerificationContent(),
				crew.getCategory(),
				crew.getVerificationType(),
				crew.isAllowLateJoin(),
				crew.getCurrentMembers(),
				crew.getMaxMembers(),
				crew.getStatus(),
				crew.getStartDate(),
				crew.getEndDate(),
				crew.getCreatedAt()
		);
	}
}
