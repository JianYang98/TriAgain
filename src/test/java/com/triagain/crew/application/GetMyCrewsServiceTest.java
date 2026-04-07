package com.triagain.crew.application;

import com.triagain.crew.domain.model.Crew;
import com.triagain.crew.domain.model.CrewMember;
import com.triagain.crew.domain.vo.CrewCategory;
import com.triagain.crew.domain.vo.CrewStatus;
import com.triagain.crew.domain.vo.CrewVisibility;
import com.triagain.crew.domain.vo.VerificationType;
import com.triagain.crew.port.in.GetMyCrewsUseCase.CrewSummaryResult;
import com.triagain.crew.port.out.CrewRepositoryPort;
import com.triagain.crew.port.out.VerificationQueryPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class GetMyCrewsServiceTest {

    @Mock
    private CrewRepositoryPort crewRepositoryPort;

    @Mock
    private VerificationQueryPort verificationQueryPort;

    @InjectMocks
    private GetMyCrewsService getMyCrewsService;

    private static final String USER_ID = "user-1";

    @Test
    @DisplayName("ACTIVE 크루 2개 중 1개만 인증하면 todayVerified가 정확히 반영된다")
    void getMyCrews_partialVerification_todayVerifiedAccurate() {
        // Given
        Crew activeCrew1 = activeCrew("crew-1", "운동 크루");
        Crew activeCrew2 = activeCrew("crew-2", "독서 크루");

        given(crewRepositoryPort.findAllByUserId(USER_ID))
                .willReturn(List.of(activeCrew1, activeCrew2));
        given(verificationQueryPort.findVerifiedCrewIds(eq(USER_ID), eq(List.of("crew-1", "crew-2")), any(LocalDate.class)))
                .willReturn(Set.of("crew-1"));

        // When
        List<CrewSummaryResult> results = getMyCrewsService.getMyCrews(USER_ID);

        // Then
        assertThat(results).hasSize(2);
        assertThat(results.stream().filter(r -> r.id().equals("crew-1")).findFirst().get().todayVerified()).isTrue();
        assertThat(results.stream().filter(r -> r.id().equals("crew-2")).findFirst().get().todayVerified()).isFalse();
    }

    @Test
    @DisplayName("RECRUITING 크루는 인증 배치 쿼리 대상에서 제외된다")
    void getMyCrews_recruitingCrew_excludedFromVerificationQuery() {
        // Given
        Crew recruitingCrew = recruitingCrew("crew-r", "준비 중 크루");

        given(crewRepositoryPort.findAllByUserId(USER_ID))
                .willReturn(List.of(recruitingCrew));
        given(verificationQueryPort.findVerifiedCrewIds(eq(USER_ID), eq(Collections.emptyList()), any(LocalDate.class)))
                .willReturn(Set.of());

        // When
        List<CrewSummaryResult> results = getMyCrewsService.getMyCrews(USER_ID);

        // Then
        assertThat(results).hasSize(1);
        assertThat(results.get(0).todayVerified()).isFalse();
        verify(verificationQueryPort).findVerifiedCrewIds(eq(USER_ID), eq(Collections.emptyList()), any(LocalDate.class));
    }

    @Test
    @DisplayName("크루가 없으면 빈 리스트를 반환한다")
    void getMyCrews_noCrews_returnsEmptyList() {
        // Given
        given(crewRepositoryPort.findAllByUserId(USER_ID)).willReturn(Collections.emptyList());
        given(verificationQueryPort.findVerifiedCrewIds(eq(USER_ID), eq(Collections.emptyList()), any(LocalDate.class)))
                .willReturn(Set.of());

        // When
        List<CrewSummaryResult> results = getMyCrewsService.getMyCrews(USER_ID);

        // Then
        assertThat(results).isEmpty();
    }

    // --- 테스트 헬퍼 ---

    private static Crew activeCrew(String crewId, String name) {
        return Crew.of(crewId, "creator-1", name, "목표", "인증 내용",
                VerificationType.TEXT, 10, 3, CrewStatus.ACTIVE,
                LocalDate.now().minusDays(1), LocalDate.now().plusDays(5),
                false, "ABC123", LocalDateTime.now().minusDays(1),
                LocalTime.of(23, 59, 59), CrewCategory.EXERCISE,
                CrewVisibility.PRIVATE, List.of());
    }

    private static Crew recruitingCrew(String crewId, String name) {
        return Crew.of(crewId, "creator-1", name, "목표", "인증 내용",
                VerificationType.TEXT, 10, 1, CrewStatus.RECRUITING,
                LocalDate.now().plusDays(1), LocalDate.now().plusDays(7),
                false, "DEF456", LocalDateTime.now(),
                LocalTime.of(23, 59, 59), CrewCategory.STUDY,
                CrewVisibility.PRIVATE, List.of());
    }
}
