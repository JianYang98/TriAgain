package com.triagain.verification.application;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

import com.triagain.common.util.IdGenerator;
import com.triagain.crew.domain.model.Challenge;
import com.triagain.crew.domain.model.Crew;
import com.triagain.crew.domain.vo.ChallengeStatus;
import com.triagain.crew.domain.vo.CrewStatus;
import com.triagain.crew.domain.vo.CrewVisibility;
import com.triagain.crew.domain.vo.VerificationType;
import com.triagain.crew.port.out.ChallengeRepositoryPort;
import com.triagain.crew.port.out.CrewRepositoryPort;
import com.triagain.verification.domain.model.Verification;
import com.triagain.verification.domain.vo.VerificationStatus;
import com.triagain.verification.port.in.CancelVerificationUseCase;
import com.triagain.verification.port.in.CancelVerificationUseCase.CancelResult;
import com.triagain.verification.port.out.VerificationRepositoryPort;

/**
 * 인증 취소 — 실 DB(TestContainers PostgreSQL16 + Flyway) 회귀 테스트.
 * <p>
 * step4 §10-1 C1~C6 — 3일차 취소(SUCCESS→IN_PROGRESS), 동시/순차 더블탭 멱등, D14-a 스케줄러 회귀,
 * 1일차 취소(0 하한)를 실제 조건부 UPDATE(challenges/verifications)로 검증한다.
 */
@SpringBootTest
@ActiveProfiles("integration")
@Tag("integration")
@Testcontainers
class CancelVerificationIntegrationTest {

	@Container
	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
			.withDatabaseName("triagain_cancel")
			.withUsername("canceluser")
			.withPassword("cancelpass");

