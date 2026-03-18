package com.triagain.crew.application;

import com.triagain.crew.domain.model.Crew;
import com.triagain.crew.port.in.SearchCrewsUseCase;
import com.triagain.crew.port.out.CrewRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SearchCrewsService implements SearchCrewsUseCase {

    private final CrewRepositoryPort crewRepositoryPort;

    @Value("${crew.search.min-remaining-days:6}")
    private int minRemainingDays;

    /** 공개 크루 검색 — 초대코드/키워드 판별 후 조회 */
    @Override
    @Transactional(readOnly = true)
    public SearchCrewsResult searchCrews(SearchCrewsQuery query) {
        if (query.isInviteCodeSearch()) {
            return searchByInviteCode(query.keyword());
        }
        return searchByKeyword(query);
    }

    /** 초대코드 검색 — visibility 무관, 정확히 일치하는 1건 반환 */
    private SearchCrewsResult searchByInviteCode(String inviteCode) {
        Optional<Crew> crew = crewRepositoryPort.findByInviteCodeExact(inviteCode.toUpperCase());
        List<CrewSearchItem> items = crew.map(c -> List.of(toSearchItem(c)))
                .orElse(List.of());
        return new SearchCrewsResult(items, false);
    }

    /** 키워드/카테고리 검색 — PUBLIC 크루 + 상태 조건 + 페이지네이션 */
    private SearchCrewsResult searchByKeyword(SearchCrewsQuery query) {
        LocalDate minEndDate = LocalDate.now().plusDays(minRemainingDays);
        int fetchSize = query.size() + 1;

        List<Crew> crews = crewRepositoryPort.searchPublicCrews(
                query.keyword(), query.category(), minEndDate,
                query.offset(), fetchSize
        );

        boolean hasNext = crews.size() > query.size();
        List<CrewSearchItem> items = crews.stream()
                .limit(query.size())
                .map(this::toSearchItem)
                .toList();

        return new SearchCrewsResult(items, hasNext);
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
                crew.getCreatedAt(),
                crew.getVisibility()
        );
    }
}
