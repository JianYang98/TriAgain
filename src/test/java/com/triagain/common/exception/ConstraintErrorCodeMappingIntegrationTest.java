package com.triagain.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.triagain.common.response.ApiResponse;
import com.triagain.habit.domain.model.HabitVerification;
import com.triagain.habit.port.out.HabitVerificationRepositoryPort;
import com.triagain.moderation.domain.model.Report;
import com.triagain.moderation.domain.vo.ReportReason;
import com.triagain.moderation.port.out.ReportRepositoryPort;
import com.triagain.verification.domain.model.Verification;
import com.triagain.verification.port.out.VerificationRepositoryPort;

/**
 * 제약 위반 → 에러코드 매핑 실 DB 검증 — M1 (step4 §10-1·§8-3, impl-guards G-6).
 * <p>
 * {@code GlobalExceptionHandler.CONSTRAINT_ERRORS}는 정확 매칭 Map이므로, 실제 Hibernate가 넘기는
 * {@code constraintName}이 마이그레이션 파일의 이름과 대소문자·형태까지 정확히 일치하는지 실DB로 확인해야
 * 의미가 있다. 분기가 틀려도 컴파일·단위테스트는 그린이므로 이 테스트가 유일한 방어선이다.
 * <p>
 * 전용 TestContainers + Flyway ON({@code ddl-auto=validate})으로 실제 partial index를 적용한다.
 * H2({@code test} 프로파일)는 partial index를 재현하지 않으므로 이 검증에 쓸 수 없다
 * ({@code VerificationUniqueConstraintsIntegrationTest}와 동일 이유).
 */
@SpringBootTest
@ActiveProfiles("integration")
@Tag("integration")
@Testcontainers
class ConstraintErrorCodeMappingIntegrationTest {

	@Container
	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
			.withDatabaseName("triagain_constraint_map")
			.withUsername("cmapuser")
			.withPassword("cmappass");

