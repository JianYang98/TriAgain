package com.triagain.crew.application.scheduler;

import com.triagain.common.port.out.DeadLetterRepositoryPort;
import com.triagain.common.scheduler.ChunkProcessor;
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
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import org.springframework.transaction.support.TransactionCallback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
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

    @Mock
    private DeadLetterRepositoryPort deadLetterRepositoryPort;

    private ActivateRecruitingCrewsScheduler scheduler;

    private static final LocalTime DEADLINE_TIME = LocalTime.of(23, 59, 59);

    @BeforeEach
    void setUp() {
        lenient().doAnswer(invocation -> {
            invocation.<Consumer<TransactionStatus>>getArgument(0).accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());

        lenient().doAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        }).when(transactionTemplate).execute(any());

        ChunkProcessor chunkProcessor = new ChunkProcessor(transactionTemplate);
        scheduler = new ActivateRecruitingCrewsScheduler(crewRepositoryPort, chunkProcessor, deadLetterRepositoryPort);
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
    @DisplayName("이미 ACTIVE인 크루가 포함되면 예외 없이 Dead Letter에 기록된다")
    void alreadyActiveCrew_deadLetterSaved() {
        // Given
        Crew activeCrew = Crew.of("crew-1", "creator-1", "테스트 크루", "목표",
                "인증 내용", VerificationType.TEXT, 10, 1, CrewStatus.ACTIVE,
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31), false, "ABC123",
                LocalDateTime.now(), DEADLINE_TIME, null, null, Collections.emptyList());
        // rehydrate해도 DB 상태가 ACTIVE이므로 여전히 실패
        Crew freshActiveCrew = Crew.of("crew-1", "creator-1", "테스트 크루", "목표",
                "인증 내용", VerificationType.TEXT, 10, 1, CrewStatus.ACTIVE,
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31), false, "ABC123",
                LocalDateTime.now(), DEADLINE_TIME, null, null, Collections.emptyList());
        given(crewRepositoryPort.findRecruitingCrewsStartedOnOrBefore(any(LocalDate.class)))
                .willReturn(List.of(activeCrew));
        given(crewRepositoryPort.findById("crew-1")).willReturn(Optional.of(freshActiveCrew));

        // When & Then — ChunkProcessor가 잡으므로 예외 전파 없음
        assertThatCode(() -> scheduler.activateRecruitingCrews())
                .doesNotThrowAnyException();

        // 실패 건은 Dead Letter에 기록 (DB 자체가 ACTIVE이므로 rehydrate해도 여전히 실패)
        verify(deadLetterRepositoryPort, times(1)).save(any());
    }

    @Test
    @DisplayName("1건 실패해도 나머지는 정상 처리되고 Dead Letter가 기록된다")
    void oneFailure_doesNotAffectOthers_andDeadLetterSaved() {
        // Given
        Crew activeCrew = Crew.of("crew-1", "creator-1", "테스트 크루", "목표",
                "인증 내용", VerificationType.TEXT, 10, 1, CrewStatus.ACTIVE,
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31), false, "ABC123",
                LocalDateTime.now(), DEADLINE_TIME, null, null, Collections.emptyList());
        Crew freshActiveCrew = Crew.of("crew-1", "creator-1", "테스트 크루", "목표",
                "인증 내용", VerificationType.TEXT, 10, 1, CrewStatus.ACTIVE,
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31), false, "ABC123",
                LocalDateTime.now(), DEADLINE_TIME, null, null, Collections.emptyList());
        Crew recruitingCrew2 = recruitingCrew("crew-2", LocalDate.of(2026, 3, 2));
        Crew freshRecruitingCrew2 = recruitingCrew("crew-2", LocalDate.of(2026, 3, 2));

        given(crewRepositoryPort.findRecruitingCrewsStartedOnOrBefore(any(LocalDate.class)))
                .willReturn(List.of(activeCrew, recruitingCrew2));
        given(crewRepositoryPort.findById("crew-1")).willReturn(Optional.of(freshActiveCrew));
        given(crewRepositoryPort.findById("crew-2")).willReturn(Optional.of(freshRecruitingCrew2));
        given(crewRepositoryPort.save(any())).willAnswer(inv -> inv.getArgument(0));

        // When
        scheduler.activateRecruitingCrews();

        // Then — 첫 번째는 DB 자체가 ACTIVE이므로 rehydrate해도 실패(Dead Letter), 두 번째는 정상 처리
        assertThat(freshRecruitingCrew2.getStatus()).isEqualTo(CrewStatus.ACTIVE);
        verify(crewRepositoryPort, times(1)).save(any());
        verify(deadLetterRepositoryPort, times(1)).save(any());
    }

    // --- 헬퍼 메서드 ---

    private static Crew recruitingCrew(String id, LocalDate startDate) {
        return Crew.of(id, "creator-1", "테스트 크루", "목표",
                "인증 내용", VerificationType.TEXT, 10, 1, CrewStatus.RECRUITING,
                startDate, startDate.plusDays(30), false, "ABC123",
                LocalDateTime.now(), DEADLINE_TIME, null, null, Collections.emptyList());
    }
}
