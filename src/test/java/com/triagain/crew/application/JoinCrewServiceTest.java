package com.triagain.crew.application;

import com.triagain.common.exception.BusinessException;
import com.triagain.common.exception.ErrorCode;
import com.triagain.crew.domain.model.Crew;
import com.triagain.crew.domain.vo.CrewStatus;
import com.triagain.crew.domain.vo.VerificationType;
import com.triagain.crew.port.in.JoinCrewUseCase.JoinCrewCommand;
import com.triagain.crew.port.out.CrewRepositoryPort;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class JoinCrewServiceTest {

    @Mock
    private CrewRepositoryPort crewRepositoryPort;

    @InjectMocks
    private JoinCrewService joinCrewService;

    private static Crew recruitingCrew(LocalDate startDate, LocalDate endDate) {
        return Crew.of(
                "CREW-001", "creator-1", "테스트 크루", "목표",
                VerificationType.TEXT, 10, 1,
                CrewStatus.RECRUITING, startDate, endDate,
                true, "ABC123", LocalDateTime.now(),
                LocalTime.of(23, 59, 59), Collections.emptyList()
        );
    }

    @Test
    @DisplayName("참여 마감일 초과 시 CREW_JOIN_DEADLINE_PASSED 예외 발생")
    void joinCrew_deadlinePassed_throwsException() {
        // Given — 크루 종료일이 내일이면 endDate - 3일 = 이틀 전 → 마감 초과
        LocalDate endDate = LocalDate.now().plusDays(1);
        LocalDate startDate = endDate.minusDays(7);
        Crew crew = recruitingCrew(startDate, endDate);

        given(crewRepositoryPort.findByIdWithLock("CREW-001")).willReturn(Optional.of(crew));

        JoinCrewCommand command = new JoinCrewCommand("user-1", "CREW-001");

        // When & Then
        assertThatThrownBy(() -> joinCrewService.joinCrew(command))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CREW_JOIN_DEADLINE_PASSED);
    }

    @Test
    @DisplayName("참여 마감일 경계값 — endDate - 3일 당일이면 가입 가능")
    void joinCrew_deadlineBoundary_succeeds() {
        // Given — endDate - 3일 = 오늘 → isAfter가 false이므로 통과
        LocalDate endDate = LocalDate.now().plusDays(3);
        LocalDate startDate = endDate.minusDays(7);
        Crew crew = recruitingCrew(startDate, endDate);

        given(crewRepositoryPort.findByIdWithLock("CREW-001")).willReturn(Optional.of(crew));

        JoinCrewCommand command = new JoinCrewCommand("user-1", "CREW-001");

        // When & Then — 마감일 검증 통과 후 addMember에서 중복 검증 등 진행됨 (예외 없이 통과)
        // addMember 성공을 위해 save mock 필요
        given(crewRepositoryPort.save(crew)).willReturn(crew);
        given(crewRepositoryPort.saveMember(org.mockito.ArgumentMatchers.any())).willReturn(null);

        // 마감일 검증 통과 확인 — CREW_JOIN_DEADLINE_PASSED 예외가 발생하지 않아야 함
        org.assertj.core.api.Assertions.assertThatCode(() -> joinCrewService.joinCrew(command))
                .doesNotThrowAnyException();
    }
}
