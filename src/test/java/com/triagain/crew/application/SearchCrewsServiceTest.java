package com.triagain.crew.application;

import com.triagain.crew.domain.model.Crew;
import com.triagain.crew.domain.vo.CrewCategory;
import com.triagain.crew.domain.vo.CrewStatus;
import com.triagain.crew.domain.vo.CrewVisibility;
import com.triagain.crew.domain.vo.VerificationType;
import com.triagain.crew.port.in.SearchCrewsUseCase.SearchCrewsQuery;
import com.triagain.crew.port.in.SearchCrewsUseCase.SearchCrewsResult;
import com.triagain.crew.port.out.CrewRepositoryPort;
import com.triagain.crew.port.out.CrewRepositoryPort.CrewSearchPage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SearchCrewsServiceTest {

    @Mock
    private CrewRepositoryPort crewRepositoryPort;

    @InjectMocks
    private SearchCrewsService searchCrewsService;

    @Nested
    @DisplayName("키워드 검색")
    class KeywordSearch {

        @Test
        @DisplayName("키워드로 공개 크루를 검색하면 결과가 반환된다")
        void searchByKeyword_returnsResults() {
            // Given
            ReflectionTestUtils.setField(searchCrewsService, "minRemainingDays", 6);
            Crew crew = publicCrew("CREW-1", "운동 크루", CrewCategory.EXERCISE);
            given(crewRepositoryPort.searchPublicCrews(eq("운동"), any(), any(), eq(0), eq(20)))
                    .willReturn(new CrewSearchPage(List.of(crew), false));

            SearchCrewsQuery query = new SearchCrewsQuery("운동", null, 0, 20);

            // When
            SearchCrewsResult result = searchCrewsService.searchCrews(query);

            // Then
            assertThat(result.crews()).hasSize(1);
            assertThat(result.crews().get(0).name()).isEqualTo("운동 크루");
            assertThat(result.hasNext()).isFalse();
        }

        @Test
        @DisplayName("카테고리 필터로 검색하면 해당 카테고리만 반환된다")
        void searchByCategory_returnsFiltered() {
            // Given
            ReflectionTestUtils.setField(searchCrewsService, "minRemainingDays", 6);
            Crew crew = publicCrew("CREW-1", "스터디 크루", CrewCategory.STUDY);
            given(crewRepositoryPort.searchPublicCrews(any(), eq(CrewCategory.STUDY), any(), eq(0), eq(20)))
                    .willReturn(new CrewSearchPage(List.of(crew), false));

            SearchCrewsQuery query = new SearchCrewsQuery(null, CrewCategory.STUDY, 0, 20);

            // When
            SearchCrewsResult result = searchCrewsService.searchCrews(query);

            // Then
            assertThat(result.crews()).hasSize(1);
            assertThat(result.crews().get(0).category()).isEqualTo(CrewCategory.STUDY);
        }

        @Test
        @DisplayName("결과가 없으면 빈 리스트가 반환된다")
        void searchNoResults_returnsEmpty() {
            // Given
            ReflectionTestUtils.setField(searchCrewsService, "minRemainingDays", 6);
            given(crewRepositoryPort.searchPublicCrews(any(), any(), any(), anyInt(), anyInt()))
                    .willReturn(new CrewSearchPage(Collections.emptyList(), false));

            SearchCrewsQuery query = new SearchCrewsQuery("존재하지않는크루", null, 0, 20);

            // When
            SearchCrewsResult result = searchCrewsService.searchCrews(query);

            // Then
            assertThat(result.crews()).isEmpty();
            assertThat(result.hasNext()).isFalse();
        }
    }

    @Nested
    @DisplayName("페이지네이션")
    class Pagination {

        @Test
        @DisplayName("다음 페이지가 존재하면 hasNext가 true이다")
        void hasNext_whenMoreResults() {
            // Given
            ReflectionTestUtils.setField(searchCrewsService, "minRemainingDays", 6);
            List<Crew> crews = List.of(
                    publicCrew("CREW-1", "크루1", CrewCategory.ETC),
                    publicCrew("CREW-2", "크루2", CrewCategory.ETC)
            );
            given(crewRepositoryPort.searchPublicCrews(any(), any(), any(), eq(0), eq(2)))
                    .willReturn(new CrewSearchPage(crews, true));

            SearchCrewsQuery query = new SearchCrewsQuery(null, null, 0, 2);

            // When
            SearchCrewsResult result = searchCrewsService.searchCrews(query);

            // Then
            assertThat(result.crews()).hasSize(2);
            assertThat(result.hasNext()).isTrue();
        }

        @Test
        @DisplayName("결과가 size 이하이면 hasNext가 false이다")
        void noHasNext_whenExactResults() {
            // Given
            ReflectionTestUtils.setField(searchCrewsService, "minRemainingDays", 6);
            List<Crew> crews = List.of(
                    publicCrew("CREW-1", "크루1", CrewCategory.ETC)
            );
            given(crewRepositoryPort.searchPublicCrews(any(), any(), any(), eq(0), eq(20)))
                    .willReturn(new CrewSearchPage(crews, false));

            SearchCrewsQuery query = new SearchCrewsQuery(null, null, 0, 20);

            // When
            SearchCrewsResult result = searchCrewsService.searchCrews(query);

            // Then
            assertThat(result.crews()).hasSize(1);
            assertThat(result.hasNext()).isFalse();
        }
    }

    @Nested
    @DisplayName("초대코드 검색")
    class InviteCodeSearch {

        @Test
        @DisplayName("유효한 초대코드 문자 6자리면 초대코드로 정확히 검색한다")
        void inviteCodeSearch_exactMatch() {
            // Given
            Crew crew = publicCrew("CREW-1", "초대 크루", CrewCategory.ETC);
            given(crewRepositoryPort.findByInviteCodeForSearch("ABC234")).willReturn(Optional.of(crew));

            SearchCrewsQuery query = new SearchCrewsQuery("abc234", null, 0, 20);

            // When
            SearchCrewsResult result = searchCrewsService.searchCrews(query);

            // Then
            assertThat(result.crews()).hasSize(1);
            assertThat(result.hasNext()).isFalse();
            verify(crewRepositoryPort, never()).searchPublicCrews(any(), any(), any(), anyInt(), anyInt());
        }

        @Test
        @DisplayName("존재하지 않는 초대코드면 빈 결과가 반환된다")
        void inviteCodeSearch_notFound() {
            // Given
            given(crewRepositoryPort.findByInviteCodeForSearch("ZZZ999")).willReturn(Optional.empty());

            SearchCrewsQuery query = new SearchCrewsQuery("ZZZ999", null, 0, 20);

            // When
            SearchCrewsResult result = searchCrewsService.searchCrews(query);

            // Then
            assertThat(result.crews()).isEmpty();
        }

        @Test
        @DisplayName("7자리 키워드면 초대코드가 아닌 일반 검색으로 동작한다")
        void sevenCharKeyword_notInviteCode() {
            // Given
            ReflectionTestUtils.setField(searchCrewsService, "minRemainingDays", 6);
            given(crewRepositoryPort.searchPublicCrews(eq("ABCDEFG"), any(), any(), anyInt(), anyInt()))
                    .willReturn(new CrewSearchPage(Collections.emptyList(), false));

            SearchCrewsQuery query = new SearchCrewsQuery("ABCDEFG", null, 0, 20);

            // When
            searchCrewsService.searchCrews(query);

            // Then
            verify(crewRepositoryPort).searchPublicCrews(eq("ABCDEFG"), any(), any(), anyInt(), anyInt());
            verify(crewRepositoryPort, never()).findByInviteCodeForSearch(any());
        }
    }

    // --- 헬퍼 메서드 ---

    private static Crew publicCrew(String id, String name, CrewCategory category) {
        return Crew.of(id, "creator-1", name, "목표",
                "인증 내용", VerificationType.TEXT, 10, 1, CrewStatus.RECRUITING,
                LocalDate.now().plusDays(1), LocalDate.now().plusDays(30), true,
                "ABC123", LocalDateTime.now(), LocalTime.of(23, 59, 59),
                category, CrewVisibility.PUBLIC, Collections.emptyList());
    }
}
