package com.triagain.crew.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import com.triagain.common.exception.BusinessException;
import com.triagain.common.exception.ErrorCode;
import com.triagain.crew.domain.model.Challenge;
import com.triagain.crew.domain.model.Crew;
import com.triagain.crew.domain.vo.ChallengeStatus;
import com.triagain.crew.domain.vo.CrewStatus;
import com.triagain.crew.domain.vo.VerificationType;
import com.triagain.crew.port.out.ChallengeRepositoryPort;
import com.triagain.crew.port.out.CrewRepositoryPort;

@ExtendWith(MockitoExtension.class)
class FindOrCreateActiveChallengeServiceTest {

	private static final ZoneId ZONE = ZoneId.systemDefault();
	private static final LocalDateTime FIXED_NOW = LocalDateTime.of(2026, 4, 13, 14, 0, 0);
	private static final Clock FIXED_CLOCK = Clock.fixed(
			FIXED_NOW.atZone(ZONE).toInstant(), ZONE);
	private static final LocalDate TODAY = FIXED_NOW.toLocalDate();

	private static final String USER_ID = "user-1";
	private static final String CREW_ID = "crew-1";
	private static final LocalTime DEADLINE_TIME = LocalTime.of(23, 59, 59);

	@Mock
	private ChallengeRepositoryPort challengeRepositoryPort;

	@Mock
	private CrewRepositoryPort crewRepositoryPort;

	private FindOrCreateActiveChallengeService service;

	@BeforeEach
	void setUp() {
		service = new FindOrCreateActiveChallengeService(
				challengeRepositoryPort, crewRepositoryPort, FIXED_CLOCK);
	}

	@Test
	@DisplayName("IN_PROGRESS 챌린지 존재 시 그대로 반환")
	void existingInProgressChallenge_returned() {
		// Given
		Challenge existing = Challenge.of("CHAL-1", USER_ID, CREW_ID, 1, 3, 1,
				ChallengeStatus.IN_PROGRESS, TODAY,
				FIXED_NOW.plusDays(3), FIXED_NOW);
		given(challengeRepositoryPort.findByUserIdAndCrewIdAndStatusWithLock(
				USER_ID, CREW_ID, ChallengeStatus.IN_PROGRESS))
				.willReturn(Optional.of(existing));

		// When
		Challenge result = service.findOrCreate(USER_ID, CREW_ID);

		// Then
		assertThat(result.getId()).isEqualTo("CHAL-1");
		verify(crewRepositoryPort, never()).findById(any());
	}

	@Test
	@DisplayName("첫 챌린지 생성 — maxCycleNumber=0이면 createFirst")
	void noExistingChallenge_createsFirst() {
		// Given
		Crew crew = activeCrew(CREW_ID, TODAY.minusDays(1), TODAY.plusDays(30));

		given(challengeRepositoryPort.findByUserIdAndCrewIdAndStatusWithLock(
				USER_ID, CREW_ID, ChallengeStatus.IN_PROGRESS))
				.willReturn(Optional.empty());
		given(crewRepositoryPort.findById(CREW_ID)).willReturn(Optional.of(crew));
		given(challengeRepositoryPort.findMaxCycleNumber(USER_ID, CREW_ID)).willReturn(0);
		given(challengeRepositoryPort.save(any())).willAnswer(inv -> inv.getArgument(0));

		// When
		Challenge result = service.findOrCreate(USER_ID, CREW_ID);

		// Then
		assertThat(result.getCycleNumber()).isEqualTo(1);
		assertThat(result.getStatus()).isEqualTo(ChallengeStatus.IN_PROGRESS);
		assertThat(result.getStartDate()).isEqualTo(TODAY);
	}

	@Test
	@DisplayName("재도전 챌린지 생성 — maxCycleNumber=2이면 cycleNumber=3")
	void existingCycles_createsNext() {
		// Given
		Crew crew = activeCrew(CREW_ID, TODAY.minusDays(10), TODAY.plusDays(20));

		given(challengeRepositoryPort.findByUserIdAndCrewIdAndStatusWithLock(
				USER_ID, CREW_ID, ChallengeStatus.IN_PROGRESS))
				.willReturn(Optional.empty());
		given(crewRepositoryPort.findById(CREW_ID)).willReturn(Optional.of(crew));
		given(challengeRepositoryPort.findMaxCycleNumber(USER_ID, CREW_ID)).willReturn(2);
		given(challengeRepositoryPort.save(any())).willAnswer(inv -> inv.getArgument(0));

		// When
		Challenge result = service.findOrCreate(USER_ID, CREW_ID);

		// Then
		assertThat(result.getCycleNumber()).isEqualTo(3);
	}

	@Test
	@DisplayName("크루 endDate가 가까��면 deadline이 endDate로 제한된다")
	void crewEndingSoon_deadlineClampedToEndDate() {
		// Given — endDate가 내일
		LocalDate endDate = TODAY.plusDays(1);
		Crew crew = activeCrew(CREW_ID, TODAY.minusDays(5), endDate);

		given(challengeRepositoryPort.findByUserIdAndCrewIdAndStatusWithLock(
				USER_ID, CREW_ID, ChallengeStatus.IN_PROGRESS))
				.willReturn(Optional.empty());
		given(crewRepositoryPort.findById(CREW_ID)).willReturn(Optional.of(crew));
		given(challengeRepositoryPort.findMaxCycleNumber(USER_ID, CREW_ID)).willReturn(0);
		given(challengeRepositoryPort.save(any())).willAnswer(inv -> inv.getArgument(0));

		// When
		Challenge result = service.findOrCreate(USER_ID, CREW_ID);

		// Then — deadline은 endDate의 deadlineTime
		assertThat(result.getDeadline()).isEqualTo(endDate.atTime(DEADLINE_TIME));
	}

