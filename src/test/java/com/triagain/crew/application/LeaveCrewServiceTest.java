package com.triagain.crew.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import com.triagain.common.exception.BusinessException;
import com.triagain.common.exception.ErrorCode;
import com.triagain.crew.domain.model.Crew;
import com.triagain.crew.domain.model.CrewMember;
import com.triagain.crew.domain.vo.CrewRole;
import com.triagain.crew.domain.vo.CrewStatus;
import com.triagain.crew.domain.vo.VerificationType;
import com.triagain.crew.port.out.ChallengeRepositoryPort;
import com.triagain.crew.port.out.CrewRepositoryPort;

@ExtendWith(MockitoExtension.class)
class LeaveCrewServiceTest {

	@Mock
	private CrewRepositoryPort crewRepositoryPort;

	@Mock
	private ChallengeRepositoryPort challengeRepositoryPort;

	@Mock
	private CrewLockProperties lockProperties;

	@Mock
	private TransactionTemplate txTemplate;

	@InjectMocks
	private LeaveCrewService leaveCrewService;

	@Nested
	@DisplayName("비관적 락 경로 (PESSIMISTIC)")
	class PessimisticLock {

		@SuppressWarnings("unchecked")
		@BeforeEach
		void setUp() {
			given(lockProperties.isPessimistic()).willReturn(true);
			doAnswer(inv -> {
				Consumer<TransactionStatus> action =
					inv.getArgument(0);
				action.accept(null);
				return null;
			}).when(txTemplate).executeWithoutResult(any());
		}

		@Test
		@DisplayName("MEMBER가 RECRUITING 크루에서 탈퇴하면 성공")
		void leaveCrew_success() {
			// Given
			Crew crew = recruitingCrewWithLeaderAndMember(
				"leader-1", "member-1");
			given(crewRepositoryPort.findByIdWithLock("CREW-1"))
				.willReturn(Optional.of(crew));
			given(crewRepositoryPort.save(crew)).willReturn(crew);

			// When & Then
			assertThatCode(() ->
				leaveCrewService.leaveCrew("CREW-1", "member-1"))
				.doesNotThrowAnyException();
			verify(crewRepositoryPort).save(crew);
			verify(crewRepositoryPort)
				.deleteMemberByCrewIdAndUserId("CREW-1", "member-1");
			verify(crewRepositoryPort, never()).deleteById(any());
		}

		@Test
		@DisplayName("탈퇴 후 크루가 빈 상태면 자동 삭제한다")
		void leaveCrew_emptyCrewAfterRemoval_deletes() {
			// Given — 리더 위임 등으로 MEMBER만 1명 남은 상태
			CrewMember solo = CrewMember.of("CRMB-1", "member-1",
				"CREW-1", CrewRole.MEMBER, LocalDateTime.now());
			Crew crew = Crew.of("CREW-1", "former-leader",
				"테스트 크루", "목표", "인증 내용",
				VerificationType.TEXT, 10, 1,
				CrewStatus.RECRUITING,
				LocalDate.now().plusDays(1),
				LocalDate.now().plusDays(14), true, "ABC123",
				LocalDateTime.now(), LocalTime.of(23, 59, 59),
				null, null, 0L, List.of(solo));
			given(crewRepositoryPort.findByIdWithLock("CREW-1"))
				.willReturn(Optional.of(crew));
			given(crewRepositoryPort.save(crew)).willReturn(crew);

			// When
			leaveCrewService.leaveCrew("CREW-1", "member-1");

			// Then
			verify(crewRepositoryPort)
				.deleteMemberByCrewIdAndUserId("CREW-1", "member-1");
			verify(crewRepositoryPort).deleteById("CREW-1");
		}

