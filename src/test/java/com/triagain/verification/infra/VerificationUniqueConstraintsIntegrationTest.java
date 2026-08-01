package com.triagain.verification.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.triagain.common.util.IdGenerator;
import com.triagain.verification.domain.model.Verification;
import com.triagain.verification.domain.vo.ReviewStatus;
import com.triagain.verification.domain.vo.VerificationStatus;
import com.triagain.verification.port.out.VerificationRepositoryPort;

/**
 * V25 partial unique 제약(uk_verifications_user_crew_date_active) + 기존 uk_verifications_upload_session
 * 실 DB 검증 — impl-guards G-5.
 * <p>
 * 전용 TestContainers + Flyway ON({@code ddl-auto=validate})으로 실제 partial index를 적용해 검증한다.
 * H2({@code test} 프로파일, create-drop)는 partial index를 재현하지 않으므로 H2 그린을 제약 검증 완료로
 * 오인하면 안 된다 — {@code HabitUniqueConstraintsIntegrationTest}와 동일 패턴.
 */
@SpringBootTest
@ActiveProfiles("integration")
@Tag("integration")
@Testcontainers
class VerificationUniqueConstraintsIntegrationTest {

	@Container
	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
			.withDatabaseName("triagain_verification_uk")
			.withUsername("vukuser")
			.withPassword("vukpass");

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

	private static final String CHALLENGE_ID = "CHAL-uk";

	// 🔴 테스트마다 독립된 (user, crew)를 써야 한다 — 컨테이너는 클래스 전체가 공유(static @Container)하고
	// 테스트 간 DB 정리(TRUNCATE)가 없으므로, 같은 (user, crew, date)를 재사용하면 이전 테스트가 커밋한 행과
	// 충돌해 의도치 않은 제약 위반이 난다(HabitUniqueConstraintsIntegrationTest의 "테스트마다 새 habitId" 원칙과 동일).
	private String userId(String suffix) {
		return "uk-user-" + suffix;
	}

	private String crewId(String suffix) {
		return "uk-crew-" + suffix;
	}

	private Verification approved(String userId, String crewId, LocalDate targetDate, int slotAttempt) {
		return Verification.createText(CHALLENGE_ID, userId, crewId, "인증", targetDate, 1, slotAttempt);
	}

	private Verification cancelled(String userId, String crewId, LocalDate targetDate, int slotAttempt) {
		return Verification.of(
				IdGenerator.generate("VRFY"), CHALLENGE_ID, userId, crewId, null, null, "취소된 인증",
				VerificationStatus.CANCELLED, 0, targetDate, 1, slotAttempt,
				ReviewStatus.NOT_REQUIRED, LocalDateTime.now());
	}

	@Test
	@DisplayName("U1 — 같은 (user, crew, date)에 APPROVED 2건 insert 시 제약 위반")
	void duplicateApprovedSameSlot_violatesUniqueConstraint() {
		// Given
		String userId = userId("u1");
		String crewId = crewId("u1");
		LocalDate targetDate = LocalDate.now();
		verificationRepositoryPort.save(approved(userId, crewId, targetDate, 1));

		// When & Then
		assertThatThrownBy(() -> verificationRepositoryPort.save(approved(userId, crewId, targetDate, 2)))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("U2 — APPROVED 1건 + CANCELLED 2건 공존 — 정상 통과(partial unique의 존재 이유)")
	void approvedWithMultipleCancelled_coexistsWithoutViolation() {
		// Given & When
		String userId = userId("u2");
		String crewId = crewId("u2");
		LocalDate targetDate = LocalDate.now();
		verificationRepositoryPort.save(cancelled(userId, crewId, targetDate, 1));
		verificationRepositoryPort.save(cancelled(userId, crewId, targetDate, 2));
		Verification saved = verificationRepositoryPort.save(approved(userId, crewId, targetDate, 3));

		// Then
		assertThat(saved.getId()).isNotNull();
		assertThat(verificationRepositoryPort.findMaxSlotAttempt(userId, crewId, targetDate)).isEqualTo(3);
	}

	@Test
	@Transactional   // cancelIfApproved(@Modifying 네이티브 UPDATE)는 활성 트랜잭션이 필요하다
	@DisplayName("U3 — APPROVED 1건을 CANCELLED로 바꾼 뒤 새 APPROVED insert — 정상 통과(재인증·치환 경로)")
	void approvedCancelledThenNewApproved_succeeds() {
		// Given
		String userId = userId("u3");
		String crewId = crewId("u3");
		LocalDate targetDate = LocalDate.now();
		Verification first = verificationRepositoryPort.save(approved(userId, crewId, targetDate, 1));
		int affected = verificationRepositoryPort.cancelIfApproved(first.getId());
		assertThat(affected).isEqualTo(1);

		// When & Then — CANCELLED로 바뀐 슬롯에 새 APPROVED가 정상 insert된다
		Verification second = verificationRepositoryPort.save(approved(userId, crewId, targetDate, 2));
		assertThat(second.getId()).isNotNull();
		assertThat(verificationRepositoryPort.existsActiveByUserIdAndCrewIdAndTargetDate(userId, crewId, targetDate))
				.isTrue();
	}

	@Test
	@DisplayName("U4 — upload_session_id가 같은 행 2건(둘 다 non-NULL) insert 시 제약 위반(기존 제약 유지 확인)")
	void duplicateUploadSessionId_violatesUniqueConstraint() {
		// Given
		String userId = userId("u4");
		String crewId = crewId("u4");
		Verification first = Verification.createPhoto(
				CHALLENGE_ID, userId, crewId, 555L, "https://cdn.example.com/1.jpg", null,
				LocalDate.now(), 1, 1);
		verificationRepositoryPort.save(first);

		// When & Then — 같은 upload_session_id(555L)로 다른 날짜에 insert해도 위반
		Verification second = Verification.createPhoto(
				CHALLENGE_ID, userId, crewId, 555L, "https://cdn.example.com/2.jpg", null,
				LocalDate.now().minusDays(1), 1, 1);
		assertThatThrownBy(() -> verificationRepositoryPort.save(second))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("U5 — upload_session_id = NULL 행 다수 — 정상 통과(NULL 승계 설계의 전제)")
	void nullUploadSessionId_allowsMultipleRows() {
		// Given & When — TEXT 인증(uploadSessionId 항상 NULL) 여러 건을 서로 다른 날짜로 저장
		String userId = userId("u5");
		String crewId = crewId("u5");
		Verification text1 = verificationRepositoryPort.save(approved(userId, crewId, LocalDate.now().minusDays(1), 1));
		Verification text2 = verificationRepositoryPort.save(approved(userId, crewId, LocalDate.now().minusDays(2), 1));

		// Then
		assertThat(text1.getUploadSessionId()).isNull();
		assertThat(text2.getUploadSessionId()).isNull();
		assertThat(verificationRepositoryPort.findById(text1.getId())).isPresent();
		assertThat(verificationRepositoryPort.findById(text2.getId())).isPresent();
	}
}
