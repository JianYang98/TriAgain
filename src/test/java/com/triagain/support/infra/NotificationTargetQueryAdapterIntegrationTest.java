package com.triagain.support.infra;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.triagain.common.util.IdGenerator;
import com.triagain.crew.domain.model.Crew;
import com.triagain.crew.domain.model.CrewMember;
import com.triagain.crew.domain.vo.CrewStatus;
import com.triagain.crew.domain.vo.CrewVisibility;
import com.triagain.crew.domain.vo.VerificationType;
import com.triagain.crew.port.out.CrewRepositoryPort;
import com.triagain.support.port.out.NotificationTargetQueryPort;
import com.triagain.support.port.out.NotificationTargetQueryPort.ReminderTarget;
import com.triagain.user.domain.model.User;
import com.triagain.user.port.out.UserRepositoryPort;
import com.triagain.verification.domain.model.Verification;
import com.triagain.verification.domain.vo.ReviewStatus;
import com.triagain.verification.domain.vo.VerificationStatus;
import com.triagain.verification.port.out.VerificationRepositoryPort;

/**
 * 결함2 회귀(Codex 리뷰, 2026-07-24) — 리마인더 대상 쿼리가 CANCELLED 인증을 "인증함"으로 오판하던 버그 검증.
 * <p>
 * {@code ChallengeJpaRepository.findExpiredWithoutVerification}(D14-a)와 동일 유형의 누락이었다.
 * LEFT JOIN이 CANCELLED 행에도 매칭되면 {@code v.id IS NULL}이 거짓이 되어 취소자가 리마인더 대상에서
 * 빠진다. ON 절(WHERE 아님)에 {@code v.status <> 'CANCELLED'}를 추가해 CANCELLED만 있는 유저는
 * 조인 자체가 안 되도록 고쳤다 — 전용 TestContainers + Flyway ON으로 실제 네이티브 쿼리를 검증한다.
 */
@SpringBootTest
@ActiveProfiles("integration")
@Tag("integration")
@Testcontainers
class NotificationTargetQueryAdapterIntegrationTest {

	@Container
	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
			.withDatabaseName("triagain_reminder")
			.withUsername("reminderuser")
			.withPassword("reminderpass");

	@DynamicPropertySource
	static void configureProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		registry.add("spring.datasource.username", POSTGRES::getUsername);
		registry.add("spring.datasource.password", POSTGRES::getPassword);
		registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
		registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
	}

	@Autowired
	private NotificationTargetQueryPort notificationTargetQueryPort;

	@Autowired
	private CrewRepositoryPort crewRepositoryPort;

	@Autowired
	private UserRepositoryPort userRepositoryPort;

	@Autowired
	private VerificationRepositoryPort verificationRepositoryPort;

	// 크루 deadline_time을 이 값으로 고정하고, 쿼리 윈도우(20:45~21:15)가 그 안쪽에 들도록 맞춘다.
	private static final LocalTime DEADLINE = LocalTime.of(21, 0, 0);
	private static final LocalTime WINDOW_FROM = LocalTime.of(20, 45, 0);
	private static final LocalTime WINDOW_TO = LocalTime.of(21, 15, 0);

	private String createUser(String suffix) {
		String userId = IdGenerator.generate("USER") + "-" + suffix;
		userRepositoryPort.save(User.of(userId, "KAKAO", userId + "@test.com", "닉네임-" + suffix,
				null, null, null, LocalDateTime.now(), LocalDateTime.now(), null, 0));
		return userId;
	}

	private String createCrewWithMember(String suffix, String memberUserId) {
		String crewId = IdGenerator.generate("CREW") + "-" + suffix;
		Crew crew = Crew.of(crewId, memberUserId, "리마인더 테스트 크루", "목표", "인증 내용",
				VerificationType.TEXT, 10, 1, CrewStatus.ACTIVE,
				LocalDate.now().minusDays(10), LocalDate.now().plusDays(30), true,
				Crew.generateInviteCode(), LocalDateTime.now(), DEADLINE, null, CrewVisibility.PRIVATE, 0L, List.of());
		crewRepositoryPort.save(crew);
		crewRepositoryPort.saveMember(CrewMember.createLeader(memberUserId, crewId));
		return crewId;
	}

	@Test
	@DisplayName("R1/결함2 회귀: 오늘 인증 후 취소(CANCELLED)한 유저는 리마인더 대상에 포함된다")
	void cancelledVerification_includedInReminderTargets() {
		// Given
		String userId = createUser("r1");
		String crewId = createCrewWithMember("r1", userId);
		Verification cancelled = Verification.of(
				IdGenerator.generate("VRFY"), "CHAL-r1", userId, crewId, null, null, "취소된 인증",
				VerificationStatus.CANCELLED, 0, LocalDate.now(), 1, 1,
				ReviewStatus.NOT_REQUIRED, LocalDateTime.now());
		verificationRepositoryPort.save(cancelled);

		// When
		List<ReminderTarget> targets = notificationTargetQueryPort
				.findReminderTargets(WINDOW_FROM, WINDOW_TO, LocalDate.now());

		// Then — CANCELLED뿐인 유저는 "미인증"으로 취급돼 리마인더 대상에 들어가야 한다
		assertThat(targets).extracting(ReminderTarget::userId).contains(userId);
	}

	@Test
	@DisplayName("R2: 오늘 정상 인증(APPROVED)한 유저는 리마인더 대상에서 제외된다 (기존 동작 불변)")
	void approvedVerification_excludedFromReminderTargets() {
		// Given
		String userId = createUser("r2");
		String crewId = createCrewWithMember("r2", userId);
		Verification approved = Verification.createText(
				"CHAL-r2", userId, crewId, "오늘 인증", LocalDate.now(), 1, 1);
		verificationRepositoryPort.save(approved);

		// When
		List<ReminderTarget> targets = notificationTargetQueryPort
				.findReminderTargets(WINDOW_FROM, WINDOW_TO, LocalDate.now());

		// Then
		assertThat(targets).extracting(ReminderTarget::userId).doesNotContain(userId);
	}

	@Test
	@DisplayName("R3: 인증 기록 자체가 없는 유저는 리마인더 대상에 포함된다 (LEFT JOIN 기본 동작 불변)")
	void noVerification_includedInReminderTargets() {
		// Given
		String userId = createUser("r3");
		createCrewWithMember("r3", userId);

		// When
		List<ReminderTarget> targets = notificationTargetQueryPort
				.findReminderTargets(WINDOW_FROM, WINDOW_TO, LocalDate.now());

		// Then
		assertThat(targets).extracting(ReminderTarget::userId).contains(userId);
	}
}