		@Test
		@DisplayName("존재하지 않는 크루 → CREW_NOT_FOUND")
		void crewNotFound_throws() {
			// Given
			given(crewRepositoryPort.findByIdWithLock("CREW-999"))
				.willReturn(Optional.empty());

			// When & Then
			assertThatThrownBy(() ->
				leaveCrewService.leaveCrew("CREW-999", "member-1"))
				.isInstanceOf(BusinessException.class)
				.extracting(e ->
					((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.CREW_NOT_FOUND);
		}

		@Test
		@DisplayName("비멤버 탈퇴 → CREW_MEMBER_NOT_FOUND")
		void nonMember_throws() {
			// Given
			Crew crew = recruitingCrewWithLeader("leader-1");
			given(crewRepositoryPort.findByIdWithLock("CREW-1"))
				.willReturn(Optional.of(crew));

			// When & Then
			assertThatThrownBy(() ->
				leaveCrewService.leaveCrew("CREW-1", "stranger"))
				.isInstanceOf(BusinessException.class)
				.extracting(e ->
					((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.CREW_MEMBER_NOT_FOUND);
		}

		@Test
		@DisplayName("LEADER 탈퇴 → LEADER_CANNOT_LEAVE")
		void leaderCannotLeave_throws() {
			// Given
			Crew crew = recruitingCrewWithLeaderAndMember(
				"leader-1", "member-1");
			given(crewRepositoryPort.findByIdWithLock("CREW-1"))
				.willReturn(Optional.of(crew));

			// When & Then
			assertThatThrownBy(() ->
				leaveCrewService.leaveCrew("CREW-1", "leader-1"))
				.isInstanceOf(BusinessException.class)
				.extracting(e ->
					((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.LEADER_CANNOT_LEAVE);
		}

		@Test
		@DisplayName("ACTIVE + 챌린지 미시작 멤버는 탈퇴 가능")
		void activeCrewWithoutChallenge_success() {
			// Given
			Crew crew = activeCrewWithLeaderAndMember(
				"leader-1", "member-1");
			given(crewRepositoryPort.findByIdWithLock("CREW-1"))
				.willReturn(Optional.of(crew));
			given(challengeRepositoryPort
				.existsByUserIdAndCrewId("member-1", "CREW-1"))
				.willReturn(false);
			given(crewRepositoryPort.save(crew)).willReturn(crew);

			// When & Then
			assertThatCode(() ->
				leaveCrewService.leaveCrew("CREW-1", "member-1"))
				.doesNotThrowAnyException();
			verify(crewRepositoryPort).save(crew);
			verify(crewRepositoryPort)
				.deleteMemberByCrewIdAndUserId("CREW-1", "member-1");
		}

		@Test
		@DisplayName("ACTIVE + 챌린지 시작 멤버 → CANNOT_LEAVE_ACTIVE_CREW")
		void activeCrewWithChallenge_throws() {
			// Given
			Crew crew = activeCrewWithLeaderAndMember(
				"leader-1", "member-1");
			given(crewRepositoryPort.findByIdWithLock("CREW-1"))
				.willReturn(Optional.of(crew));
			given(challengeRepositoryPort
				.existsByUserIdAndCrewId("member-1", "CREW-1"))
				.willReturn(true);

			// When & Then
			assertThatThrownBy(() ->
				leaveCrewService.leaveCrew("CREW-1", "member-1"))
				.isInstanceOf(BusinessException.class)
				.extracting(e ->
					((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.CANNOT_LEAVE_ACTIVE_CREW);
		}

		@Test
		@DisplayName("COMPLETED 크루 탈퇴 → CANNOT_LEAVE_ACTIVE_CREW")
		void completedCrew_throws() {
			// Given
			CrewMember leader = CrewMember.of("CRMB-1", "leader-1",
				"CREW-1", CrewRole.LEADER, LocalDateTime.now());
			CrewMember member = CrewMember.of("CRMB-2", "member-1",
				"CREW-1", CrewRole.MEMBER, LocalDateTime.now());
			Crew crew = Crew.of("CREW-1", "leader-1", "크루", "목표",
				"인증", VerificationType.TEXT, 10, 2,
				CrewStatus.COMPLETED,
				LocalDate.now().minusDays(20),
				LocalDate.now().minusDays(1), true, "ABC123",
				LocalDateTime.now(), LocalTime.of(23, 59, 59),
				null, null, 0L, List.of(leader, member));
			given(crewRepositoryPort.findByIdWithLock("CREW-1"))
				.willReturn(Optional.of(crew));
			given(challengeRepositoryPort
				.existsByUserIdAndCrewId("member-1", "CREW-1"))
				.willReturn(false);

			// When & Then
			assertThatThrownBy(() ->
				leaveCrewService.leaveCrew("CREW-1", "member-1"))
				.isInstanceOf(BusinessException.class)
				.extracting(e ->
					((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.CANNOT_LEAVE_ACTIVE_CREW);
		}
	}

	@Nested
	@DisplayName("낙관적 락 경로 (OPTIMISTIC)")
	class OptimisticLock {

		@BeforeEach
		void setUp() {
			given(lockProperties.isPessimistic()).willReturn(false);
			given(lockProperties.getMaxRetry()).willReturn(3);
			given(txTemplate.execute(any())).willAnswer(inv -> {
				TransactionCallback<?> cb = inv.getArgument(0);
				return cb.doInTransaction(null);
			});
		}

		@Test
		@DisplayName("version 일치 시 첫 시도에 탈퇴 성공한다")
		void optimisticLeave_firstAttemptSuccess() {
			// Given
			Crew crew = recruitingCrewWithLeaderAndMember(
				"leader-1", "member-1");
			given(crewRepositoryPort.findById("CREW-1"))
				.willReturn(Optional.of(crew));
			given(crewRepositoryPort
				.updateCurrentMembersWithVersion(
					"CREW-1", 1, 0L))
				.willReturn(1);

			// When & Then
			assertThatCode(() ->
				leaveCrewService.leaveCrew("CREW-1", "member-1"))
				.doesNotThrowAnyException();
			verify(crewRepositoryPort)
				.deleteMemberByCrewIdAndUserId("CREW-1", "member-1");
		}

		@Test
		@DisplayName("version 충돌 후 재시도에서 탈퇴 성공한다")
		void optimisticLeave_retrySuccess() {
			// Given — 매 시도마다 fresh crew
			given(crewRepositoryPort.findById("CREW-1"))
				.willAnswer(inv -> Optional.of(
					recruitingCrewWithLeaderAndMember(
						"leader-1", "member-1")));
			given(crewRepositoryPort
				.updateCurrentMembersWithVersion(
					"CREW-1", 1, 0L))
				.willReturn(0)
				.willReturn(1);

			// When & Then
			assertThatCode(() ->
				leaveCrewService.leaveCrew("CREW-1", "member-1"))
				.doesNotThrowAnyException();
			verify(crewRepositoryPort)
				.deleteMemberByCrewIdAndUserId("CREW-1", "member-1");
		}

		@Test
		@DisplayName("maxRetry 초과 시 CREW_JOIN_CONFLICT 예외 발생")
		void optimisticLeave_maxRetryExceeded_throwsConflict() {
			// Given — 3회 모두 version 충돌
			given(crewRepositoryPort.findById("CREW-1"))
				.willAnswer(inv -> Optional.of(
					recruitingCrewWithLeaderAndMember(
						"leader-1", "member-1")));
			given(crewRepositoryPort
				.updateCurrentMembersWithVersion(
					"CREW-1", 1, 0L))
				.willReturn(0);

			// When & Then
			assertThatThrownBy(() ->
				leaveCrewService.leaveCrew("CREW-1", "member-1"))
				.isInstanceOf(BusinessException.class)
				.extracting(e ->
					((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.CREW_JOIN_CONFLICT);
		}
	}

	// --- 헬퍼 ---

	private static Crew recruitingCrewWithLeader(String leaderId) {
		CrewMember leader = CrewMember.of("CRMB-1", leaderId,
			"CREW-1", CrewRole.LEADER, LocalDateTime.now());
		return Crew.of("CREW-1", leaderId, "테스트 크루", "목표",
			"인증 내용", VerificationType.TEXT, 10, 1,
			CrewStatus.RECRUITING, LocalDate.now().plusDays(1),
			LocalDate.now().plusDays(14), true, "ABC123",
			LocalDateTime.now(), LocalTime.of(23, 59, 59),
			null, null, 0L, List.of(leader));
	}

	private static Crew recruitingCrewWithLeaderAndMember(
			String leaderId, String memberId) {
		CrewMember leader = CrewMember.of("CRMB-1", leaderId,
			"CREW-1", CrewRole.LEADER, LocalDateTime.now());
		CrewMember member = CrewMember.of("CRMB-2", memberId,
			"CREW-1", CrewRole.MEMBER, LocalDateTime.now());
		return Crew.of("CREW-1", leaderId, "테스트 크루", "목표",
			"인증 내용", VerificationType.TEXT, 10, 2,
			CrewStatus.RECRUITING, LocalDate.now().plusDays(1),
			LocalDate.now().plusDays(14), true, "ABC123",
			LocalDateTime.now(), LocalTime.of(23, 59, 59),
			null, null, 0L, List.of(leader, member));
	}

	private static Crew activeCrewWithLeaderAndMember(
			String leaderId, String memberId) {
		CrewMember leader = CrewMember.of("CRMB-1", leaderId,
			"CREW-1", CrewRole.LEADER, LocalDateTime.now());
		CrewMember member = CrewMember.of("CRMB-2", memberId,
			"CREW-1", CrewRole.MEMBER, LocalDateTime.now());
		return Crew.of("CREW-1", leaderId, "테스트 크루", "목표",
			"인증 내용", VerificationType.TEXT, 10, 2,
			CrewStatus.ACTIVE, LocalDate.now().minusDays(1),
			LocalDate.now().plusDays(13), true, "ABC123",
			LocalDateTime.now(), LocalTime.of(23, 59, 59),
			null, null, 0L, List.of(leader, member));
	}
}
