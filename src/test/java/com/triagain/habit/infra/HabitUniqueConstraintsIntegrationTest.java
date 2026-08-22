package com.triagain.habit.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import org.junit.jupiter.api.BeforeEach;
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

import com.triagain.habit.domain.model.Habit;
import com.triagain.habit.domain.model.HabitCycle;
import com.triagain.habit.domain.model.HabitVerification;
import com.triagain.habit.domain.vo.HabitCycleStatus;
import com.triagain.habit.domain.vo.HabitVerificationType;
import com.triagain.habit.port.out.HabitCycleRepositoryPort;
import com.triagain.habit.port.out.HabitRepositoryPort;
import com.triagain.habit.port.out.HabitVerificationRepositoryPort;

/**
 * V23 partial unique 제약(uk_habit_cycles_in_progress·uk_habit_verifications_habit_date·
 * uk_habit_verifications_upload_session) 실 DB 검증 — G4.
 * <p>
 * 전용 TestContainers + Flyway ON({@code ddl-auto=validate})으로 V23 마이그레이션의 실제 partial index를
 * 적용해 검증한다. H2({@code test} 프로파일, create-drop)는 partial index를 재현하지 않으므로
 * H2 그린을 제약 검증 완료로 오인하면 안 된다(impl-guards G4). {@code NotificationTypeCheckConstraintIntegrationTest}(T8)
 * 패턴을 답습 — 전용 컨테이너로 공유 CucumberSpringContext(create-drop)와 충돌 회피.
 */
@SpringBootTest
@ActiveProfiles("integration")
@Tag("integration")
@Testcontainers
class HabitUniqueConstraintsIntegrationTest {

	@Container
	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
			.withDatabaseName("triagain_habit_uk")
			.withUsername("habituser")
			.withPassword("habitpass");

	@DynamicPropertySource
	static void configureProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		registry.add("spring.datasource.username", POSTGRES::getUsername);
		registry.add("spring.datasource.password", POSTGRES::getPassword);
		registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
		registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
	}

	@Autowired
	private HabitRepositoryPort habitRepositoryPort;

	@Autowired
	private HabitCycleRepositoryPort habitCycleRepositoryPort;

	@Autowired
	private HabitVerificationRepositoryPort habitVerificationRepositoryPort;

	private String habitId;

	@BeforeEach
	void setUp() {
		Habit habit = Habit.create("uk-test-user", "매일 물 2L", HabitVerificationType.TEXT, LocalTime.of(23, 59, 59),
				null);
		habitId = habitRepositoryPort.save(habit).getId();
	}

	@Test
	@DisplayName("uk_habit_cycles_in_progress — 같은 habit_id로 IN_PROGRESS 사이클 2건 insert 시 제약 위반")
	void duplicateInProgressCycle_violatesUniqueConstraint() {
		// Given
		HabitCycle first = HabitCycle.start(habitId, "uk-test-user", 1, LocalDate.now(),
				LocalDateTime.now().plusDays(3));
		habitCycleRepositoryPort.save(first);

		// When & Then — 같은 habit에 대해 2번째 IN_PROGRESS insert는 partial unique 위반
		HabitCycle second = HabitCycle.start(habitId, "uk-test-user", 2, LocalDate.now(),
				LocalDateTime.now().plusDays(3));
		assertThatThrownBy(() -> habitCycleRepositoryPort.save(second))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("SUCCESS/FAILED 사이클은 같은 habit_id로 여러 건 누적 insert가 통과한다(partial 조건 확인)")
	void terminalCycles_accumulateWithoutViolation() {
		// Given & When
		for (int i = 1; i <= 3; i++) {
			HabitCycle cycle = HabitCycle.of("HCYC-uk-" + i, habitId, "uk-test-user", i, 3, 3,
					HabitCycleStatus.SUCCESS, LocalDate.now().minusDays(i * 4L),
					LocalDateTime.now().minusDays(i * 4L), LocalDateTime.now());
			habitCycleRepositoryPort.save(cycle);
		}

		// Then
		assertThat(habitCycleRepositoryPort.countSuccessByHabitId(habitId)).isEqualTo(3);
	}

	@Test
	@DisplayName("uk_habit_verifications_habit_date — 같은 (habit_id, target_date) 인증 2건 insert 시 제약 위반")
	void duplicateHabitDateVerification_violatesUniqueConstraint() {
		// Given
		LocalDate targetDate = LocalDate.now();
		HabitVerification first = HabitVerification.createText(
				"HCYC-uk", habitId, "uk-test-user", "오늘도 물 2L", targetDate, 1);
		habitVerificationRepositoryPort.save(first);

		// When & Then
		HabitVerification second = HabitVerification.createText(
				"HCYC-uk", habitId, "uk-test-user", "또 인증", targetDate, 1);
		assertThatThrownBy(() -> habitVerificationRepositoryPort.save(second))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("uk_habit_verifications_upload_session — 같은 upload_session_id 인증 2건 insert 시 제약 위반, NULL은 다건 허용")
	void duplicateUploadSessionVerification_violatesUniqueConstraint_nullAllowsMultiple() {
		// Given — 같은 upload_session_id(999L)로 서로 다른 target_date에 2건 저장 시도
		HabitVerification first = HabitVerification.createPhoto(
				"HCYC-uk", habitId, "uk-test-user", 999L,
				"https://s3.example.com/1.jpg", null, LocalDate.now(), 1);
		habitVerificationRepositoryPort.save(first);

		HabitVerification second = HabitVerification.createPhoto(
				"HCYC-uk", habitId, "uk-test-user", 999L,
				"https://s3.example.com/2.jpg", null, LocalDate.now().minusDays(1), 1);

		// When & Then
		assertThatThrownBy(() -> habitVerificationRepositoryPort.save(second))
				.isInstanceOf(DataIntegrityViolationException.class);

		// NULL 세션(TEXT 인증)은 다건 허용 — PostgreSQL UNIQUE는 NULL 중복을 허용
		HabitVerification textOne = HabitVerification.createText(
				"HCYC-uk", habitId, "uk-test-user", "텍스트1", LocalDate.now().minusDays(2), 1);
		HabitVerification textTwo = HabitVerification.createText(
				"HCYC-uk", habitId, "uk-test-user", "텍스트2", LocalDate.now().minusDays(3), 1);
		habitVerificationRepositoryPort.save(textOne);
		habitVerificationRepositoryPort.save(textTwo);

		assertThat(habitVerificationRepositoryPort.existsByHabitIdAndTargetDate(habitId, LocalDate.now().minusDays(2)))
				.isTrue();
		assertThat(habitVerificationRepositoryPort.existsByHabitIdAndTargetDate(habitId, LocalDate.now().minusDays(3)))
				.isTrue();
	}
}
