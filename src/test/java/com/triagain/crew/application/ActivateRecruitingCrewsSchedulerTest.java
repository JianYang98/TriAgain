package com.triagain.crew.application;

import com.triagain.crew.domain.model.Crew;
import com.triagain.crew.domain.vo.CrewStatus;
import com.triagain.crew.domain.vo.VerificationType;
import com.triagain.crew.port.out.CrewRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionTemplate;

import org.springframework.transaction.TransactionStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ActivateRecruitingCrewsSchedulerTest {

    @Mock
    private CrewRepositoryPort crewRepositoryPort;

    @Mock
    private TransactionTemplate transactionTemplate;

    private ActivateRecruitingCrewsScheduler scheduler;

    private static final LocalTime DEADLINE_TIME = LocalTime.of(23, 59, 59);

    @BeforeEach
    void setUp() {
        // TransactionTemplate.executeWithoutResult()가 콜백을 즉시 실행하도록 stub
        // empty list early return 테스트에서 사용되지 않으므로 lenient
        lenient().doAnswer(invocation -> {
            invocation.<Consumer<TransactionStatus>>getArgument(0).accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());

        scheduler = new ActivateRecruitingCrewsScheduler(crewRepositoryPort, transactionTemplate);
    }

    @Test
    @DisplayName("startDate 도래한 RECRUITING 크루가 ACTIVE로 전환된다")
    void recruitingCrews_activatedToActive() {
        // Given
        Crew crew1 = recruitingCrew("crew-1", LocalDate.of(2026, 3, 1));
        Crew crew2 = recruitingCrew("crew-2", LocalDate.of(2026, 3, 2));
        given(crewRepositoryPort.findRecruitingCrewsStartedOnOrBefore(any(LocalDate.class)))
                .willReturn(List.of(crew1, crew2));
        given(crewRepositoryPort.save(any())).willAnswer(inv -> inv.getArgument(0));

        // When
        scheduler.activateRecruitingCrews();

        // Then
        assertThat(crew1.getStatus()).isEqualTo(CrewStatus.ACTIVE);
        assertThat(crew2.getStatus()).isEqualTo(CrewStatus.ACTIVE);
        verify(crewRepositoryPort, times(2)).save(any());
    }

    @Test
    @DisplayName("대상 크루가 없으면 save를 호출하지 않는다")
    void noRecruitingCrews_noSave() {
        // Given
        given(crewRepositoryPort.findRecruitingCrewsStartedOnOrBefore(any(LocalDate.class)))
                .willReturn(Collections.emptyList());

        // When
        scheduler.activateRecruitingCrews();

        // Then
        verify(crewRepositoryPort, never()).save(any());
    }

    @Test
    @DisplayName("이미 ACTIVE인 크루가 포함되면 예외 없이 실패 로그만 남긴다")
    void alreadyActiveCrew_logsErrorWithoutThrowing() {
        // Given
        Crew activeCrew = Crew.of("crew-1", "creator-1", "테스트 크루", "목표",
                "인증 내용", VerificationType.TEXT, 10, 1, CrewStatus.ACTIVE,
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31), false, "ABC123",
                LocalDateTime.now(), DEADLINE_TIME, null, null, Collections.emptyList());
        given(crewRepositoryPort.findRecruitingCrewsStartedOnOrBefore(any(LocalDate.class)))
                .willReturn(List.of(activeCrew));

        // When & Then — try-catch가 잡으므로 예외 전파 없음
        assertThatCode(() -> scheduler.activateRecruitingCrews())
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("1건 실패해도 나머지는 정상 처리된다")
    void oneFailure_doesNotAffectOthers() {
        // Given
        Crew activeCrew = Crew.of("crew-1", "creator-1", "테스트 크루", "목표",
                "인증 내용", VerificationType.TEXT, 10, 1, CrewStatus.ACTIVE,
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31), false, "ABC123",
                LocalDateTime.now(), DEADLINE_TIME, null, null, Collections.emptyList());
        Crew recruitingCrew = recruitingCrew("crew-2", LocalDate.of(2026, 3, 2));

        given(crewRepositoryPort.findRecruitingCrewsStartedOnOrBefore(any(LocalDate.class)))
                .willReturn(List.of(activeCrew, recruitingCrew));
        given(crewRepositoryPort.save(any())).willAnswer(inv -> inv.getArgument(0));

        // When
        scheduler.activateRecruitingCrews();

        // Then — 첫 번째는 실패, 두 번째는 정상 처리
        assertThat(recruitingCrew.getStatus()).isEqualTo(CrewStatus.ACTIVE);
        verify(crewRepositoryPort, times(1)).save(any());
    }

    // --- 헬퍼 메서드 ---

    private static Crew recruitingCrew(String id, LocalDate startDate) {
        return Crew.of(id, "creator-1", "테스트 크루", "목표",
                "인증 내용", VerificationType.TEXT, 10, 1, CrewStatus.RECRUITING,
                startDate, startDate.plusDays(30), false, "ABC123",
                LocalDateTime.now(), DEADLINE_TIME, null, null, Collections.emptyList());
    }
}
