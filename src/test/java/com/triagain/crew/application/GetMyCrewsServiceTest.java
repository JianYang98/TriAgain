package com.triagain.crew.application;

import com.triagain.crew.domain.model.Crew;
import com.triagain.crew.domain.vo.CrewCategory;
import com.triagain.crew.domain.vo.CrewStatus;
import com.triagain.crew.domain.vo.CrewVisibility;
import com.triagain.crew.domain.vo.VerificationType;
import com.triagain.crew.port.in.GetMyCrewsUseCase.CrewSummaryResult;
import com.triagain.crew.port.out.ChallengeRepositoryPort;
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
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class GetMyCrewsServiceTest {

    @Mock
    private CrewRepositoryPort crewRepositoryPort;

    @Mock
    private VerificationQueryPort verificationQueryPort;

    @Mock
    private ChallengeRepositoryPort challengeRepositoryPort;

    @InjectMocks
    private GetMyCrewsService getMyCrewsService;

    private static final String USER_ID = "user-1";

    // ─────────────────────────────────────────────────────────────
    // 기존 todayVerified 회귀 테스트
    // ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("ACTIVE 크루 2개 중 1개만 인증하면 todayVerified가 정확히 반영된다")
    void getMyCrews_partialVerification_todayVerifiedAccurate() {
        // Given
        Crew activeCrew1 = activeCrew("crew-1", "운동 크루");
        Crew activeCrew2 = activeCrew("crew-2", "독서 크루");

        given(crewRepositoryPort.findAllByUserId(USER_ID))
                .willReturn(List.of(activeCrew1, activeCrew2));
        given(verificationQueryPort.findVerifiedCrewIds(
                eq(USER_ID), eq(List.of("crew-1", "crew-2")), any(LocalDate.class)))
                .willReturn(Set.of("crew-1"));
        given(verificationQueryPort.findApprovedDayCountsByCrewIds(eq(USER_ID), eq(Collections.emptyList())))
                .willReturn(Map.of());
        given(challengeRepositoryPort.findSuccessCountsByUserIdAndCrewIds(eq(USER_ID), eq(Collections.emptyList())))
                .willReturn(Map.of());

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
        given(verificationQueryPort.findVerifiedCrewIds(
                eq(USER_ID), eq(Collections.emptyList()), any(LocalDate.class)))
                .willReturn(Set.of());
        given(verificationQueryPort.findApprovedDayCountsByCrewIds(eq(USER_ID), eq(Collections.emptyList())))
                .willReturn(Map.of());
        given(challengeRepositoryPort.findSuccessCountsByUserIdAndCrewIds(eq(USER_ID), eq(Collections.emptyList())))
                .willReturn(Map.of());

        // When
        List<CrewSummaryResult> results = getMyCrewsService.getMyCrews(USER_ID);

        // Then
        assertThat(results).hasSize(1);
        assertThat(results.get(0).todayVerified()).isFalse();
        verify(verificationQueryPort).findVerifiedCrewIds(
                eq(USER_ID), eq(Collections.emptyList()), any(LocalDate.class));
    }

    @Test
    @DisplayName("크루가 없으면 빈 리스트를 반환한다")
    void getMyCrews_noCrews_returnsEmptyList() {
        // Given
        given(crewRepositoryPort.findAllByUserId(USER_ID)).willReturn(Collections.emptyList());
        given(verificationQueryPort.findVerifiedCrewIds(
                eq(USER_ID), eq(Collections.emptyList()), any(LocalDate.class)))
                .willReturn(Set.of());
        given(verificationQueryPort.findApprovedDayCountsByCrewIds(eq(USER_ID), eq(Collections.emptyList())))
                .willReturn(Map.of());
        given(challengeRepositoryPort.findSuccessCountsByUserIdAndCrewIds(eq(USER_ID), eq(Collections.emptyList())))
                .willReturn(Map.of());

        // When
        List<CrewSummaryResult> results = getMyCrewsService.getMyCrews(USER_ID);

        // Then
        assertThat(results).isEmpty();
    }

    // ─────────────────────────────────────────────────────────────
    // successCount 집계 테스트
    // ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("COMPLETED 크루에서 SUCCESS 챌린지 2개면 successCount == 2이다")
    void getMyCrews_completedCrew_successCountReflected() {
        // Given
        Crew completedCrew = completedCrew("crew-c", "완료 크루");

        given(crewRepositoryPort.findAllByUserId(USER_ID))
                .willReturn(List.of(completedCrew));
        given(verificationQueryPort.findVerifiedCrewIds(
                eq(USER_ID), eq(Collections.emptyList()), any(LocalDate.class)))
                .willReturn(Set.of());
        given(challengeRepositoryPort.findSuccessCountsByUserIdAndCrewIds(eq(USER_ID), eq(List.of("crew-c"))))
                .willReturn(Map.of("crew-c", 2));
        given(verificationQueryPort.findApprovedDayCountsByCrewIds(eq(USER_ID), eq(List.of("crew-c"))))
                .willReturn(Map.of("crew-c", 8));

        // When
        List<CrewSummaryResult> results = getMyCrewsService.getMyCrews(USER_ID);

        // Then
        assertThat(results).hasSize(1);
        assertThat(results.get(0).successCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("COMPLETED 크루에서 APPROVED 인증 8일이면 verifiedDayCount == 8이다")
    void getMyCrews_completedCrew_verifiedDayCountReflected() {
        // Given
        Crew completedCrew = completedCrew("crew-c", "완료 크루");

        given(crewRepositoryPort.findAllByUserId(USER_ID))
                .willReturn(List.of(completedCrew));
        given(verificationQueryPort.findVerifiedCrewIds(
                eq(USER_ID), eq(Collections.emptyList()), any(LocalDate.class)))
                .willReturn(Set.of());
        given(challengeRepositoryPort.findSuccessCountsByUserIdAndCrewIds(eq(USER_ID), eq(List.of("crew-c"))))
                .willReturn(Map.of("crew-c", 2));
        given(verificationQueryPort.findApprovedDayCountsByCrewIds(eq(USER_ID), eq(List.of("crew-c"))))
                .willReturn(Map.of("crew-c", 8));

        // When
        List<CrewSummaryResult> results = getMyCrewsService.getMyCrews(USER_ID);

        // Then
        assertThat(results).hasSize(1);
        assertThat(results.get(0).verifiedDayCount()).isEqualTo(8);
    }

    @Test
    @DisplayName("COMPLETED 크루에서 SUCCESS 0개면 successCount == 0, verifiedDayCount == 0이다")
    void getMyCrews_completedCrew_zeroAchievements() {
        // Given
        Crew completedCrew = completedCrew("crew-c", "완료 크루");

        given(crewRepositoryPort.findAllByUserId(USER_ID))
                .willReturn(List.of(completedCrew));
        given(verificationQueryPort.findVerifiedCrewIds(
                eq(USER_ID), eq(Collections.emptyList()), any(LocalDate.class)))
                .willReturn(Set.of());
        given(challengeRepositoryPort.findSuccessCountsByUserIdAndCrewIds(eq(USER_ID), eq(List.of("crew-c"))))
                .willReturn(Map.of());
        given(verificationQueryPort.findApprovedDayCountsByCrewIds(eq(USER_ID), eq(List.of("crew-c"))))
                .willReturn(Map.of());

        // When
        List<CrewSummaryResult> results = getMyCrewsService.getMyCrews(USER_ID);

        // Then
        assertThat(results).hasSize(1);
        assertThat(results.get(0).successCount()).isZero();
        assertThat(results.get(0).verifiedDayCount()).isZero();
    }

    @Test
    @DisplayName("ACTIVE/RECRUITING 크루는 successCount == 0 & verifiedDayCount == 0이다(미집계)")
    void getMyCrews_activeAndRecruitingCrew_achievementsAreZero() {
        // Given
        Crew activeCrew = activeCrew("crew-a", "진행 중 크루");
        Crew recruitingCrew = recruitingCrew("crew-r", "모집 중 크루");

        given(crewRepositoryPort.findAllByUserId(USER_ID))
                .willReturn(List.of(activeCrew, recruitingCrew));
        given(verificationQueryPort.findVerifiedCrewIds(
                eq(USER_ID), eq(List.of("crew-a")), any(LocalDate.class)))
                .willReturn(Set.of());
        given(challengeRepositoryPort.findSuccessCountsByUserIdAndCrewIds(eq(USER_ID), eq(Collections.emptyList())))
                .willReturn(Map.of());
        given(verificationQueryPort.findApprovedDayCountsByCrewIds(eq(USER_ID), eq(Collections.emptyList())))
                .willReturn(Map.of());

        // When
        List<CrewSummaryResult> results = getMyCrewsService.getMyCrews(USER_ID);

        // Then
        assertThat(results).hasSize(2);
        assertThat(results).allSatisfy(r -> {
            assertThat(r.successCount()).isZero();
            assertThat(r.verifiedDayCount()).isZero();
        });
    }

    @Test
    @DisplayName("한 크루에 멤버 여럿이어도 요청자 본인 successCount만 집계된다(crewId 키 정확성)")
    void getMyCrews_multiMemberCrew_onlyRequesterCountReturned() {
        // Given — crew-c 에 user-1(2회)·user-2(5회)·user-3(1회) 가 있어도
        //          findSuccessCountsByUserIdAndCrewIds 는 userId=user-1 기준으로만 쿼리하므로
        //          반환 Map 에 user-1 의 값만 들어온다
        Crew completedCrew = completedCrew("crew-c", "완료 크루");

        given(crewRepositoryPort.findAllByUserId(USER_ID))
                .willReturn(List.of(completedCrew));
        given(verificationQueryPort.findVerifiedCrewIds(
                eq(USER_ID), eq(Collections.emptyList()), any(LocalDate.class)))
                .willReturn(Set.of());
        // 어댑터는 userId=user-1 으로만 조회 → crewId→count, count=2
        given(challengeRepositoryPort.findSuccessCountsByUserIdAndCrewIds(eq(USER_ID), eq(List.of("crew-c"))))
                .willReturn(Map.of("crew-c", 2));
        given(verificationQueryPort.findApprovedDayCountsByCrewIds(eq(USER_ID), eq(List.of("crew-c"))))
                .willReturn(Map.of("crew-c", 6));

        // When
        List<CrewSummaryResult> results = getMyCrewsService.getMyCrews(USER_ID);

        // Then — 다른 멤버 수치가 아닌 user-1 값(2)만 반환
        assertThat(results.get(0).successCount()).isEqualTo(2);
        assertThat(results.get(0).verifiedDayCount()).isEqualTo(6);
    }

    @Test
    @DisplayName("COMPLETED 크루 N개 조회 시 성취 집계 쿼리가 묶음당 2회(N+1 없음)")
    void getMyCrews_multipleCompletedCrews_batchQueryCalledOnce() {
        // Given
        Crew c1 = completedCrew("crew-c1", "완료1");
        Crew c2 = completedCrew("crew-c2", "완료2");
        Crew c3 = completedCrew("crew-c3", "완료3");

        given(crewRepositoryPort.findAllByUserId(USER_ID))
                .willReturn(List.of(c1, c2, c3));
        given(verificationQueryPort.findVerifiedCrewIds(
                eq(USER_ID), eq(Collections.emptyList()), any(LocalDate.class)))
                .willReturn(Set.of());
        given(challengeRepositoryPort.findSuccessCountsByUserIdAndCrewIds(
                eq(USER_ID), eq(List.of("crew-c1", "crew-c2", "crew-c3"))))
                .willReturn(Map.of("crew-c1", 1, "crew-c2", 2, "crew-c3", 3));
        given(verificationQueryPort.findApprovedDayCountsByCrewIds(
                eq(USER_ID), eq(List.of("crew-c1", "crew-c2", "crew-c3"))))
                .willReturn(Map.of("crew-c1", 3, "crew-c2", 6, "crew-c3", 9));

        // When
        getMyCrewsService.getMyCrews(USER_ID);

        // Then — 크루 수에 무관하게 각 배치 포트가 정확히 1회씩만 호출된다
        verify(challengeRepositoryPort, times(1))
                .findSuccessCountsByUserIdAndCrewIds(any(), any());
        verify(verificationQueryPort, times(1))
                .findApprovedDayCountsByCrewIds(any(), any());
    }

    // ─────────────────────────────────────────────────────────────
    // inviteCode 매핑 테스트
    // ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("크루 목록 결과에 초대코드가 매핑된다")
    void getMyCrews_mapsInviteCode() {
        // Given — 기존 activeCrew 헬퍼 재사용 (inviteCode = "ABC123")
        given(crewRepositoryPort.findAllByUserId(USER_ID))
                .willReturn(List.of(activeCrew("crew-1", "운동 크루")));
        given(verificationQueryPort.findVerifiedCrewIds(eq(USER_ID), eq(List.of("crew-1")), any(LocalDate.class)))
                .willReturn(Set.of());
        given(verificationQueryPort.findApprovedDayCountsByCrewIds(eq(USER_ID), eq(Collections.emptyList())))
                .willReturn(Map.of());
        given(challengeRepositoryPort.findSuccessCountsByUserIdAndCrewIds(eq(USER_ID), eq(Collections.emptyList())))
                .willReturn(Map.of());

        // When
        List<CrewSummaryResult> results = getMyCrewsService.getMyCrews(USER_ID);

        // Then
        assertThat(results.get(0).inviteCode()).isEqualTo("ABC123");
    }

    // ─────────────────────────────────────────────────────────────
    // 테스트 헬퍼
    // ─────────────────────────────────────────────────────────────

    private static Crew activeCrew(String crewId, String name) {
        return Crew.of(crewId, "creator-1", name, "목표", "인증 내용",
                VerificationType.TEXT, 10, 3, CrewStatus.ACTIVE,
                LocalDate.now().minusDays(1), LocalDate.now().plusDays(5),
                false, "ABC123", LocalDateTime.now().minusDays(1),
                LocalTime.of(23, 59, 59), CrewCategory.EXERCISE,
                CrewVisibility.PRIVATE, 0L, List.of());
    }

    private static Crew recruitingCrew(String crewId, String name) {
        return Crew.of(crewId, "creator-1", name, "목표", "인증 내용",
                VerificationType.TEXT, 10, 1, CrewStatus.RECRUITING,
                LocalDate.now().plusDays(1), LocalDate.now().plusDays(7),
                false, "DEF456", LocalDateTime.now(),
                LocalTime.of(23, 59, 59), CrewCategory.STUDY,
                CrewVisibility.PRIVATE, 0L, List.of());
    }

    private static Crew completedCrew(String crewId, String name) {
        return Crew.of(crewId, "creator-1", name, "목표", "인증 내용",
                VerificationType.TEXT, 10, 3, CrewStatus.COMPLETED,
                LocalDate.now().minusDays(14), LocalDate.now().minusDays(1),
                false, "GHI789", LocalDateTime.now().minusDays(14),
                LocalTime.of(23, 59, 59), CrewCategory.EXERCISE,
                CrewVisibility.PRIVATE, 0L, List.of());
    }
}
