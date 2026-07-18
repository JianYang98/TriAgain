package com.triagain.verification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.triagain.common.exception.BusinessException;
import com.triagain.common.exception.ErrorCode;
import com.triagain.verification.domain.model.UploadSession;
import com.triagain.verification.port.in.CreateUploadSessionUseCase.CreateUploadSessionCommand;
import com.triagain.verification.port.out.ChallengePort;
import com.triagain.verification.port.out.ChallengePort.ActiveChallengeInfo;
import com.triagain.verification.port.out.CrewPort;
import com.triagain.verification.port.out.CrewPort.CrewVerificationWindowInfo;
import com.triagain.verification.port.out.HabitPort;
import com.triagain.common.port.out.StoragePort;
import com.triagain.verification.port.out.UploadSessionRepositoryPort;

@ExtendWith(MockitoExtension.class)
class CreateUploadSessionServiceTest {

	private static final ZoneId ZONE = ZoneId.systemDefault();
	private static final LocalDateTime FIXED_NOW = LocalDateTime.of(2026, 4, 13, 14, 0, 0);
	private static final Clock FIXED_CLOCK = Clock.fixed(
			FIXED_NOW.atZone(ZONE).toInstant(), ZONE);
	private static final LocalDate TODAY = FIXED_NOW.toLocalDate();

	private static final String USER_ID = "user-1";
	private static final String CREW_ID = "crew-1";
	private static final String HABIT_ID = "habit-1";
	private static final String CHALLENGE_ID = "challenge-1";
	private static final String FILE_NAME = "photo.jpg";
	private static final String FILE_TYPE = "image/jpeg";
	private static final long FILE_SIZE = 1024 * 1024; // 1MB
	private static final String IMAGE_KEY = "upload-sessions/user-1/abc123.jpg";
	private static final String PRESIGNED_URL = "https://s3.example.com/presigned";
	private static final String IMAGE_URL = "https://s3.example.com/image.jpg";

	@Mock
	private UploadSessionRepositoryPort uploadSessionRepositoryPort;

	@Mock
	private StoragePort storagePort;

	@Mock
	private ChallengePort challengePort;

	@Mock
	private CrewPort crewPort;

	@Mock
	private HabitPort habitPort;

	private CreateUploadSessionService createUploadSessionService;

	@BeforeEach
	void setUp() {
		createUploadSessionService = new CreateUploadSessionService(
				uploadSessionRepositoryPort, storagePort, challengePort, crewPort, habitPort, FIXED_CLOCK);
	}

	private static CrewVerificationWindowInfo activePhotoCrew(LocalTime deadlineTime) {
		return new CrewVerificationWindowInfo(
				"PHOTO", "ACTIVE",
				TODAY.minusDays(1), TODAY.plusDays(10),
				false, deadlineTime
		);
	}

	private static ActiveChallengeInfo activeChallengeWithDeadline(LocalDateTime deadline) {
		return activeChallengeWithDeadline(TODAY.minusDays(1), deadline);
	}

	private static ActiveChallengeInfo activeChallengeWithDeadline(LocalDate startDate, LocalDateTime deadline) {
		return new ActiveChallengeInfo(CHALLENGE_ID, "IN_PROGRESS", 1, 3, startDate, deadline);
	}

	/** 고정 시각을 다르게 주입한 별도 서비스 인스턴스 — T-U8(b)처럼 클래스 공용 FIXED_CLOCK과 다른 시각이 필요할 때 사용 */
	private CreateUploadSessionService serviceAt(LocalDateTime fixedNow) {
		Clock fixedClock = Clock.fixed(fixedNow.atZone(ZONE).toInstant(), ZONE);
		return new CreateUploadSessionService(
				uploadSessionRepositoryPort, storagePort, challengePort, crewPort, habitPort, fixedClock);
	}

	private CreateUploadSessionCommand defaultCommand() {
		return new CreateUploadSessionCommand(USER_ID, CREW_ID, null, FILE_NAME, FILE_TYPE, FILE_SIZE);
	}

