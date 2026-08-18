package com.triagain.crew.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import com.triagain.common.exception.BusinessException;
import com.triagain.common.exception.ErrorCode;
import com.triagain.crew.domain.model.Crew;
import com.triagain.crew.domain.model.CrewMember;
import com.triagain.crew.domain.vo.CrewRole;
import com.triagain.crew.domain.vo.CrewStatus;
import com.triagain.crew.domain.vo.CrewVisibility;
import com.triagain.crew.domain.vo.VerificationType;
import com.triagain.crew.port.in.JoinCrewUseCase.JoinCrewCommand;
import com.triagain.crew.port.in.JoinCrewUseCase.JoinCrewResult;
import com.triagain.crew.port.out.CrewRepositoryPort;

@ExtendWith(MockitoExtension.class)
class JoinCrewServiceTest {

	@Mock
	private CrewRepositoryPort crewRepositoryPort;

	@Mock
	private CrewLockProperties lockProperties;

	@Mock
	private TransactionTemplate txTemplate;

	@InjectMocks
	private JoinCrewService joinCrewService;

	@Nested
	@DisplayName("비관적 락 경로 (PESSIMISTIC)")
	class PessimisticLock {

		@BeforeEach
		void setUp() {
			given(lockProperties.isPessimistic()).willReturn(true);
			given(txTemplate.execute(any())).willAnswer(invocation -> {
				TransactionCallback<?> cb = invocation.getArgument(0);
				return cb.doInTransaction(null);
			});
		}

		@Test
		@DisplayName("PUBLIC 크루에 정상 가입하면 MEMBER 역할로 등록된다")
		void joinPublicCrew_success() {
			// Given
			Crew crew = publicRecruitingCrew(
				LocalDate.now().plusDays(7),
				LocalDate.now().plusDays(30));
			given(crewRepositoryPort.findByIdWithLock("CREW-001"))
				.willReturn(Optional.of(crew));
			given(crewRepositoryPort.save(any())).willReturn(crew);
			given(crewRepositoryPort.saveMember(any()))
				.willReturn(null);

			JoinCrewCommand command =
				new JoinCrewCommand("user-1", "CREW-001");

			// When
			JoinCrewResult result = joinCrewService.joinCrew(command);

			// Then
			assertThat(result.role()).isEqualTo(CrewRole.MEMBER);
			assertThat(result.userId()).isEqualTo("user-1");
		}

