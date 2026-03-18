package com.triagain.crew.port.in;

import com.triagain.crew.domain.model.Crew;
import com.triagain.crew.domain.vo.CrewCategory;
import com.triagain.crew.domain.vo.CrewStatus;
import com.triagain.crew.domain.vo.CrewVisibility;
import com.triagain.crew.domain.vo.VerificationType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Pattern;

public interface SearchCrewsUseCase {

    /** 공개 크루 검색 — 비로그인 사용자도 조회 가능 */
    SearchCrewsResult searchCrews(SearchCrewsQuery query);

    record SearchCrewsQuery(
            String keyword,
            CrewCategory category,
            int page,
            int size
    ) {
        private static final Pattern INVITE_CODE_PATTERN =
                Pattern.compile("^[" + Crew.INVITE_CODE_CHARS + "]{6}$");

        public SearchCrewsQuery {
            if (page < 0) page = 0;
            if (size <= 0) size = 20;
            if (size > 50) size = 50;
        }

        /** 페이지네이션 오프셋 계산 */
        public int offset() {
            return page * size;
        }

        /** 초대코드 검색 여부 판별 — INVITE_CODE_CHARS 문자 집합의 6자리만 매칭 */
        public boolean isInviteCodeSearch() {
            return keyword != null && INVITE_CODE_PATTERN.matcher(keyword.toUpperCase()).matches();
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
            LocalDateTime createdAt,
            CrewVisibility visibility
    ) {}
}
