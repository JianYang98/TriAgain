package com.triagain.crew.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDate;
import java.time.LocalTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.triagain.common.exception.BusinessException;
import com.triagain.common.exception.ErrorCode;
import com.triagain.crew.domain.model.Crew;
import com.triagain.crew.domain.vo.CrewCategory;
import com.triagain.crew.domain.vo.CrewVisibility;
import com.triagain.crew.domain.vo.VerificationType;
import com.triagain.crew.port.in.CreateCrewUseCase.CreateCrewCommand;
import com.triagain.crew.port.out.CrewRepositoryPort;

@ExtendWith(MockitoExtension.class)
class CreateCrewServiceTest {

	@Mock
	private CrewRepositoryPort crewRepositoryPort;

	private CreateCrewService createCrewService;

	@BeforeEach
	void setUp() {
		createCrewService = new CreateCrewService(crewRepositoryPort);
		ReflectionTestUtils.setField(createCrewService, "maxDurationDays", 30);
	}

	/** 시작일~종료일 차이가 days인 커맨드 생성 (ChronoUnit.DAYS.between 기준) */
	private CreateCrewCommand commandWithDuration(long days) {
		LocalDate start = LocalDate.now().plusDays(1);
		return new CreateCrewCommand(
				"user-1",
				"테스트 크루",
				"테스트 목표",
				"인증 내용",
				VerificationType.TEXT,
				10,
				start,
				start.plusDays(days),
				true,
				LocalTime.of(23, 59, 59),
				CrewCategory.ETC,
				CrewVisibility.PRIVATE
		);
	}

	@DisplayName("크루 기간이 정확히 7일(days=6)이면 정상 생성된다 — 최소 경계값")
	@Test
	void createCrew_durationExactlyMinimum_succeeds() {
		// Given
		CreateCrewCommand command = commandWithDuration(6);
		given(crewRepositoryPort.save(any(Crew.class))).willAnswer(inv -> inv.getArgument(0));

		// When
		createCrewService.createCrew(command);

		// Then
		verify(crewRepositoryPort).save(any(Crew.class));
	}

	@DisplayName("크루 기간이 6일(days=5)이면 CREW_DURATION_TOO_SHORT 예외가 발생한다 — 최소 미만")
	@Test
	void createCrew_durationBelowMinimum_throwsTooShort() {
		// Given
		CreateCrewCommand command = commandWithDuration(5);

		// When & Then
		assertThatThrownBy(() -> createCrewService.createCrew(command))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.CREW_DURATION_TOO_SHORT);

		verify(crewRepositoryPort, never()).save(any());
	}

	@DisplayName("크루 기간이 1일(days=0)이면 CREW_DURATION_TOO_SHORT 예외가 발생한다")
	@Test
	void createCrew_durationOneDay_throwsTooShort() {
		// Given
		CreateCrewCommand command = commandWithDuration(0);

		// When & Then
		assertThatThrownBy(() -> createCrewService.createCrew(command))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.CREW_DURATION_TOO_SHORT);

		verify(crewRepositoryPort, never()).save(any());
	}

	@DisplayName("크루 기간이 최대값(days=maxDurationDays)이면 정상 생성된다 — 최대 경계값")
	@Test
	void createCrew_durationExactlyMaximum_succeeds() {
		// Given
		CreateCrewCommand command = commandWithDuration(30);
		given(crewRepositoryPort.save(any(Crew.class))).willAnswer(inv -> inv.getArgument(0));

		// When
		createCrewService.createCrew(command);

		// Then
		verify(crewRepositoryPort).save(any(Crew.class));
	}

	@DisplayName("크루 기간이 최대값을 초과하면(days=31) CREW_DURATION_TOO_LONG 예외가 발생한다")
	@Test
	void createCrew_durationAboveMaximum_throwsTooLong() {
		// Given
		CreateCrewCommand command = commandWithDuration(31);

		// When & Then
		assertThatThrownBy(() -> createCrewService.createCrew(command))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.CREW_DURATION_TOO_LONG);

		verify(crewRepositoryPort, never()).save(any());
	}

	@DisplayName("정상 생성 시 크루장이 LEADER 멤버로 함께 저장된다")
	@Test
	void createCrew_success_savesLeaderMember() {
		// Given
		CreateCrewCommand command = commandWithDuration(14);
		given(crewRepositoryPort.save(any(Crew.class))).willAnswer(inv -> inv.getArgument(0));

		// When
		var result = createCrewService.createCrew(command);

		// Then
		verify(crewRepositoryPort).save(any(Crew.class));
		verify(crewRepositoryPort).saveMember(any());
		assertThat(result.creatorId()).isEqualTo("user-1");
		assertThat(result.currentMembers()).isEqualTo(1);
	}
}