	@Test
	@DisplayName("COMPLETED 크루에서 챌린지 생성 시도 → CREW_NOT_ACTIVE")
	void completedCrew_throwsNotActive() {
		// Given
		Crew completedCrew = Crew.of(CREW_ID, "creator-1", "크루", "목표",
				"인증 내용", VerificationType.TEXT, 10, 1, CrewStatus.COMPLETED,
				TODAY.minusDays(10), TODAY.minusDays(1),
				false, "ABC123", FIXED_NOW, DEADLINE_TIME, null, null, Collections.emptyList());

		given(challengeRepositoryPort.findByUserIdAndCrewIdAndStatusWithLock(
				USER_ID, CREW_ID, ChallengeStatus.IN_PROGRESS))
				.willReturn(Optional.empty());
		given(crewRepositoryPort.findById(CREW_ID)).willReturn(Optional.of(completedCrew));

		// When & Then
		assertThatThrownBy(() -> service.findOrCreate(USER_ID, CREW_ID))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.CREW_NOT_ACTIVE);
	}

	@Test
	@DisplayName("크루 기간 만료 후 챌린지 생성 시도 → CREW_PERIOD_ENDED")
	void crewPeriodEnded_throws() {
		// Given — ACTIVE지만 endDate 지남
		Crew expiredCrew = Crew.of(CREW_ID, "creator-1", "크루", "목표",
				"인증 내용", VerificationType.TEXT, 10, 1, CrewStatus.ACTIVE,
				TODAY.minusDays(10), TODAY.minusDays(1),
				false, "ABC123", FIXED_NOW, DEADLINE_TIME, null, null, Collections.emptyList());

		given(challengeRepositoryPort.findByUserIdAndCrewIdAndStatusWithLock(
				USER_ID, CREW_ID, ChallengeStatus.IN_PROGRESS))
				.willReturn(Optional.empty());
		given(crewRepositoryPort.findById(CREW_ID)).willReturn(Optional.of(expiredCrew));

		// When & Then
		assertThatThrownBy(() -> service.findOrCreate(USER_ID, CREW_ID))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.CREW_PERIOD_ENDED);
	}

	@Test
	@DisplayName("동시 요청 — DataIntegrityViolationException 발생 시 재조회")
	void concurrentRequest_retriesOnConstraintViolation() {
		// Given
		Crew crew = activeCrew(CREW_ID, TODAY.minusDays(1), TODAY.plusDays(30));
		Challenge existingFromOtherThread = Challenge.of("CHAL-OTHER", USER_ID, CREW_ID, 1, 3, 0,
				ChallengeStatus.IN_PROGRESS, TODAY,
				FIXED_NOW.plusDays(3), FIXED_NOW);

		given(challengeRepositoryPort.findByUserIdAndCrewIdAndStatusWithLock(
				USER_ID, CREW_ID, ChallengeStatus.IN_PROGRESS))
				.willReturn(Optional.empty())
				.willReturn(Optional.of(existingFromOtherThread));
		given(crewRepositoryPort.findById(CREW_ID)).willReturn(Optional.of(crew));
		given(challengeRepositoryPort.findMaxCycleNumber(USER_ID, CREW_ID)).willReturn(0);
		given(challengeRepositoryPort.save(any())).willThrow(new DataIntegrityViolationException("UK violation"));

		// When
		Challenge result = service.findOrCreate(USER_ID, CREW_ID);

		// Then — 재조회해서 다른 스레드가 만든 챌린지 반환
		assertThat(result.getId()).isEqualTo("CHAL-OTHER");
	}

	@Test
	@DisplayName("오늘의 deadline_time + grace period 초과 시 → VERIFICATION_DEADLINE_EXCEEDED")
	void deadlineTimeExceeded_throws() {
		// Given — deadlineTime을 고정 시각(14:00) 6분 전으로 설정 (grace 5분 초과)
		LocalTime pastDeadline = LocalTime.of(13, 54);
		Crew crew = Crew.of(CREW_ID, "creator-1", "크루", "목표",
				"인증 내용", VerificationType.TEXT, 10, 1, CrewStatus.ACTIVE,
				TODAY.minusDays(1), TODAY.plusDays(30),
				false, "ABC123", FIXED_NOW, pastDeadline, null, null, Collections.emptyList());

		given(challengeRepositoryPort.findByUserIdAndCrewIdAndStatusWithLock(
				USER_ID, CREW_ID, ChallengeStatus.IN_PROGRESS))
				.willReturn(Optional.empty());
		given(crewRepositoryPort.findById(CREW_ID)).willReturn(Optional.of(crew));

		// When & Then
		assertThatThrownBy(() -> service.findOrCreate(USER_ID, CREW_ID))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.VERIFICATION_DEADLINE_EXCEEDED);
	}

	// --- 헬퍼 메서드 ---

	private static Crew activeCrew(String id, LocalDate startDate, LocalDate endDate) {
		return Crew.of(id, "creator-1", "테스트 크루", "목표",
				"인증 내용", VerificationType.TEXT, 10, 1, CrewStatus.ACTIVE,
				startDate, endDate, false, "ABC123",
				FIXED_NOW, DEADLINE_TIME, null, null, Collections.emptyList());
	}
}
