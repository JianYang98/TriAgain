package com.triagain.crew.application;

import com.triagain.common.exception.BusinessException;
import com.triagain.common.exception.ErrorCode;
import com.triagain.crew.domain.model.Crew;
import com.triagain.crew.domain.model.CrewMember;
import com.triagain.crew.domain.vo.CrewRole;
import com.triagain.crew.domain.vo.CrewStatus;
import com.triagain.crew.domain.vo.VerificationType;
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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DeleteCrewServiceTest {

    @Mock
    private CrewRepositoryPort crewRepositoryPort;

    @InjectMocks
    private DeleteCrewService deleteCrewService;

    @Test
    @DisplayName("LEADER가 RECRUITING + 혼자인 크루를 삭제하면 성공한다")
    void deleteCrew_success() {
        // Given
        Crew crew = recruitingCrewWithLeader("leader-1");
        given(crewRepositoryPort.findByIdWithLock("CREW-1")).willReturn(Optional.of(crew));

        // When & Then
        assertThatCode(() -> deleteCrewService.deleteCrew("CREW-1", "leader-1"))
                .doesNotThrowAnyException();
        verify(crewRepositoryPort).deleteById("CREW-1");
    }

    @Test
    @DisplayName("존재하지 않는 크루를 삭제하면 CREW_NOT_FOUND 예외가 발생한다")
    void crewNotFound_throws() {
        // Given
        given(crewRepositoryPort.findByIdWithLock("CREW-999")).willReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> deleteCrewService.deleteCrew("CREW-999", "leader-1"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CREW_NOT_FOUND);
    }

    @Test
    @DisplayName("MEMBER가 삭제하면 CREW_ACCESS_DENIED 예외가 발생한다")
    void memberCannotDelete_throws() {
        // Given
        Crew crew = recruitingCrewWithLeaderAndMember("leader-1", "member-1");
        given(crewRepositoryPort.findByIdWithLock("CREW-1")).willReturn(Optional.of(crew));

        // When & Then
        assertThatThrownBy(() -> deleteCrewService.deleteCrew("CREW-1", "member-1"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CREW_ACCESS_DENIED);
    }

    @Test
    @DisplayName("멤버가 2명 이상이면 CREW_HAS_MEMBERS 예외가 발생한다")
    void hasMembers_throws() {
        // Given
        Crew crew = recruitingCrewWithLeaderAndMember("leader-1", "member-1");
        given(crewRepositoryPort.findByIdWithLock("CREW-1")).willReturn(Optional.of(crew));

        // When & Then
        assertThatThrownBy(() -> deleteCrewService.deleteCrew("CREW-1", "leader-1"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CREW_HAS_MEMBERS);
    }

    @Test
    @DisplayName("ACTIVE 상태 크루를 삭제하면 CREW_NOT_RECRUITING 예외가 발생한다")
    void activeCannotDelete_throws() {
        // Given
        CrewMember leader = CrewMember.of("CRMB-1", "leader-1", "CREW-1", CrewRole.LEADER, LocalDateTime.now());
        Crew crew = Crew.of("CREW-1", "leader-1", "크루", "목표", "인증",
                VerificationType.TEXT, 10, 1, CrewStatus.ACTIVE,
                LocalDate.now().plusDays(1), LocalDate.now().plusDays(14), true,
                "ABC123", LocalDateTime.now(), LocalTime.of(23, 59, 59), List.of(leader));
        given(crewRepositoryPort.findByIdWithLock("CREW-1")).willReturn(Optional.of(crew));

        // When & Then
        assertThatThrownBy(() -> deleteCrewService.deleteCrew("CREW-1", "leader-1"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CREW_NOT_RECRUITING);
    }

    // --- 헬퍼 ---

    private Crew recruitingCrewWithLeader(String leaderId) {
        CrewMember leader = CrewMember.of("CRMB-1", leaderId, "CREW-1", CrewRole.LEADER, LocalDateTime.now());
        return Crew.of("CREW-1", leaderId, "테스트 크루", "목표", "인증 내용",
                VerificationType.TEXT, 10, 1, CrewStatus.RECRUITING,
                LocalDate.now().plusDays(1), LocalDate.now().plusDays(14), true,
                "ABC123", LocalDateTime.now(), LocalTime.of(23, 59, 59), List.of(leader));
    }

    private Crew recruitingCrewWithLeaderAndMember(String leaderId, String memberId) {
        CrewMember leader = CrewMember.of("CRMB-1", leaderId, "CREW-1", CrewRole.LEADER, LocalDateTime.now());
        CrewMember member = CrewMember.of("CRMB-2", memberId, "CREW-1", CrewRole.MEMBER, LocalDateTime.now());
        return Crew.of("CREW-1", leaderId, "테스트 크루", "목표", "인증 내용",
                VerificationType.TEXT, 10, 2, CrewStatus.RECRUITING,
                LocalDate.now().plusDays(1), LocalDate.now().plusDays(14), true,
                "ABC123", LocalDateTime.now(), LocalTime.of(23, 59, 59), List.of(leader, member));
    }
}
