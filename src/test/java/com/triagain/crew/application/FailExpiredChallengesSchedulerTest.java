package com.triagain.crew.application;

import com.triagain.crew.domain.model.Challenge;
import com.triagain.crew.domain.model.Crew;
import com.triagain.crew.domain.vo.ChallengeStatus;
import com.triagain.crew.domain.vo.CrewStatus;
import com.triagain.crew.domain.vo.VerificationType;
import com.triagain.crew.port.out.ChallengeRepositoryPort;
import com.triagain.crew.port.out.CrewRepositoryPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FailExpiredChallengesSchedulerTest {

    @Mock
    private ChallengeRepositoryPort challengeRepositoryPort;

    @Mock
    private CrewRepositoryPort crewRepositoryPort;

    @InjectMocks
    private FailExpiredChallengesScheduler scheduler;

    private static final LocalTime DEADLINE_TIME = LocalTime.of(23, 59, 59);

    @Test
    @DisplayName("만료 챌린지 없으면 save 호출 없음")
    void noExpiredChallenges_noSave() {
        // Given
        given(challengeRepositoryPort.findExpiredWithoutVerification())
                .willReturn(Collections.emptyList());

        // When
        scheduler.failExpiredChallenges();

        // Then
        verify(challengeRepositoryPort, never()).save(any());
        verify(crewRepositoryPort, never()).findAllByIds(any());
    }

    @Test
    @DisplayName("만료 챌린지 → FAILED 처리 + 다음 사이클 생성")
    void expiredChallenge_failedAndNextCycleCreated() {
        // Given
        LocalDate startDate = LocalDate.of(2026, 3, 1);
        Challenge expired = Challenge.of("CHAL-1", "user-1", "crew-1", 1, 3, 0,
                ChallengeStatus.IN_PROGRESS, startDate,
                LocalDateTime.of(2026, 3, 4, 23, 59, 59), LocalDateTime.now());

        Crew crew = activeCrew("crew-1", LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31));

        given(challengeRepositoryPort.findExpiredWithoutVerification())
                .willReturn(List.of(expired));
        given(crewRepositoryPort.findAllByIds(List.of("crew-1")))
                .willReturn(List.of(crew));
        given(challengeRepositoryPort.save(any())).willAnswer(inv -> inv.getArgument(0));

        // When
        scheduler.failExpiredChallenges();

        // Then — FAILED 저장 + 새 챌린지 저장 = 총 2회
        assertThat(expired.getStatus()).isEqualTo(ChallengeStatus.FAILED);

        ArgumentCaptor<Challenge> captor = ArgumentCaptor.forClass(Challenge.class);
        verify(challengeRepositoryPort, times(2)).save(captor.capture());

        Challenge saved = captor.getAllValues().get(0);
        assertThat(saved.getStatus()).isEqualTo(ChallengeStatus.FAILED);

        Challenge next = captor.getAllValues().get(1);
        assertThat(next.getStatus()).isEqualTo(ChallengeStatus.IN_PROGRESS);
        assertThat(next.getCycleNumber()).isEqualTo(2);
        assertThat(next.getStartDate()).isEqualTo(startDate.plusDays(1));
    }

    @Test
    @DisplayName("크루 endDate 지나면 새 챌린지 미생성 (fail만)")
    void crewEndDatePassed_failOnlyNoNewChallenge() {
        // Given — 크루 endDate가 이미 지남 (missedDate 다음 날이 endDate 이후)
        LocalDate startDate = LocalDate.of(2026, 3, 1);
        Challenge expired = Challenge.of("CHAL-1", "user-1", "crew-1", 1, 3, 0,
                ChallengeStatus.IN_PROGRESS, startDate,
                LocalDateTime.of(2026, 3, 4, 23, 59, 59), LocalDateTime.now());

        // endDate가 missedDate(3/1)와 같음 → newStartDate(3/2)는 endDate 이후
        Crew crew = activeCrew("crew-1", LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 1));

        given(challengeRepositoryPort.findExpiredWithoutVerification())
                .willReturn(List.of(expired));
        given(crewRepositoryPort.findAllByIds(List.of("crew-1")))
                .willReturn(List.of(crew));
        given(challengeRepositoryPort.save(any())).willAnswer(inv -> inv.getArgument(0));

        // When
        scheduler.failExpiredChallenges();

        // Then — FAILED 저장 1회만 (새 챌린지 미생성)
        assertThat(expired.getStatus()).isEqualTo(ChallengeStatus.FAILED);
        verify(challengeRepositoryPort, times(1)).save(any());
    }

    @Test
    @DisplayName("completedDays > 0인 중간 실패 시 다음 startDate가 올바르다")
    void midCycleFail_correctNextStartDate() {
        // Given — completedDays = 2 (3일 중 2일 완료, 3일째 미인증)
        LocalDate startDate = LocalDate.of(2026, 3, 1);
        Challenge expired = Challenge.of("CHAL-1", "user-1", "crew-1", 1, 3, 2,
                ChallengeStatus.IN_PROGRESS, startDate,
                LocalDateTime.of(2026, 3, 4, 23, 59, 59), LocalDateTime.now());

        Crew crew = activeCrew("crew-1", LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31));

        given(challengeRepositoryPort.findExpiredWithoutVerification())
                .willReturn(List.of(expired));
        given(crewRepositoryPort.findAllByIds(List.of("crew-1")))
                .willReturn(List.of(crew));
        given(challengeRepositoryPort.save(any())).willAnswer(inv -> inv.getArgument(0));

        // When
        scheduler.failExpiredChallenges();

        // Then — missedDate = startDate + 2 = 3/3, newStartDate = 3/4
        ArgumentCaptor<Challenge> captor = ArgumentCaptor.forClass(Challenge.class);
        verify(challengeRepositoryPort, times(2)).save(captor.capture());

        Challenge next = captor.getAllValues().get(1);
        assertThat(next.getStartDate()).isEqualTo(LocalDate.of(2026, 3, 4));
        assertThat(next.getCycleNumber()).isEqualTo(2);
    }

    @Test
    @DisplayName("여러 크루의 챌린지 동시 처리")
    void multipleCrews_processedTogether() {
        // Given
        Challenge expired1 = Challenge.of("CHAL-1", "user-1", "crew-1", 1, 3, 0,
                ChallengeStatus.IN_PROGRESS, LocalDate.of(2026, 3, 1),
                LocalDateTime.of(2026, 3, 4, 23, 59, 59), LocalDateTime.now());
        Challenge expired2 = Challenge.of("CHAL-2", "user-2", "crew-2", 2, 3, 1,
                ChallengeStatus.IN_PROGRESS, LocalDate.of(2026, 3, 1),
                LocalDateTime.of(2026, 3, 4, 23, 59, 59), LocalDateTime.now());

        Crew crew1 = activeCrew("crew-1", LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31));
        Crew crew2 = activeCrew("crew-2", LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31));

        given(challengeRepositoryPort.findExpiredWithoutVerification())
                .willReturn(List.of(expired1, expired2));
        given(crewRepositoryPort.findAllByIds(argThat(ids ->
                ids.containsAll(List.of("crew-1", "crew-2")))))
                .willReturn(List.of(crew1, crew2));
        given(challengeRepositoryPort.save(any())).willAnswer(inv -> inv.getArgument(0));

        // When
        scheduler.failExpiredChallenges();

        // Then — 2건 FAILED + 2건 새 챌린지 = 총 4회 save
        assertThat(expired1.getStatus()).isEqualTo(ChallengeStatus.FAILED);
        assertThat(expired2.getStatus()).isEqualTo(ChallengeStatus.FAILED);
        verify(challengeRepositoryPort, times(4)).save(any());
    }

    // --- 헬퍼 메서드 ---

    private static Crew activeCrew(String id, LocalDate startDate, LocalDate endDate) {
        return Crew.of(id, "creator-1", "테스트 크루", "목표",
                VerificationType.TEXT, 10, 1, CrewStatus.ACTIVE,
                startDate, endDate, false, "ABC123",
                LocalDateTime.now(), DEADLINE_TIME, Collections.emptyList());
    }
}