	private void stubMembershipAndCrewInfo(CrewVerificationWindowInfo crewInfo) {
		doNothing().when(crewPort).validateMembership(CREW_ID, USER_ID);
		given(crewPort.getCrewVerificationWindowInfo(CREW_ID)).willReturn(crewInfo);
	}

	private void stubStorageAndRepository() {
		given(storagePort.generateImageKey(anyString(), anyString())).willReturn(IMAGE_KEY);
		given(storagePort.generatePresignedUrl(anyString(), anyString(), anyLong())).willReturn(PRESIGNED_URL);
		given(storagePort.getImageUrl(anyString())).willReturn(IMAGE_URL);
		given(uploadSessionRepositoryPort.save(any(UploadSession.class)))
				.willAnswer(invocation -> {
					UploadSession session = invocation.getArgument(0);
					return UploadSession.of(1L, session.getUserId(), session.getCrewId(), session.getHabitId(),
							session.getImageKey(), session.getContentType(),
							session.getStatus(), session.getRequestedAt(), session.getCreatedAt());
				});
	}

	@Test
	@DisplayName("활성 챌린지 있고 마감 전 → 성공")
	void activeChallengeBeforeDeadline_success() {
		// Given
		LocalDateTime deadline = FIXED_NOW.plusMinutes(30);
		stubMembershipAndCrewInfo(activePhotoCrew(LocalTime.of(23, 59, 59)));
		given(challengePort.findActiveByUserIdAndCrewId(USER_ID, CREW_ID))
				.willReturn(Optional.of(activeChallengeWithDeadline(deadline)));
		stubStorageAndRepository();

		// When
		var result = createUploadSessionService.createUploadSession(defaultCommand());

		// Then
		assertThat(result).isNotNull();
		assertThat(result.presignedUrl()).isEqualTo(PRESIGNED_URL);
	}

	@Test
	@DisplayName("활성 챌린지 있고 마감 + 3분 → 성공 (grace period 5분 이내)")
	void activeChallengeWithinGrace_success() {
		// Given
		LocalDateTime deadline = FIXED_NOW.minusMinutes(3);
		stubMembershipAndCrewInfo(activePhotoCrew(LocalTime.of(23, 59, 59)));
		given(challengePort.findActiveByUserIdAndCrewId(USER_ID, CREW_ID))
				.willReturn(Optional.of(activeChallengeWithDeadline(deadline)));
		stubStorageAndRepository();

		// When
		var result = createUploadSessionService.createUploadSession(defaultCommand());

		// Then
		assertThat(result).isNotNull();
	}

