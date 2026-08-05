package com.triagain.verification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.triagain.common.exception.BusinessException;
import com.triagain.common.exception.ErrorCode;
import com.triagain.common.util.IdGenerator;
import com.triagain.crew.domain.model.Challenge;
import com.triagain.crew.domain.model.Crew;
import com.triagain.crew.domain.vo.ChallengeStatus;
import com.triagain.crew.domain.vo.CrewStatus;
import com.triagain.crew.domain.vo.CrewVisibility;
import com.triagain.crew.domain.vo.VerificationType;
import com.triagain.crew.port.out.ChallengeRepositoryPort;
import com.triagain.crew.port.out.CrewRepositoryPort;
import com.triagain.verification.domain.model.UploadSession;
import com.triagain.verification.domain.model.Verification;
import com.triagain.verification.domain.vo.ReviewStatus;
import com.triagain.verification.domain.vo.VerificationStatus;
import com.triagain.verification.port.in.CancelVerificationUseCase;
import com.triagain.verification.port.in.UpdateVerificationUseCase;
import com.triagain.verification.port.in.UpdateVerificationUseCase.UpdateCommand;
import com.triagain.verification.port.in.UpdateVerificationUseCase.UpdateResult;
import com.triagain.verification.port.out.UploadSessionRepositoryPort;
import com.triagain.verification.port.out.VerificationRepositoryPort;

/**
 * 인증 수정(치환) — 실 DB(TestContainers PostgreSQL16 + Flyway) 회귀 테스트.
 * <p>
 * step4 §10-1 P1~P5 — PHOTO 크루 텍스트만 수정(image_url 승계 + upload_session_id NULL, G-10·G-11),
 * challenge 불변(G-12), TEXT 크루 사진 첨부 차단(V017), moderation·CANCELLED 대상 차단을 검증한다.
 * P6 — 사진 교체(uploadSessionId 제공) 시 새 행이 새 세션ID를 보유해 재사용이 막히는지(G-10 정정, Codex 리뷰).
 */
@SpringBootTest
@ActiveProfiles("integration")
@Tag("integration")
@Testcontainers
class UpdateVerificationIntegrationTest {

	@Container
	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
			.withDatabaseName("triagain_update")
			.withUsername("updateuser")
			.withPassword("updatepass");

