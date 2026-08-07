package com.triagain.support.infra;

import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * 리액션 실 DB 계층(레인 2: 전용 컨테이너 + Flyway ON, ddl-auto=validate)의 공용 베이스.
 * {@code ReactionIntegrationTest}·{@code ReactionApiTest}·{@code ReactionUniqueConstraintsIntegrationTest}가
 * 이 컨테이너 1개를 공유한다(step1-biz-logic.md §7-0).
 * <p>
 * ⚠️ <b>싱글턴 컨테이너 패턴이다 — {@code @Testcontainers}/{@code @Container} 를 쓰지 않는다.</b>
 * 그 조합은 static 필드를 상속으로 공유할 때 <b>첫 하위 클래스가 끝나는 시점에 컨테이너를 중지</b>시킨다.
 * 그러면 뒤따르는 클래스가 죽은 컨테이너를 물고 {@code HikariPool … total=0} 으로 죽는다
 * (2026-08-06 실측: 하위 클래스가 3개가 되자 전체 실행에서 재현. 개별 실행은 통과했다).
 * 선례는 {@code com.triagain.acceptance.TestContainers} — static 블록에서 start() 하고 아무도 stop 하지 않는다.
 */
@SpringBootTest
@ActiveProfiles("integration")
@Tag("integration")
public abstract class ReactionIntegrationTestBase {

	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
			.withDatabaseName("triagain_reaction")
			.withUsername("reactionuser")
			.withPassword("reactionpass");

	static {
		POSTGRES.start();
	}

	@DynamicPropertySource
	static void configureProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		registry.add("spring.datasource.username", POSTGRES::getUsername);
		registry.add("spring.datasource.password", POSTGRES::getPassword);
		registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
		registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
		// 취소 컷오프 해제 — E1-b 가 실제 취소 서비스를 태우는데, 기본 5분이면 하루의 마지막 5분에
		// 벽시계 때문에 결정적으로 깨진다. 컷오프 로직 자체는 CancelVerificationServiceTest:152,171 이
		// 시각·컷오프를 주입해 잠그고 있으므로 여기서 0으로 내려도 커버리지 손실이 없다.
		// ⚠️ 이 베이스를 상속하는 리액션 테스트에서 컷오프를 검증하려면 이 줄을 되돌리고 그 테스트에서 값을 지정한다.
		registry.add("triagain.verification.cancel-cutoff-minutes", () -> "0");
	}
}
