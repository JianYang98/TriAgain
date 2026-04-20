package com.triagain.crew.application;

import com.triagain.common.exception.BusinessException;
import com.triagain.common.exception.ErrorCode;
import com.triagain.crew.domain.model.Crew;
import com.triagain.crew.domain.model.CrewMember;
import com.triagain.crew.domain.vo.CrewRole;
import com.triagain.crew.domain.vo.CrewStatus;
import com.triagain.crew.domain.vo.VerificationType;
import com.triagain.crew.port.out.ChallengeRepositoryPort;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class LeaveCrewServiceTest {

    @Mock
    private CrewRepositoryPort crewRepositoryPort;

    @Mock
    private ChallengeRepositoryPort challengeRepositoryPort;

    @Mock
    private CrewLockProperties lockProperties;

    @Mock
    private org.springframework.transaction.support.TransactionTemplate txTemplate;

    @InjectMocks
    private LeaveCrewService leaveCrewService;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        org.mockito.BDDMockito.given(lockProperties.isPessimistic()).willReturn(true);
        org.mockito.Mockito.doAnswer(invocation -> {
            java.util.function.Consumer<org.springframework.transaction.TransactionStatus> action = invocation.getArgument(0);
            action.accept(null);
            return null;
        }).when(txTemplate).executeWithoutResult(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("MEMBER가 RECRUITING 크루에서 탈퇴하면 성공한다")
    void leaveCrew_success() {
        // Given
        Crew crew = recruitingCrewWithLeaderAndMember("leader-1", "member-1");
        given(crewRepositoryPort.findByIdWithLock("CREW-1")).willReturn(Optional.of(crew));
        given(crewRepositoryPort.save(crew)).willReturn(crew);

        // When & Then
        assertThatCode(() -> leaveCrewService.leaveCrew("CREW-1", "member-1"))
                .doesNotThrowAnyException();
        verify(crewRepositoryPort).save(crew);
        verify(crewRepositoryPort).deleteMemberByCrewIdAndUserId("CREW-1", "member-1");
        verify(crewRepositoryPort, never()).deleteById(any());
    }

    @Test
    @DisplayName("탈퇴 후 크루가 빈 상태(currentMembers=0)면 빈 크루를 자동 삭제한다")
    void leaveCrew_emptyCrewAfterRemoval_deletesCrewById() {
        // Given — 비현실적 시뮬레이션: LEADER 자동 위임 등으로 도달 가능한 잔여 멤버 1명 상태
        CrewMember soloMember = CrewMember.of("CRMB-1", "member-1", "CREW-1", CrewRole.MEMBER, LocalDateTime.now());
        Crew crew = Crew.of("CREW-1", "former-leader", "테스트 크루", "목표", "인증 내용",
                VerificationType.TEXT, 10, 1, CrewStatus.RECRUITING,
                LocalDate.now().plusDays(1), LocalDate.now().plusDays(14), true,
                "ABC123", LocalDateTime.now(), LocalTime.of(23, 59, 59), null, null, 0L, List.of(soloMember));
        given(crewRepositoryPort.findByIdWithLock("CREW-1")).willReturn(Optional.of(crew));
        given(crewRepositoryPort.save(crew)).willReturn(crew);

        // When
        leaveCrewService.leaveCrew("CREW-1", "member-1");

        // Then
        verify(crewRepositoryPort).deleteMemberByCrewIdAndUserId("CREW-1", "member-1");
        verify(crewRepositoryPort).deleteById("CREW-1");
    }

    @Test
    @DisplayName("존재하지 않는 크루에서 탈퇴하면 CREW_NOT_FOUND 예외가 발생한다")
    void crewNotFound_throws() {
        // Given
        given(crewRepositoryPort.findByIdWithLock("CREW-999")).willReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> leaveCrewService.leaveCrew("CREW-999", "member-1"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CREW_NOT_FOUND);
    }

    @Test
    @DisplayName("비멤버가 탈퇴하면 CREW_MEMBER_NOT_FOUND 예외가 발생한다")
    void nonMember_throws() {
        // Given
        Crew crew = recruitingCrewWithLeader("leader-1");
        given(crewRepositoryPort.findByIdWithLock("CREW-1")).willReturn(Optional.of(crew));

        // When & Then
        assertThatThrownBy(() -> leaveCrewService.leaveCrew("CREW-1", "stranger"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CREW_MEMBER_NOT_FOUND);
    }

    @Test
    @DisplayName("LEADER가 탈퇴하면 LEADER_CANNOT_LEAVE 예외가 발생한다")
    void leaderCannotLeave_throws() {
        // Given
        Crew crew = recruitingCrewWithLeaderAndMember("leader-1", "member-1");
        given(crewRepositoryPort.findByIdWithLock("CREW-1")).willReturn(Optional.of(crew));

        // When & Then
        assertThatThrownBy(() -> leaveCrewService.leaveCrew("CREW-1", "leader-1"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.LEADER_CANNOT_LEAVE);
    }

    @Test
    @DisplayName("ACTIVE 크루 + 챌린지 미시작 멤버는 탈퇴할 수 있다")
    void activeCrewWithoutChallenge_success() {
        // Given
        Crew crew = activeCrewWithLeaderAndMember("leader-1", "member-1");
        given(crewRepositoryPort.findByIdWithLock("CREW-1")).willReturn(Optional.of(crew));
        given(challengeRepositoryPort.existsByUserIdAndCrewId("member-1", "CREW-1")).willReturn(false);
        given(crewRepositoryPort.save(crew)).willReturn(crew);

        // When & Then
        assertThatCode(() -> leaveCrewService.leaveCrew("CREW-1", "member-1"))
                .doesNotThrowAnyException();
        verify(crewRepositoryPort).save(crew);
        verify(crewRepositoryPort).deleteMemberByCrewIdAndUserId("CREW-1", "member-1");
    }

    @Test
    @DisplayName("ACTIVE 크루 + 챌린지를 시작한 멤버가 탈퇴를 시도하면 CANNOT_LEAVE_ACTIVE_CREW 예외가 발생한다")
    void activeCrewWithChallenge_throws() {
        // Given
        Crew crew = activeCrewWithLeaderAndMember("leader-1", "member-1");
        given(crewRepositoryPort.findByIdWithLock("CREW-1")).willReturn(Optional.of(crew));
        given(challengeRepositoryPort.existsByUserIdAndCrewId("member-1", "CREW-1")).willReturn(true);

        // When & Then
        assertThatThrownBy(() -> leaveCrewService.leaveCrew("CREW-1", "member-1"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CANNOT_LEAVE_ACTIVE_CREW);
    }

    @Test
    @DisplayName("COMPLETED 크루에서 탈퇴하면 CANNOT_LEAVE_ACTIVE_CREW 예외가 발생한다")
    void completedCrew_throws() {
        // Given
        CrewMember leader = CrewMember.of("CRMB-1", "leader-1", "CREW-1", CrewRole.LEADER, LocalDateTime.now());
        CrewMember member = CrewMember.of("CRMB-2", "member-1", "CREW-1", CrewRole.MEMBER, LocalDateTime.now());
        Crew crew = Crew.of("CREW-1", "leader-1", "크루", "목표", "인증",
                VerificationType.TEXT, 10, 2, CrewStatus.COMPLETED,
                LocalDate.now().minusDays(20), LocalDate.now().minusDays(1), true,
                "ABC123", LocalDateTime.now(), LocalTime.of(23, 59, 59), null, null, 0L, List.of(leader, member));
        given(crewRepositoryPort.findByIdWithLock("CREW-1")).willReturn(Optional.of(crew));
        given(challengeRepositoryPort.existsByUserIdAndCrewId("member-1", "CREW-1")).willReturn(false);

        // When & Then
        assertThatThrownBy(() -> leaveCrewService.leaveCrew("CREW-1", "member-1"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CANNOT_LEAVE_ACTIVE_CREW);
    }

    // --- 헬퍼 ---

    private Crew recruitingCrewWithLeader(String leaderId) {
        CrewMember leader = CrewMember.of("CRMB-1", leaderId, "CREW-1", CrewRole.LEADER, LocalDateTime.now());
        return Crew.of("CREW-1", leaderId, "테스트 크루", "목표", "인증 내용",
                VerificationType.TEXT, 10, 1, CrewStatus.RECRUITING,
                LocalDate.now().plusDays(1), LocalDate.now().plusDays(14), true,
                "ABC123", LocalDateTime.now(), LocalTime.of(23, 59, 59), null, null, 0L, List.of(leader));
    }

    private Crew recruitingCrewWithLeaderAndMember(String leaderId, String memberId) {
        CrewMember leader = CrewMember.of("CRMB-1", leaderId, "CREW-1", CrewRole.LEADER, LocalDateTime.now());
        CrewMember member = CrewMember.of("CRMB-2", memberId, "CREW-1", CrewRole.MEMBER, LocalDateTime.now());
        return Crew.of("CREW-1", leaderId, "테스트 크루", "목표", "인증 내용",
                VerificationType.TEXT, 10, 2, CrewStatus.RECRUITING,
                LocalDate.now().plusDays(1), LocalDate.now().plusDays(14), true,
                "ABC123", LocalDateTime.now(), LocalTime.of(23, 59, 59), null, null, 0L, List.of(leader, member));
    }

    private Crew activeCrewWithLeaderAndMember(String leaderId, String memberId) {
        CrewMember leader = CrewMember.of("CRMB-1", leaderId, "CREW-1", CrewRole.LEADER, LocalDateTime.now());
        CrewMember member = CrewMember.of("CRMB-2", memberId, "CREW-1", CrewRole.MEMBER, LocalDateTime.now());
        return Crew.of("CREW-1", leaderId, "테스트 크루", "목표", "인증 내용",
                VerificationType.TEXT, 10, 2, CrewStatus.ACTIVE,
                LocalDate.now().minusDays(1), LocalDate.now().plusDays(13), true,
                "ABC123", LocalDateTime.now(), LocalTime.of(23, 59, 59), null, null, 0L, List.of(leader, member));
    }
}
