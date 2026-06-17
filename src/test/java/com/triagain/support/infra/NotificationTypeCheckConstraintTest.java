package com.triagain.support.infra;

import java.time.LocalDate;

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

import com.triagain.support.domain.model.Notification;
import com.triagain.support.domain.vo.NotificationTargetType;
import com.triagain.support.domain.vo.NotificationType;
import com.triagain.support.port.out.NotificationRepositoryPort;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T8 (G2): V21 CHECK 제약 적용 검증 — 전용 TestContainers + Flyway ON
 *
 * 공유 TestContainers 싱글톤을 사용하지 않고 전용 컨테이너를 생성한다.
 * 이유: 공유 컨테이너는 CucumberSpringContext(create-drop)와 충돌 가능.
 * 전용 컨테이너에서 Flyway 21개 마이그레이션 전체를 적용하고 V21 제약을 검증한다.
 */
@SpringBootTest
@ActiveProfiles("integration")
@Tag("integration")
@Testcontainers
class NotificationTypeCheckConstraintTest {

	@Container
	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
			.withDatabaseName("triagain_t8")
			.withUsername("t8user")
			.withPassword("t8pass");

	@DynamicPropertySource
	static void configureProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		registry.add("spring.datasource.username", POSTGRES::getUsername);
		registry.add("spring.datasource.password", POSTGRES::getPassword);
		registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
		registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
	}

	@Autowired
	private NotificationRepositoryPort notificationRepositoryPort;

	@Test
	@DisplayName("T8-1: V21 적용 후 CREW_FIRST_VERIFICATION type INSERT 통과")
	void v21_newType_insertPasses() {
		Notification notification = Notification.create(
				"user-t8", NotificationType.CREW_FIRST_VERIFICATION,
				"테스트 제목", "테스트 내용",
				NotificationTargetType.CREW, "crew-t8");

		Notification saved = notificationRepositoryPort.save(notification);
		assertThat(saved.getId()).isNotNull();
		assertThat(saved.getType()).isEqualTo(NotificationType.CREW_FIRST_VERIFICATION);
	}

	@Test
	@DisplayName("T8-2: 멱등 @Query existsCrewFirstVerificationOnDate — 저장 후 true 반환")
	void idempotentQuery_afterSave_returnsTrue() {
		LocalDate today = LocalDate.now();
		String crewId = "crew-idempotent-" + System.currentTimeMillis();
		Notification notification = Notification.create(
				"user-idempotent", NotificationType.CREW_FIRST_VERIFICATION,
				"제목", "내용",
				NotificationTargetType.CREW, crewId);
		notificationRepositoryPort.save(notification);

		boolean exists = notificationRepositoryPort.existsCrewFirstVerificationOnDate(crewId, today);

		assertThat(exists).isTrue();
	}

	@Test
	@DisplayName("T8-3: 멱등 @Query — 다른 크루이면 false 반환")
	void idempotentQuery_differentCrew_returnsFalse() {
		boolean exists = notificationRepositoryPort
				.existsCrewFirstVerificationOnDate("crew-not-exist-xyz", LocalDate.now());

		assertThat(exists).isFalse();
	}
}