	@Test
	@DisplayName("활성 챌린지 있고 마감 + 6분 → VERIFICATION_DEADLINE_EXCEEDED (grace 초과)")
	void activeChallengeExceedsGrace_throws() {
		// Given
		LocalDateTime deadline = FIXED_NOW.minusMinutes(6);
		stubMembershipAndCrewInfo(activePhotoCrew(LocalTime.of(23, 59, 59)));
		given(challengePort.findActiveByUserIdAndCrewId(USER_ID, CREW_ID))
				.willReturn(Optional.of(activeChallengeWithDeadline(deadline)));

		// When & Then
		assertThatThrownBy(() -> createUploadSessionService.createUploadSession(defaultCommand()))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.VERIFICATION_DEADLINE_EXCEEDED);
	}

	@Test
	@DisplayName("활성 챌린지 없고 크루 마감 전 → 성공 (crew-level deadline)")
	void noActiveChallengeBeforeCrewDeadline_success() {
		// Given — deadlineTime을 23:59:59로 설정하면 오늘 마감 전
		stubMembershipAndCrewInfo(activePhotoCrew(LocalTime.of(23, 59, 59)));
		given(challengePort.findActiveByUserIdAndCrewId(USER_ID, CREW_ID))
				.willReturn(Optional.empty());
		stubStorageAndRepository();

		// When
		var result = createUploadSessionService.createUploadSession(defaultCommand());

		// Then
		assertThat(result).isNotNull();
		assertThat(result.presignedUrl()).isEqualTo(PRESIGNED_URL);
	}

	@Test
	@DisplayName("활성 챌린지 없고 크루 마감 후 → VERIFICATION_DEADLINE_EXCEEDED")
	void noActiveChallengeAfterCrewDeadline_throws() {
		// Given — deadlineTime을 고정 시각(14:00) 10분 전으로 설정 (grace 5분 초과)
		LocalTime pastDeadline = LocalTime.of(13, 50);
		stubMembershipAndCrewInfo(activePhotoCrew(pastDeadline));
		given(challengePort.findActiveByUserIdAndCrewId(USER_ID, CREW_ID))
				.willReturn(Optional.empty());

		// When & Then
		assertThatThrownBy(() -> createUploadSessionService.createUploadSession(defaultCommand()))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.VERIFICATION_DEADLINE_EXCEEDED);
	}

	@Test
	@DisplayName("비활성 크루 → CREW_NOT_ACTIVE")
	void inactiveCrew_throws() {
		// Given
		doNothing().when(crewPort).validateMembership(CREW_ID, USER_ID);
		given(crewPort.getCrewVerificationWindowInfo(CREW_ID))
				.willReturn(new CrewVerificationWindowInfo(
						"PHOTO", "RECRUITING",
						TODAY.plusDays(1), TODAY.plusDays(10),
						false, LocalTime.of(23, 59, 59)
				));

		// When & Then
		assertThatThrownBy(() -> createUploadSessionService.createUploadSession(defaultCommand()))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.CREW_NOT_ACTIVE);
	}

	@Test
	@DisplayName("크루 기간 종료 → CREW_PERIOD_ENDED")
	void crewPeriodEnded_throws() {
		// Given
		doNothing().when(crewPort).validateMembership(CREW_ID, USER_ID);
		given(crewPort.getCrewVerificationWindowInfo(CREW_ID))
				.willReturn(new CrewVerificationWindowInfo(
						"PHOTO", "ACTIVE",
						TODAY.minusDays(10), TODAY.minusDays(1),
						false, LocalTime.of(23, 59, 59)
				));

		// When & Then
		assertThatThrownBy(() -> createUploadSessionService.createUploadSession(defaultCommand()))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.CREW_PERIOD_ENDED);
	}

	@Test
	@DisplayName("크루 시작 전 → CREW_NOT_STARTED")
	void crewNotStarted_throws() {
		// Given
		doNothing().when(crewPort).validateMembership(CREW_ID, USER_ID);
		given(crewPort.getCrewVerificationWindowInfo(CREW_ID))
				.willReturn(new CrewVerificationWindowInfo(
						"PHOTO", "ACTIVE",
						TODAY.plusDays(1), TODAY.plusDays(10),
						false, LocalTime.of(23, 59, 59)
				));

		// When & Then
		assertThatThrownBy(() -> createUploadSessionService.createUploadSession(defaultCommand()))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.CREW_NOT_STARTED);
	}

	@Test
	@DisplayName("비회원 → CREW_ACCESS_DENIED")
	void nonMember_throws() {
		// Given
		doThrow(new BusinessException(ErrorCode.CREW_ACCESS_DENIED))
				.when(crewPort).validateMembership(CREW_ID, USER_ID);

		// When & Then
		assertThatThrownBy(() -> createUploadSessionService.createUploadSession(defaultCommand()))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.CREW_ACCESS_DENIED);
	}

	@Test
	@DisplayName("TEXT 크루 → UPLOAD_SESSION_NOT_REQUIRED")
	void textCrew_throws() {
		// Given
		doNothing().when(crewPort).validateMembership(CREW_ID, USER_ID);
		given(crewPort.getCrewVerificationWindowInfo(CREW_ID))
				.willReturn(new CrewVerificationWindowInfo(
						"TEXT", "ACTIVE",
						TODAY.minusDays(1), TODAY.plusDays(10),
						false, LocalTime.of(23, 59, 59)
				));

		// When & Then
		assertThatThrownBy(() -> createUploadSessionService.createUploadSession(defaultCommand()))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.UPLOAD_SESSION_NOT_REQUIRED);
	}

	@Test
	@DisplayName("허용되지 않는 파일 타입 → INVALID_FILE_TYPE")
	void invalidFileType_throws() {
		// Given
		stubMembershipAndCrewInfo(activePhotoCrew(LocalTime.of(23, 59, 59)));
		given(challengePort.findActiveByUserIdAndCrewId(USER_ID, CREW_ID))
				.willReturn(Optional.of(activeChallengeWithDeadline(FIXED_NOW.plusHours(1))));
		CreateUploadSessionCommand command = new CreateUploadSessionCommand(
				USER_ID, CREW_ID, null, "doc.pdf", "application/pdf", FILE_SIZE);

		// When & Then
		assertThatThrownBy(() -> createUploadSessionService.createUploadSession(command))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.INVALID_FILE_TYPE);
	}

	@Test
	@DisplayName("파일 크기 초과 → FILE_TOO_LARGE")
	void fileTooLarge_throws() {
		// Given
		stubMembershipAndCrewInfo(activePhotoCrew(LocalTime.of(23, 59, 59)));
		given(challengePort.findActiveByUserIdAndCrewId(USER_ID, CREW_ID))
				.willReturn(Optional.of(activeChallengeWithDeadline(FIXED_NOW.plusHours(1))));
		long oversizedFile = 6 * 1024 * 1024; // 6MB (max 5MB)
		CreateUploadSessionCommand command = new CreateUploadSessionCommand(
				USER_ID, CREW_ID, null, FILE_NAME, FILE_TYPE, oversizedFile);

		// When & Then
		assertThatThrownBy(() -> createUploadSessionService.createUploadSession(command))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.FILE_TOO_LARGE);
	}

	@Test
	@DisplayName("crewId/habitId 둘 다 없음 → INVALID_INPUT(C001, XOR 위반)")
	void neitherCrewIdNorHabitId_throws() {
		// Given
		CreateUploadSessionCommand command = new CreateUploadSessionCommand(
				USER_ID, null, null, FILE_NAME, FILE_TYPE, FILE_SIZE);

		// When & Then
		assertThatThrownBy(() -> createUploadSessionService.createUploadSession(command))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.INVALID_INPUT);
	}

	@Test
	@DisplayName("crewId/habitId 둘 다 존재 → INVALID_INPUT(C001, XOR 위반)")
	void bothCrewIdAndHabitId_throws() {
		// Given
		CreateUploadSessionCommand command = new CreateUploadSessionCommand(
				USER_ID, CREW_ID, HABIT_ID, FILE_NAME, FILE_TYPE, FILE_SIZE);

		// When & Then
		assertThatThrownBy(() -> createUploadSessionService.createUploadSession(command))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.INVALID_INPUT);
	}

	@Test
	@DisplayName("habitId 경로 — HabitPort 위임 후 발급 성공, habit_id만 저장(crew_id는 NULL)")
	void habitIdPath_success_savesHabitIdOnly() {
		// Given
		doNothing().when(habitPort).validateHabitAndDeadline(HABIT_ID, USER_ID);
		stubStorageAndRepository();
		CreateUploadSessionCommand command = new CreateUploadSessionCommand(
				USER_ID, null, HABIT_ID, FILE_NAME, FILE_TYPE, FILE_SIZE);

		// When
		var result = createUploadSessionService.createUploadSession(command);

		// Then
		assertThat(result).isNotNull();
		ArgumentCaptor<UploadSession> captor = ArgumentCaptor.forClass(UploadSession.class);
		verify(uploadSessionRepositoryPort).save(captor.capture());
		assertThat(captor.getValue().getHabitId()).isEqualTo(HABIT_ID);
		assertThat(captor.getValue().getCrewId()).isNull();
		verify(crewPort, never()).validateMembership(any(), any());
	}

	@Test
	@DisplayName("habitId 경로 — HabitPort가 던진 예외가 그대로 전파된다(예: 멈춘 습관 HABIT_NOT_ACTIVE)")
	void habitIdPath_habitPortThrows_propagates() {
		// Given
		doThrow(new BusinessException(ErrorCode.HABIT_NOT_ACTIVE))
				.when(habitPort).validateHabitAndDeadline(HABIT_ID, USER_ID);
		CreateUploadSessionCommand command = new CreateUploadSessionCommand(
				USER_ID, null, HABIT_ID, FILE_NAME, FILE_TYPE, FILE_SIZE);

		// When & Then
		assertThatThrownBy(() -> createUploadSessionService.createUploadSession(command))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.HABIT_NOT_ACTIVE);
	}

	@Test
	@DisplayName("crewId 경로 회귀 — habit_id는 NULL로 저장되고 HabitPort는 호출되지 않는다(G1)")
	void crewIdPath_regression_habitIdNullAndHabitPortNotCalled() {
		// Given
		stubMembershipAndCrewInfo(activePhotoCrew(LocalTime.of(23, 59, 59)));
		given(challengePort.findActiveByUserIdAndCrewId(USER_ID, CREW_ID))
				.willReturn(Optional.empty());
		stubStorageAndRepository();

		// When
		createUploadSessionService.createUploadSession(defaultCommand());

		// Then
		ArgumentCaptor<UploadSession> captor = ArgumentCaptor.forClass(UploadSession.class);
		verify(uploadSessionRepositoryPort).save(captor.capture());
		assertThat(captor.getValue().getCrewId()).isEqualTo(CREW_ID);
		assertThat(captor.getValue().getHabitId()).isNull();
		verify(habitPort, never()).validateHabitAndDeadline(any(), any());
	}

	// ===== T-U8: 슬롯 일일마감 통합(step4 §2) — 발급 경로는 하한(V003) 미적용, 상한만 슬롯 기준으로 통합 =====

	@Test
	@DisplayName("T-U8(a) 당일 인증 완료 후(슬롯=내일) 같은 날 재발급 시도 — 발급은 성공한다(하한 미적용, 비대칭)")
	void slotTomorrow_reissueSameDay_success() {
		// Given — 오늘 이미 완료해 completedDays=1, 슬롯은 내일(TODAY+1)로 넘어간 상태
		stubMembershipAndCrewInfo(activePhotoCrew(LocalTime.of(23, 59, 59)));
		given(challengePort.findActiveByUserIdAndCrewId(USER_ID, CREW_ID))
				.willReturn(Optional.of(activeChallengeWithDeadline(TODAY, TODAY.plusDays(2).atTime(23, 59, 59))));
		stubStorageAndRepository();

		// When
		var result = createUploadSessionService.createUploadSession(defaultCommand());

		// Then — 슬롯(내일) 마감 전이므로 재발급 성공 (V003 하한은 /verifications에만 적용)
		assertThat(result).isNotNull();
	}

	@Test
	@DisplayName("T-U8(b) 마감 21:00 크루 2일차 슬롯, 23:00 발급 시도 — 슬롯 일일마감 초과로 V002 (07-18 건 해소)")
	void slotDailyDeadlineExceeded_issuance_throwsV002() {
		// Given — deadlineTime=21:00, 슬롯=오늘(2일차, startDate=어제+completedDays=1), 발급 시각=오늘 23:00
		CreateUploadSessionService service = serviceAt(TODAY.atTime(23, 0, 0));
		stubMembershipAndCrewInfo(activePhotoCrew(LocalTime.of(21, 0, 0)));
		given(challengePort.findActiveByUserIdAndCrewId(USER_ID, CREW_ID))
				.willReturn(Optional.of(activeChallengeWithDeadline(
						TODAY.minusDays(1), TODAY.plusDays(5).atTime(21, 0, 0))));

		// When & Then — 사이클 마감(TODAY+5)은 넉넉히 남았지만 슬롯 일일마감(TODAY 21:00)을 초과
		assertThatThrownBy(() -> service.createUploadSession(defaultCommand()))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.VERIFICATION_DEADLINE_EXCEEDED);
	}
}