	@DynamicPropertySource
	static void configureProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		registry.add("spring.datasource.username", POSTGRES::getUsername);
		registry.add("spring.datasource.password", POSTGRES::getPassword);
		registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
		registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
	}

	@Autowired
	private CancelVerificationUseCase cancelVerificationUseCase;

	@Autowired
	private VerificationRepositoryPort verificationRepositoryPort;

	@Autowired
	private ChallengeRepositoryPort challengeRepositoryPort;

	@Autowired
	private CrewRepositoryPort crewRepositoryPort;

	private void createActiveCrew(String crewId, String leaderId) {
		Crew crew = Crew.of(
				crewId, leaderId, "취소 테스트 크루", "목표",
				"인증 내용", VerificationType.TEXT, 10, 1, CrewStatus.ACTIVE,
				LocalDate.now().minusDays(10), LocalDate.now().plusDays(30), true,
				inviteCode(), LocalDateTime.now(),
				LocalTime.of(23, 59, 59), null, CrewVisibility.PRIVATE, 0L, List.of());
		crewRepositoryPort.save(crew);
	}

	private Challenge createChallenge(String userId, String crewId, int completedDays, ChallengeStatus status) {
		Challenge challenge = Challenge.of(
				IdGenerator.generate("CHAL"), userId, crewId, 1,
				3, completedDays, status,
				LocalDate.now().minusDays(2), LocalDateTime.now().plusDays(5), LocalDateTime.now());
		return challengeRepositoryPort.save(challenge);
	}

	private Verification createApprovedVerification(String challengeId, String userId, String crewId,
			int attemptNumber, int slotAttempt) {
		Verification v = Verification.createText(
				challengeId, userId, crewId, "인증 완료", LocalDate.now(), attemptNumber, slotAttempt);
		return verificationRepositoryPort.save(v);
	}

	/** crews.invite_code는 VARCHAR(6) — 6자 랜덤 코드 생성(E2eTestBase.generateInviteCode와 동일 패턴) */
	private String inviteCode() {
		String chars = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
		StringBuilder code = new StringBuilder(6);
		for (int i = 0; i < 6; i++) {
			code.append(chars.charAt((int) (Math.random() * chars.length())));
		}
		return code.toString();
	}

	@Test
	@DisplayName("C1: 3일차 취소 — SUCCESS(3) → IN_PROGRESS(2), completed_days 3→2")
	void c1_thirdDayCancel_revertsSuccessToInProgress() {
		// Given
		String userId = "c1-user";
		String crewId = IdGenerator.generate("CREW");
		createActiveCrew(crewId, userId);
		Challenge challenge = createChallenge(userId, crewId, 3, ChallengeStatus.SUCCESS);
		Verification v = createApprovedVerification(challenge.getId(), userId, crewId, 3, 1);

		// When
		CancelResult result = cancelVerificationUseCase.cancelVerification(v.getId(), userId);

		// Then
		assertThat(result.status()).isEqualTo(VerificationStatus.CANCELLED);
		assertThat(result.challengeProgress().completedDays()).isEqualTo(2);
		assertThat(result.challengeProgress().status()).isEqualTo("IN_PROGRESS");

		Verification savedV = verificationRepositoryPort.findById(v.getId()).orElseThrow();
		assertThat(savedV.getStatus()).isEqualTo(VerificationStatus.CANCELLED);

		Challenge savedChallenge = challengeRepositoryPort.findById(challenge.getId()).orElseThrow();
		assertThat(savedChallenge.getStatus()).isEqualTo(ChallengeStatus.IN_PROGRESS);
		assertThat(savedChallenge.getCompletedDays()).isEqualTo(2);
	}

	@Test
	@DisplayName("C2: 동시 DELETE 2회 — completed_days는 정확히 1회만 감소한다 (더블탭 lost update 방지)")
	void c2_concurrentDoubleTap_decrementsExactlyOnce() throws Exception {
		// Given
		String userId = "c2-user";
		String crewId = IdGenerator.generate("CREW");
		createActiveCrew(crewId, userId);
		Challenge challenge = createChallenge(userId, crewId, 2, ChallengeStatus.IN_PROGRESS);
		Verification v = createApprovedVerification(challenge.getId(), userId, crewId, 2, 1);

		ExecutorService executor = Executors.newFixedThreadPool(2);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);

		Callable<CancelResult> task = () -> {
			ready.countDown();
			start.await();
			return cancelVerificationUseCase.cancelVerification(v.getId(), userId);
		};

		// When — 두 스레드가 거의 동시에 취소 요청
		Future<CancelResult> f1 = executor.submit(task);
		Future<CancelResult> f2 = executor.submit(task);
		ready.await();
		start.countDown();

		CancelResult r1 = f1.get(10, TimeUnit.SECONDS);
		CancelResult r2 = f2.get(10, TimeUnit.SECONDS);
		executor.shutdown();

		// Then — 둘 다 예외 없이 CANCELLED 응답을 받지만, DB의 completed_days는 정확히 1만큼만 감소
		assertThat(r1.status()).isEqualTo(VerificationStatus.CANCELLED);
		assertThat(r2.status()).isEqualTo(VerificationStatus.CANCELLED);

		Challenge updated = challengeRepositoryPort.findById(challenge.getId()).orElseThrow();
		assertThat(updated.getCompletedDays())
				.as("동시 더블탭이어도 completed_days는 2에서 정확히 1만 줄어든 1이어야 한다(이중 감산 금지)")
				.isEqualTo(1);
	}

	@Test
	@DisplayName("C3: 취소 → 재인증 → 다시 취소 — slot_attempt가 1→2로 증가하며 둘 다 CANCELLED로 공존한다")
	void c3_cancelThenReverifyThenCancelAgain_slotAttemptIncrements() {
		// Given
		String userId = "c3-user";
		String crewId = IdGenerator.generate("CREW");
		createActiveCrew(crewId, userId);
		Challenge challenge = createChallenge(userId, crewId, 0, ChallengeStatus.IN_PROGRESS);
		Verification v1 = createApprovedVerification(challenge.getId(), userId, crewId, 1, 1);

		// When — 1차 취소
		cancelVerificationUseCase.cancelVerification(v1.getId(), userId);

		int nextSlotAttempt = verificationRepositoryPort.findMaxSlotAttempt(userId, crewId, LocalDate.now()) + 1;
		assertThat(nextSlotAttempt).isEqualTo(2);

		// 재인증 (slot_attempt=2)
		Verification v2 = createApprovedVerification(challenge.getId(), userId, crewId, 1, nextSlotAttempt);

		// 2차 취소
		CancelResult result = cancelVerificationUseCase.cancelVerification(v2.getId(), userId);

		// Then
		assertThat(result.status()).isEqualTo(VerificationStatus.CANCELLED);
		assertThat(result.slotAttempt()).isEqualTo(2);
		assertThat(verificationRepositoryPort.findById(v1.getId()).orElseThrow().getStatus())
				.isEqualTo(VerificationStatus.CANCELLED);
		assertThat(verificationRepositoryPort.findById(v2.getId()).orElseThrow().getStatus())
				.isEqualTo(VerificationStatus.CANCELLED);
		assertThat(verificationRepositoryPort.findMaxSlotAttempt(userId, crewId, LocalDate.now())).isEqualTo(2);
	}

	@Test
	@Transactional   // cancelIfApproved(@Modifying 네이티브 UPDATE) 직접 호출은 활성 트랜잭션이 필요하다
	@DisplayName("C4: 취소 후 재인증 없이 마감 경과 — 스케줄러가 CANCELLED 행에 막히지 않고 정상 FAILED 대상에 포함한다 (D14-a 회귀)")
	void c4_cancelledSlotDoesNotBlockScheduler() {
		// Given — 마감을 훌쩍 넘긴 과거 슬롯의 챌린지, 그 슬롯에 CANCELLED 인증만 존재(재인증 없음)
		String userId = "c4-user";
		String crewId = IdGenerator.generate("CREW");
		createActiveCrew(crewId, userId);

		LocalDate slot = LocalDate.now().minusDays(10);
		LocalDateTime expiredDeadline = slot.plusDays(3).atTime(23, 59, 59);
		Challenge challenge = Challenge.of(
				IdGenerator.generate("CHAL"), userId, crewId, 1,
				3, 0, ChallengeStatus.IN_PROGRESS, slot, expiredDeadline, LocalDateTime.now());
		challengeRepositoryPort.save(challenge);

		Verification v = Verification.createText(challenge.getId(), userId, crewId, "취소될 인증", slot, 1, 1);
		Verification saved = verificationRepositoryPort.save(v);
		int affected = verificationRepositoryPort.cancelIfApproved(saved.getId());
		assertThat(affected).isEqualTo(1);
		assertThat(verificationRepositoryPort.findById(saved.getId()).orElseThrow().getStatus())
				.isEqualTo(VerificationStatus.CANCELLED);

		// When — 만료 스케줄러의 NOT EXISTS 쿼리 직접 호출
		List<Challenge> expired = challengeRepositoryPort.findExpiredWithoutVerification();

		// Then — CANCELLED 행이 슬롯을 점유한 것처럼 보이면 안 된다. 챌린지가 만료 목록에 포함되어야 정상(G-2)
		assertThat(expired.stream().map(Challenge::getId))
				.as("CANCELLED 인증만 존재하는 슬롯은 '미인증'으로 취급되어 만료 목록에 포함되어야 한다")
				.contains(challenge.getId());
	}

	@Test
	@DisplayName("C5: 1일차 취소 — completed_days 1→0, challenge는 IN_PROGRESS로 유지(0 하한, C1과 다른 경계)")
	void c5_firstDayCancel_completedDaysFloorsAtZero() {
		// Given
		String userId = "c5-user";
		String crewId = IdGenerator.generate("CREW");
		createActiveCrew(crewId, userId);
		Challenge challenge = createChallenge(userId, crewId, 1, ChallengeStatus.IN_PROGRESS);
		Verification v = createApprovedVerification(challenge.getId(), userId, crewId, 1, 1);

		// When
		CancelResult result = cancelVerificationUseCase.cancelVerification(v.getId(), userId);

		// Then
		assertThat(result.challengeProgress().completedDays()).isZero();
		assertThat(result.challengeProgress().status()).isEqualTo("IN_PROGRESS");

		Challenge updated = challengeRepositoryPort.findById(challenge.getId()).orElseThrow();
		assertThat(updated.getCompletedDays()).isZero();
		assertThat(updated.getStatus()).isEqualTo(ChallengeStatus.IN_PROGRESS);
	}

	@Test
	@DisplayName("C6: 이미 CANCELLED인 대상에 순차 DELETE 재요청 — 둘 다 200 멱등, completed_days 불변(모순1 회귀)")
	void c6_sequentialDoubleDelete_bothReturn200Idempotently() {
		// Given
		String userId = "c6-user";
		String crewId = IdGenerator.generate("CREW");
		createActiveCrew(crewId, userId);
		Challenge challenge = createChallenge(userId, crewId, 1, ChallengeStatus.IN_PROGRESS);
		Verification v = createApprovedVerification(challenge.getId(), userId, crewId, 1, 1);

		// When — 순차 2회 호출
		CancelResult first = cancelVerificationUseCase.cancelVerification(v.getId(), userId);
		CancelResult second = cancelVerificationUseCase.cancelVerification(v.getId(), userId);

		// Then — 🔴 두 번째 요청도 예외(V022) 없이 200으로 CANCELLED를 반환해야 한다 (G-1)
		assertThat(first.status()).isEqualTo(VerificationStatus.CANCELLED);
		assertThat(second.status()).isEqualTo(VerificationStatus.CANCELLED);

		Challenge updated = challengeRepositoryPort.findById(challenge.getId()).orElseThrow();
		assertThat(updated.getCompletedDays())
				.as("두 번째 취소 요청은 challenge를 다시 감산하면 안 된다")
				.isZero();
	}
}
