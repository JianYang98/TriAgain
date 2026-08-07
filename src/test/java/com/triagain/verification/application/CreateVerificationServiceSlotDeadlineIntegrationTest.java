package com.triagain.verification.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.triagain.common.exception.BusinessException;
import com.triagain.common.exception.ErrorCode;
import com.triagain.common.util.IdGenerator;
import com.triagain.crew.domain.model.Challenge;
import com.triagain.crew.domain.model.Crew;
import com.triagain.crew.domain.model.CrewMember;
import com.triagain.crew.domain.vo.ChallengeStatus;
import com.triagain.crew.domain.vo.CrewStatus;
import com.triagain.crew.domain.vo.CrewVisibility;
import com.triagain.crew.domain.vo.VerificationType;
import com.triagain.e2e.E2eTestBase;
import com.triagain.verification.port.in.CreateVerificationUseCase;
import com.triagain.verification.port.in.CreateVerificationUseCase.CreateVerificationCommand;

/**
 * T-I2: endDate 캡 사이클의 min(슬롯 일일마감, 사이클 마감) 정합 테스트 — 실제 PostgreSQL(Testcontainers) +
 * 실제 {@link CreateVerificationService} 빈 사용 (mock 금지).
 * <p>
 * 목적: crew.endDate=crew.startDate인 1일 캡 사이클(challenge.deadline이 1일차 마감으로 고정)에서 2일차
 * 슬롯을 제출하면, 슬롯 자체의 일일마감(아직 안 지남)이 아니라 캡된 challenge.deadline(이미 지남) 기준으로
 * VERIFICATION_DEADLINE_EXCEEDED(V002)가 발생하는지 실 DB 데이터로 검증한다 (step1 §3-2 endDate 캡 보존).
 * <p>
 * 자정 경계 플레이키니스 회피: deadlineTime을 자정과 먼 정오(12:00)로 두어, "어제 정오+5분"이 항상
 * "오늘의 어떤 시각"보다 앞서도록 설계했다 — 테스트 실행 시각에 관계없이 결정적으로 통과한다.
 */
@Tag("e2e")
class CreateVerificationServiceSlotDeadlineIntegrationTest extends E2eTestBase {

	@Autowired
	private CreateVerificationUseCase createVerificationUseCase;

	@Test
	@DisplayName("endDate=startDate 캡 사이클(1일차 마감으로 고정)에서 2일차 슬롯 제출 — 캡된 사이클 마감 초과로 V002")
	void cappedCycleDeadline_slotWithinOwnDailyDeadline_stillThrowsV002() {
		// Given — 1일 캡 크루(startDate=endDate=어제), deadlineTime=정오(자정 인접 플레이키니스 회피)
		String userId = "integ-cap-user-01";
		createUser(userId);

		LocalDate yesterday = LocalDate.now().minusDays(1);
		LocalTime deadlineTime = LocalTime.of(12, 0, 0);
		String crewId = IdGenerator.generate("CREW");
		Crew crew = Crew.of(
				crewId, userId, "캡 테스트 크루", "목표",
				"인증 내용", VerificationType.TEXT, 10, 1, CrewStatus.ACTIVE,
				yesterday, yesterday, true,
				Crew.generateInviteCode(), LocalDateTime.now(),
				deadlineTime, null, CrewVisibility.PRIVATE, 0L, List.of()
		);
		crewRepositoryPort.save(crew);
		crewRepositoryPort.saveMember(CrewMember.createLeader(userId, crewId));

		// Given — 캡된 챌린지: deadline=어제(1일차) 정오로 고정, completedDays=1(2일차 슬롯=오늘)
		Challenge challenge = Challenge.of(
				IdGenerator.generate("CHAL"), userId, crewId, 1,
				3, 1, ChallengeStatus.IN_PROGRESS,
				yesterday, yesterday.atTime(deadlineTime), LocalDateTime.now());
		Challenge saved = challengeRepositoryPort.save(challenge);

		CreateVerificationCommand command = new CreateVerificationCommand(
				userId, saved.getId(), crewId, null, "텍스트 인증");

		// When & Then — 슬롯(오늘) 자체 일일마감(오늘 정오)은 아직 안 지났을 수 있지만,
		// min(슬롯 일일마감, challenge.deadline=어제 정오)이 challenge.deadline을 택해 이미 초과 상태다
		assertThatThrownBy(() -> createVerificationUseCase.createVerification(command))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.VERIFICATION_DEADLINE_EXCEEDED);
	}
}