	@DynamicPropertySource
	static void configureProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		registry.add("spring.datasource.username", POSTGRES::getUsername);
		registry.add("spring.datasource.password", POSTGRES::getPassword);
		registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
		registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
	}

	@Autowired
	private UpdateVerificationUseCase updateVerificationUseCase;

	@Autowired
	private CancelVerificationUseCase cancelVerificationUseCase;

	@Autowired
	private VerificationRepositoryPort verificationRepositoryPort;

	@Autowired
	private UploadSessionRepositoryPort uploadSessionRepositoryPort;

	@Autowired
	private ChallengeRepositoryPort challengeRepositoryPort;

	@Autowired
	private CrewRepositoryPort crewRepositoryPort;

	private void createCrew(String crewId, String leaderId, VerificationType type) {
		Crew crew = Crew.of(
				crewId, leaderId, "수정 테스트 크루", "목표",
				"인증 내용", type, 10, 1, CrewStatus.ACTIVE,
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

	/** COMPLETED 업로드 세션 생성 — 사진 교체 PATCH가 요구하는 사전조건(session.isCompleted()) */
	private UploadSession createCompletedSession(String userId, String crewId, String imageKey) {
		UploadSession pending = UploadSession.create(userId, crewId, imageKey, "image/jpeg");
		UploadSession saved = uploadSessionRepositoryPort.save(pending);
		saved.complete();
		return uploadSessionRepositoryPort.save(saved);
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
	@DisplayName("P1/G-10/G-11: PHOTO 크루에서 세션 없이 텍스트만 수정 — image_url 승계, 새 행 upload_session_id는 NULL")
	void p1_photoCrewTextOnlyEdit_carriesImageUrlWithNullSession() {
		// Given
		String userId = "p1-user";
		String crewId = IdGenerator.generate("CREW");
		createCrew(crewId, userId, VerificationType.PHOTO);
		Challenge challenge = createChallenge(userId, crewId, 1, ChallengeStatus.IN_PROGRESS);
		Verification old = Verification.createPhoto(
				challenge.getId(), userId, crewId, 777L,
				"https://cdn.example.com/photo.jpg", "옛 캡션", LocalDate.now(), 1, 1);
		Verification savedOld = verificationRepositoryPort.save(old);

		// When — uploadSessionId 없이 텍스트만 수정
		UpdateCommand command = new UpdateCommand(savedOld.getId(), userId, null, "새 캡션");
		UpdateResult result = updateVerificationUseCase.updateVerification(command);

		// Then
		assertThat(result.imageUrl()).isEqualTo("https://cdn.example.com/photo.jpg");
		assertThat(result.textContent()).isEqualTo("새 캡션");
		assertThat(result.previousVerificationId()).isEqualTo(savedOld.getId());
		assertThat(result.slotAttempt()).isEqualTo(2);
		assertThat(result.reviewStatus()).isEqualTo(ReviewStatus.NOT_REQUIRED);
		assertThat(result.reportCount()).isZero();

		Verification newVerification = verificationRepositoryPort.findById(result.verificationId()).orElseThrow();
		assertThat(newVerification.getUploadSessionId()).isNull();
		assertThat(newVerification.getImageUrl()).isEqualTo("https://cdn.example.com/photo.jpg");

		Verification oldNow = verificationRepositoryPort.findById(savedOld.getId()).orElseThrow();
		assertThat(oldNow.getStatus()).isEqualTo(VerificationStatus.CANCELLED);
		assertThat(oldNow.getUploadSessionId()).isEqualTo(777L);   // 옛 행은 옛 세션을 계속 참조(§2-4(2))
	}

	@Test
	@DisplayName("P2/G-12: 수정 전후로 challenges.completed_days·status가 불변이다")
	void p2_update_neverMutatesChallenge() {
		// Given
		String userId = "p2-user";
		String crewId = IdGenerator.generate("CREW");
		createCrew(crewId, userId, VerificationType.TEXT);
		Challenge challenge = createChallenge(userId, crewId, 1, ChallengeStatus.IN_PROGRESS);
		Verification old = Verification.createText(
				challenge.getId(), userId, crewId, "옛 텍스트", LocalDate.now(), 1, 1);
		Verification savedOld = verificationRepositoryPort.save(old);

		// When
		updateVerificationUseCase.updateVerification(
				new UpdateCommand(savedOld.getId(), userId, null, "새 텍스트"));

		// Then
		Challenge after = challengeRepositoryPort.findById(challenge.getId()).orElseThrow();
		assertThat(after.getCompletedDays()).isEqualTo(1);
		assertThat(after.getStatus()).isEqualTo(ChallengeStatus.IN_PROGRESS);
	}

	@Test
	@DisplayName("P3: TEXT 크루에 uploadSessionId를 담아 PATCH — 400 V017")
	void p3_textCrewWithSession_throwsV017() {
		// Given
		String userId = "p3-user";
		String crewId = IdGenerator.generate("CREW");
		createCrew(crewId, userId, VerificationType.TEXT);
		Challenge challenge = createChallenge(userId, crewId, 1, ChallengeStatus.IN_PROGRESS);
		Verification old = Verification.createText(
				challenge.getId(), userId, crewId, "옛 텍스트", LocalDate.now(), 1, 1);
		Verification savedOld = verificationRepositoryPort.save(old);

		// When & Then
		assertThatThrownBy(() -> updateVerificationUseCase.updateVerification(
				new UpdateCommand(savedOld.getId(), userId, 123L, null)))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.UPLOAD_SESSION_NOT_REQUIRED);

		// 옛 행은 여전히 APPROVED — 가드에서 막혔으므로 아무 변화도 없어야 한다
		assertThat(verificationRepositoryPort.findById(savedOld.getId()).orElseThrow().getStatus())
				.isEqualTo(VerificationStatus.APPROVED);
	}

	@Test
	@DisplayName("P4: REPORTED 대상에 DELETE·PATCH — 양쪽 다 409 V023")
	void p4_reportedTarget_bothCancelAndUpdate_throwV023() {
		// Given
		String userId = "p4-user";
		String crewId = IdGenerator.generate("CREW");
		createCrew(crewId, userId, VerificationType.TEXT);
		Challenge challenge = createChallenge(userId, crewId, 1, ChallengeStatus.IN_PROGRESS);
		// 신고·검토 중(REPORTED) 상태 픽스처를 직접 구성 — 테스트 전용 셋업(운영 경로는 신고 3건 누적으로 전이)
		Verification reportedVerification = Verification.of(
				IdGenerator.generate("VRFY"), challenge.getId(), userId, crewId, null, null, "신고당할 인증",
				VerificationStatus.REPORTED, 1, LocalDate.now(), 1, 1,
				ReviewStatus.PENDING, LocalDateTime.now());
		Verification saved = verificationRepositoryPort.save(reportedVerification);

		// When & Then — DELETE
		assertThatThrownBy(() -> cancelVerificationUseCase.cancelVerification(saved.getId(), userId))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.VERIFICATION_UNDER_MODERATION);

		// When & Then — PATCH
		assertThatThrownBy(() -> updateVerificationUseCase.updateVerification(
				new UpdateCommand(saved.getId(), userId, null, "수정 시도")))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.VERIFICATION_UNDER_MODERATION);
	}

	@Test
	@DisplayName("P5: CANCELLED 대상에 PATCH — 409 V022 (DELETE의 200 멱등과 대비, 모순1의 반대편)")
	void p5_cancelledTarget_patch_throwsV022() {
		// Given
		String userId = "p5-user";
		String crewId = IdGenerator.generate("CREW");
		createCrew(crewId, userId, VerificationType.TEXT);
		Challenge challenge = createChallenge(userId, crewId, 1, ChallengeStatus.IN_PROGRESS);
		Verification old = Verification.createText(
				challenge.getId(), userId, crewId, "취소될 인증", LocalDate.now(), 1, 1);
		Verification saved = verificationRepositoryPort.save(old);

		// 먼저 취소 — 200
		cancelVerificationUseCase.cancelVerification(saved.getId(), userId);

		// When & Then — 그 뒤 PATCH는 409 V022
		assertThatThrownBy(() -> updateVerificationUseCase.updateVerification(
				new UpdateCommand(saved.getId(), userId, null, "수정 시도")))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.VERIFICATION_NOT_ACTIVE);
	}

	@Test
	@DisplayName("P6/결함1 회귀(G-10 정정): 사진 교체 시 새 행이 새 세션ID를 보유하고, 그 세션 재사용 시도는 제약 위반으로 막힌다")
	void p6_photoReplace_newRowHoldsSessionId_reuseIsBlocked() {
		// Given — PHOTO 크루 + 옛 세션(111L)으로 만들어진 기존 인증
		String userId = "p6-user";
		String crewId = IdGenerator.generate("CREW");
		createCrew(crewId, userId, VerificationType.PHOTO);
		Challenge challenge = createChallenge(userId, crewId, 1, ChallengeStatus.IN_PROGRESS);
		Verification old = Verification.createPhoto(
				challenge.getId(), userId, crewId, 111L,
				"https://cdn.example.com/old.jpg", "옛 캡션", LocalDate.now(), 1, 1);
		Verification savedOld = verificationRepositoryPort.save(old);

		UploadSession newSession = createCompletedSession(userId, crewId, "uploads/" + userId + "/p6-new.jpg");

		// When — 새 세션으로 사진 교체
		UpdateResult result = updateVerificationUseCase.updateVerification(
				new UpdateCommand(savedOld.getId(), userId, newSession.getId(), null));

		// Then — 새 행이 새 세션ID를 보유해야 한다 (교체 전 버그: NULL로 저장되어 세션이 해방됨)
		Verification newVerification = verificationRepositoryPort.findById(result.verificationId()).orElseThrow();
		assertThat(newVerification.getUploadSessionId()).isEqualTo(newSession.getId());

		// 옛 행은 옛 세션(111L)을 그대로 보유 — 서로 다른 세션값이라 유니크 충돌 없음
		Verification oldNow = verificationRepositoryPort.findById(savedOld.getId()).orElseThrow();
		assertThat(oldNow.getStatus()).isEqualTo(VerificationStatus.CANCELLED);
		assertThat(oldNow.getUploadSessionId()).isEqualTo(111L);

		// When & Then — 같은 새 세션(newSession)을 다시 PATCH에 재사용하면 uk_verifications_upload_session 위반
		// (수정 전 버그였다면 새 행의 upload_session_id가 NULL이라 이 재사용이 조용히 성공했을 것)
		assertThatThrownBy(() -> updateVerificationUseCase.updateVerification(
				new UpdateCommand(result.verificationId(), userId, newSession.getId(), null)))
				.isInstanceOf(DataIntegrityViolationException.class);
	}
}