		@Test
		@DisplayName("PRIVATE 크루에 직접 가입하면 CREW_NOT_PUBLIC 예외")
		void joinPrivateCrew_throwsNotPublic() {
			// Given
			Crew crew = privateRecruitingCrew(
				LocalDate.now().plusDays(7),
				LocalDate.now().plusDays(30));
			given(crewRepositoryPort.findByIdWithLock("CREW-001"))
				.willReturn(Optional.of(crew));

			JoinCrewCommand command =
				new JoinCrewCommand("user-1", "CREW-001");

			// When & Then
			assertThatThrownBy(() ->
				joinCrewService.joinCrew(command))
				.isInstanceOf(BusinessException.class)
				.extracting(e ->
					((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.CREW_NOT_PUBLIC);
		}

		@Test
		@DisplayName("정원 초과 시 CREW_FULL 예외가 발생한다")
		void joinFullCrew_throwsFull() {
			// Given
			Crew crew = publicCrewWithMembers(
				CrewStatus.RECRUITING, 2, 2,
				LocalDate.now().plusDays(7),
				LocalDate.now().plusDays(30));
			given(crewRepositoryPort.findByIdWithLock("CREW-001"))
				.willReturn(Optional.of(crew));

			JoinCrewCommand command =
				new JoinCrewCommand("user-1", "CREW-001");

			// When & Then
			assertThatThrownBy(() ->
				joinCrewService.joinCrew(command))
				.isInstanceOf(BusinessException.class)
				.extracting(e ->
					((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.CREW_FULL);
		}

		@Test
		@DisplayName("이미 참여한 유저가 다시 가입하면 CREW_ALREADY_JOINED")
		void joinAlreadyJoined_throws() {
			// Given
			CrewMember leader = CrewMember.of("CRMB-1", "leader",
				"CREW-001", CrewRole.LEADER, LocalDateTime.now());
			CrewMember existing = CrewMember.of("CRMB-2", "user-1",
				"CREW-001", CrewRole.MEMBER, LocalDateTime.now());
			Crew crew = Crew.of("CREW-001", "leader", "테스트", "목표",
				"인증", VerificationType.TEXT, 10, 2,
				CrewStatus.RECRUITING,
				LocalDate.now().plusDays(1),
				LocalDate.now().plusDays(30), true, "ABC123",
				LocalDateTime.now(), LocalTime.of(23, 59, 59),
				null, CrewVisibility.PUBLIC, 0L,
				List.of(leader, existing));
			given(crewRepositoryPort.findByIdWithLock("CREW-001"))
				.willReturn(Optional.of(crew));

			JoinCrewCommand command =
				new JoinCrewCommand("user-1", "CREW-001");

			// When & Then
			assertThatThrownBy(() ->
				joinCrewService.joinCrew(command))
				.isInstanceOf(BusinessException.class)
				.extracting(e ->
					((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.CREW_ALREADY_JOINED);
		}

		@Test
		@DisplayName("참여 마감일 초과 시 CREW_JOIN_DEADLINE_PASSED")
		void joinCrew_deadlinePassed() {
			// Given — endDate가 내일 → endDate-3 = 이틀 전 → 마감 초과
			LocalDate endDate = LocalDate.now().plusDays(1);
			LocalDate startDate = endDate.minusDays(7);
			Crew crew = publicRecruitingCrew(startDate, endDate);

			given(crewRepositoryPort.findByIdWithLock("CREW-001"))
				.willReturn(Optional.of(crew));

			JoinCrewCommand command =
				new JoinCrewCommand("user-1", "CREW-001");

			// When & Then
			assertThatThrownBy(() ->
				joinCrewService.joinCrew(command))
				.isInstanceOf(BusinessException.class)
				.extracting(e ->
					((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.CREW_JOIN_DEADLINE_PASSED);
		}

		@Test
		@DisplayName("마감일 경계값 — endDate-3일 당일이면 가입 가능")
		void joinCrew_deadlineBoundary_succeeds() {
			// Given — endDate-3 = 오늘 → isAfter false → 통과
			LocalDate endDate = LocalDate.now().plusDays(3);
			LocalDate startDate = endDate.minusDays(7);
			Crew crew = publicRecruitingCrew(startDate, endDate);

			given(crewRepositoryPort.findByIdWithLock("CREW-001"))
				.willReturn(Optional.of(crew));
			given(crewRepositoryPort.save(crew)).willReturn(crew);
			given(crewRepositoryPort.saveMember(any()))
				.willReturn(null);

			JoinCrewCommand command =
				new JoinCrewCommand("user-1", "CREW-001");

			// When & Then
			assertThatCode(() ->
				joinCrewService.joinCrew(command))
				.doesNotThrowAnyException();
		}
	}

	@Nested
	@DisplayName("낙관적 락 경로 (OPTIMISTIC)")
	class OptimisticLock {

		@BeforeEach
		void setUp() {
			given(lockProperties.isPessimistic()).willReturn(false);
			given(lockProperties.getMaxRetry()).willReturn(3);
			given(txTemplate.execute(any())).willAnswer(invocation -> {
				TransactionCallback<?> cb = invocation.getArgument(0);
				return cb.doInTransaction(null);
			});
		}

		@Test
		@DisplayName("version 일치 시 첫 시도에 가입 성공한다")
		void optimisticJoin_firstAttemptSuccess() {
			// Given
			Crew crew = publicRecruitingCrew(
				LocalDate.now().plusDays(7),
				LocalDate.now().plusDays(30));
			given(crewRepositoryPort.findById("CREW-001"))
				.willReturn(Optional.of(crew));
			given(crewRepositoryPort
				.updateCurrentMembersWithVersion(
					"CREW-001", 2, 0L))
				.willReturn(1);
			given(crewRepositoryPort.saveMember(any()))
				.willReturn(null);

			JoinCrewCommand command =
				new JoinCrewCommand("user-1", "CREW-001");

			// When
			JoinCrewResult result = joinCrewService.joinCrew(command);

			// Then
			assertThat(result.role()).isEqualTo(CrewRole.MEMBER);
			assertThat(result.userId()).isEqualTo("user-1");
			assertThat(result.currentMembers()).isEqualTo(2);
		}

		@Test
		@DisplayName("version 충돌 후 재시도에서 성공한다")
		void optimisticJoin_retrySuccess() {
			// Given — 매 시도마다 fresh crew 반환 (addMember 부작용 회피)
			given(crewRepositoryPort.findById("CREW-001"))
				.willAnswer(inv -> Optional.of(publicRecruitingCrew(
					LocalDate.now().plusDays(7),
					LocalDate.now().plusDays(30))));
			given(crewRepositoryPort
				.updateCurrentMembersWithVersion(
					"CREW-001", 2, 0L))
				.willReturn(0)
				.willReturn(1);
			given(crewRepositoryPort.saveMember(any()))
				.willReturn(null);

			JoinCrewCommand command =
				new JoinCrewCommand("user-1", "CREW-001");

			// When
			JoinCrewResult result = joinCrewService.joinCrew(command);

			// Then
			assertThat(result).isNotNull();
			assertThat(result.userId()).isEqualTo("user-1");
		}

		@Test
		@DisplayName("maxRetry 초과 시 CREW_JOIN_CONFLICT 예외 발생")
		void optimisticJoin_maxRetryExceeded_throwsConflict() {
			// Given — 매 시도마다 fresh crew, 3회 모두 version 충돌
			given(crewRepositoryPort.findById("CREW-001"))
				.willAnswer(inv -> Optional.of(publicRecruitingCrew(
					LocalDate.now().plusDays(7),
					LocalDate.now().plusDays(30))));
			given(crewRepositoryPort
				.updateCurrentMembersWithVersion(
					"CREW-001", 2, 0L))
				.willReturn(0);

			JoinCrewCommand command =
				new JoinCrewCommand("user-1", "CREW-001");

			// When & Then
			assertThatThrownBy(() ->
				joinCrewService.joinCrew(command))
				.isInstanceOf(BusinessException.class)
				.extracting(e ->
					((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.CREW_JOIN_CONFLICT);
		}
	}

	@Nested
	@DisplayName("조건부 원자적 UPDATE 경로 (CONDITIONAL)")
	class ConditionalLock {

		@BeforeEach
		void setUp() {
			given(lockProperties.isPessimistic()).willReturn(false);
			given(lockProperties.isConditional()).willReturn(true);
			given(txTemplate.execute(any())).willAnswer(invocation -> {
				TransactionCallback<?> cb = invocation.getArgument(0);
				return cb.doInTransaction(null);
			});
		}

		@Test
		@DisplayName("incrementMembersIfNotFull이 1을 반환하면 가입에 성공한다")
		void conditionalJoin_success() {
			// Given
			Crew crew = publicRecruitingCrew(
				LocalDate.now().plusDays(7),
				LocalDate.now().plusDays(30));
			given(crewRepositoryPort.findById("CREW-001"))
				.willReturn(Optional.of(crew));
			given(crewRepositoryPort.incrementMembersIfNotFull("CREW-001"))
				.willReturn(1);
			given(crewRepositoryPort.saveMemberAndFlush(any()))
				.willReturn(null);

			JoinCrewCommand command =
				new JoinCrewCommand("user-1", "CREW-001");

			// When
			JoinCrewResult result = joinCrewService.joinCrew(command);

			// Then
			assertThat(result.role()).isEqualTo(CrewRole.MEMBER);
			assertThat(result.userId()).isEqualTo("user-1");
		}

		@Test
		@DisplayName("incrementMembersIfNotFull이 0을 반환하면 CREW_FULL 예외가 발생한다")
		void conditionalJoin_full() {
			// Given
			Crew crew = publicRecruitingCrew(
				LocalDate.now().plusDays(7),
				LocalDate.now().plusDays(30));
			given(crewRepositoryPort.findById("CREW-001"))
				.willReturn(Optional.of(crew));
			given(crewRepositoryPort.incrementMembersIfNotFull("CREW-001"))
				.willReturn(0);

			JoinCrewCommand command =
				new JoinCrewCommand("user-1", "CREW-001");

			// When & Then
			assertThatThrownBy(() ->
				joinCrewService.joinCrew(command))
				.isInstanceOf(BusinessException.class)
				.extracting(e ->
					((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.CREW_FULL);
		}

		@Test
		@DisplayName("saveMemberAndFlush가 DataIntegrityViolationException을 던지면 CREW_ALREADY_JOINED")
		void conditionalJoin_duplicateConcurrent() {
			// ⚠️ U3: mock으로 DataIntegrityViolationException을 throw시키므로
			// flush 누락 버그(plain save)는 여기서는 못 잡음 — 실DB T2(JoinCrewConcurrencyE2eTest)가 필수
			// Given
			Crew crew = publicRecruitingCrew(
				LocalDate.now().plusDays(7),
				LocalDate.now().plusDays(30));
			given(crewRepositoryPort.findById("CREW-001"))
				.willReturn(Optional.of(crew));
			given(crewRepositoryPort.incrementMembersIfNotFull("CREW-001"))
				.willReturn(1);
			given(crewRepositoryPort.saveMemberAndFlush(any()))
				.willThrow(new DataIntegrityViolationException("uq_crew_members_crew_id_user_id"));

			JoinCrewCommand command =
				new JoinCrewCommand("user-1", "CREW-001");

			// When & Then
			assertThatThrownBy(() ->
				joinCrewService.joinCrew(command))
				.isInstanceOf(BusinessException.class)
				.extracting(e ->
					((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.CREW_ALREADY_JOINED);
		}

		@Test
		@DisplayName("PRIVATE 크루에 직접 가입하면 CREW_NOT_PUBLIC 예외")
		void conditionalJoin_privateCrew() {
			// Given
			Crew crew = privateRecruitingCrew(
				LocalDate.now().plusDays(7),
				LocalDate.now().plusDays(30));
			given(crewRepositoryPort.findById("CREW-001"))
				.willReturn(Optional.of(crew));

			JoinCrewCommand command =
				new JoinCrewCommand("user-1", "CREW-001");

			// When & Then
			assertThatThrownBy(() ->
				joinCrewService.joinCrew(command))
				.isInstanceOf(BusinessException.class)
				.extracting(e ->
					((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.CREW_NOT_PUBLIC);
		}

		@Test
		@DisplayName("참여 마감일 초과 시 CREW_JOIN_DEADLINE_PASSED")
		void conditionalJoin_deadlinePassed() {
			// Given — endDate가 내일 → endDate-3 = 이틀 전 → 마감 초과
			LocalDate endDate = LocalDate.now().plusDays(1);
			LocalDate startDate = endDate.minusDays(7);
			Crew crew = publicRecruitingCrew(startDate, endDate);
			given(crewRepositoryPort.findById("CREW-001"))
				.willReturn(Optional.of(crew));

			JoinCrewCommand command =
				new JoinCrewCommand("user-1", "CREW-001");

			// When & Then
			assertThatThrownBy(() ->
				joinCrewService.joinCrew(command))
				.isInstanceOf(BusinessException.class)
				.extracting(e ->
					((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.CREW_JOIN_DEADLINE_PASSED);
		}
	}

	// --- 헬퍼 메서드 ---

	private static Crew publicRecruitingCrew(
			LocalDate startDate, LocalDate endDate) {
		return Crew.of("CREW-001", "creator-1", "테스트 크루", "목표",
			"인증 내용", VerificationType.TEXT, 10, 1,
			CrewStatus.RECRUITING, startDate, endDate,
			true, "ABC123", LocalDateTime.now(),
			LocalTime.of(23, 59, 59), null,
			CrewVisibility.PUBLIC, 0L, Collections.emptyList());
	}

	private static Crew privateRecruitingCrew(
			LocalDate startDate, LocalDate endDate) {
		return Crew.of("CREW-001", "creator-1", "테스트 크루", "목표",
			"인증 내용", VerificationType.TEXT, 10, 1,
			CrewStatus.RECRUITING, startDate, endDate,
			true, "ABC123", LocalDateTime.now(),
			LocalTime.of(23, 59, 59), null,
			CrewVisibility.PRIVATE, 0L, Collections.emptyList());
	}

	private static Crew publicCrewWithMembers(CrewStatus status,
			int maxMembers, int currentMembers,
			LocalDate startDate, LocalDate endDate) {
		return Crew.of("CREW-001", "creator-1", "테스트 크루", "목표",
			"인증 내용", VerificationType.TEXT,
			maxMembers, currentMembers, status, startDate,
			endDate, true, "ABC123", LocalDateTime.now(),
			LocalTime.of(23, 59, 59), null,
			CrewVisibility.PUBLIC, 0L, Collections.emptyList());
	}
}