	@DynamicPropertySource
	static void configureProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		registry.add("spring.datasource.username", POSTGRES::getUsername);
		registry.add("spring.datasource.password", POSTGRES::getPassword);
		registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
		registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
	}

	@Autowired
	private VerificationRepositoryPort verificationRepositoryPort;

	@Autowired
	private ReportRepositoryPort reportRepositoryPort;

	@Autowired
	private HabitVerificationRepositoryPort habitVerificationRepositoryPort;

	@Autowired
	private GlobalExceptionHandler globalExceptionHandler;

	private static final String CHALLENGE_ID = "CHAL-cmap";

	@Test
	@DisplayName("M1-1 — uk_verifications_user_crew_date_active 위반 → V003(VERIFICATION_ALREADY_EXISTS)")
	void duplicateActiveSlot_mapsToVerificationAlreadyExists() {
		// Given — 같은 (user, crew, date)에 APPROVED 인증이 이미 존재
		String userId = "cmap-user-1";
		String crewId = "cmap-crew-1";
		LocalDate targetDate = LocalDate.now();
		verificationRepositoryPort.save(
				Verification.createText(CHALLENGE_ID, userId, crewId, "인증", targetDate, 1, 1));

		// When — 같은 슬롯에 두 번째 APPROVED insert 시도 → 실제 제약 위반 유발
		DataIntegrityViolationException thrown = catchDataIntegrityViolation(() ->
				verificationRepositoryPort.save(
						Verification.createText(CHALLENGE_ID, userId, crewId, "인증2", targetDate, 1, 2)));

		// Then — GlobalExceptionHandler가 실제 constraintName으로 V003을 반환하는지 확인
		ResponseEntity<ApiResponse<Void>> response = globalExceptionHandler.handleDataIntegrityViolation(
				thrown, new MockHttpServletRequest("POST", "/verifications"));
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().error()).isNotNull();
		assertThat(response.getBody().error().code()).isEqualTo("V003");
	}

	@Test
	@DisplayName("M1-2 — uk_verifications_upload_session 위반 → V015(UPLOAD_SESSION_ALREADY_USED)")
	void duplicateUploadSession_mapsToUploadSessionAlreadyUsed() {
		// Given — 같은 upload_session_id를 쓰는 PHOTO 인증이 이미 존재 (다른 날짜 — date 제약과 분리)
		String userId = "cmap-user-2";
		String crewId = "cmap-crew-2";
		verificationRepositoryPort.save(Verification.createPhoto(
				CHALLENGE_ID, userId, crewId, 777L, "https://cdn.example.com/1.jpg", null,
				LocalDate.now(), 1, 1));

		// When — 같은 upload_session_id로 다른 날짜에 두 번째 insert 시도
		DataIntegrityViolationException thrown = catchDataIntegrityViolation(() ->
				verificationRepositoryPort.save(Verification.createPhoto(
						CHALLENGE_ID, userId, crewId, 777L, "https://cdn.example.com/2.jpg", null,
						LocalDate.now().minusDays(1), 1, 1)));

		// Then
		ResponseEntity<ApiResponse<Void>> response = globalExceptionHandler.handleDataIntegrityViolation(
				thrown, new MockHttpServletRequest("POST", "/verifications"));
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().error()).isNotNull();
		assertThat(response.getBody().error().code()).isEqualTo("V015");
	}

	@Test
	@DisplayName("M1-3 — uk_reports_verification_reporter 위반 → M002(REPORT_ALREADY_EXISTS)")
	void duplicateReport_mapsToReportAlreadyExists() {
		// Given — 같은 (verification, reporter)로 신고가 이미 존재
		String verificationId = "cmap-verf-1";
		String reporterId = "cmap-reporter-1";
		reportRepositoryPort.save(Report.create(verificationId, reporterId, ReportReason.SPAM, "설명"));

		// When — 같은 (verification, reporter)로 두 번째 신고 insert 시도 → 실제 제약 위반 유발
		DataIntegrityViolationException thrown = catchDataIntegrityViolation(() ->
				reportRepositoryPort.save(
						Report.create(verificationId, reporterId, ReportReason.FAKE, "설명2")));

		// Then — GlobalExceptionHandler가 실제 constraintName으로 M002를 반환하는지 확인
		ResponseEntity<ApiResponse<Void>> response = globalExceptionHandler.handleDataIntegrityViolation(
				thrown, new MockHttpServletRequest("POST", "/reports"));
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().error()).isNotNull();
		assertThat(response.getBody().error().code()).isEqualTo("M002");
	}

	@Test
	@DisplayName("M1-4 — uk_habit_verifications_upload_session 위반 → V015(UPLOAD_SESSION_ALREADY_USED)")
	void duplicateHabitUploadSession_mapsToUploadSessionAlreadyUsed() {
		// Given — 같은 upload_session_id를 쓰는 습관 사진 인증이 이미 존재 (다른 날짜 — habit_date 제약과 분리)
		String habitCycleId = "cmap-cycle-1";
		String habitId = "cmap-habit-1";
		String userId = "cmap-user-3";
		habitVerificationRepositoryPort.save(HabitVerification.createPhoto(
				habitCycleId, habitId, userId, 888L, "https://cdn.example.com/h1.jpg", null,
				LocalDate.now(), 1));

		// When — 같은 upload_session_id로 다른 날짜에 두 번째 insert 시도
		DataIntegrityViolationException thrown = catchDataIntegrityViolation(() ->
				habitVerificationRepositoryPort.save(HabitVerification.createPhoto(
						habitCycleId, habitId, userId, 888L, "https://cdn.example.com/h2.jpg", null,
						LocalDate.now().minusDays(1), 1)));

		// Then
		ResponseEntity<ApiResponse<Void>> response = globalExceptionHandler.handleDataIntegrityViolation(
				thrown, new MockHttpServletRequest("POST", "/habit-verifications"));
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().error()).isNotNull();
		assertThat(response.getBody().error().code()).isEqualTo("V015");
	}

	@Test
	@DisplayName("M1-5 — uk_habit_verifications_habit_date 위반 → HB010(HABIT_VERIFICATION_ALREADY_EXISTS)")
	void duplicateHabitDateSlot_mapsToHabitVerificationAlreadyExists() {
		// Given — 같은 (habit, targetDate)에 텍스트 인증이 이미 존재 (upload_session_id는 둘 다 null — 별개 제약과 분리)
		String habitCycleId = "cmap-cycle-2";
		String habitId = "cmap-habit-2";
		String userId = "cmap-user-4";
		LocalDate targetDate = LocalDate.now();
		habitVerificationRepositoryPort.save(HabitVerification.createText(
				habitCycleId, habitId, userId, "오늘 인증", targetDate, 1));

		// When — 같은 (habit, targetDate)에 두 번째 insert 시도 → 실제 제약 위반 유발
		DataIntegrityViolationException thrown = catchDataIntegrityViolation(() ->
				habitVerificationRepositoryPort.save(HabitVerification.createText(
						habitCycleId, habitId, userId, "오늘 인증2", targetDate, 2)));

		// Then — GlobalExceptionHandler가 실제 constraintName으로 HB010을 반환하는지 확인
		ResponseEntity<ApiResponse<Void>> response = globalExceptionHandler.handleDataIntegrityViolation(
				thrown, new MockHttpServletRequest("POST", "/habit-verifications"));
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().error()).isNotNull();
		assertThat(response.getBody().error().code()).isEqualTo("HB010");
	}

	/** 저장 중 발생하는 DataIntegrityViolationException을 실제로 캐치한다 — 없으면 즉시 실패 */
	private DataIntegrityViolationException catchDataIntegrityViolation(Runnable action) {
		try {
			action.run();
		} catch (DataIntegrityViolationException e) {
			return e;
		}
		throw new AssertionError("DataIntegrityViolationException이 발생하지 않았습니다");
	}
}
