package com.triagain.habit.api;

import static org.assertj.core.api.Assertions.assertThat;

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
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.triagain.habit.domain.model.Habit;
import com.triagain.habit.domain.model.HabitCycle;
import com.triagain.habit.domain.vo.HabitCycleStatus;
import com.triagain.habit.domain.vo.HabitVerificationType;
import com.triagain.habit.port.out.HabitCycleRepositoryPort;
import com.triagain.habit.port.out.HabitRepositoryPort;

import io.restassured.RestAssured;
import io.restassured.response.Response;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * 솔로 인증 더블탭(POST /habits/&#123;habitId&#125;/verifications 2회) — 지는 쪽 에러코드 계약.
 *
 * <h2>측정된 계약 (이 값이 정본이다)</h2>
 * 동시(T1)·순차(T2) <b>둘 다</b> 승자 1건이 {@code 201 CREATED}, 패자는 <b>{@code 400 / V002}</b>
 * ({@code VERIFICATION_DEADLINE_EXCEEDED})다. 2026-07-28 이 하네스에서 4회 반복 관측했고 매번 동일했다
 * (동시 케이스는 어느 스레드가 이기는지만 바뀐다). <b>두 경로는 갈리지 않는다.</b>
 *
 * <h2>왜 V002인가 — 근거</h2>
 * 락이 경합을 경합이 아니게 만들기 때문이다. {@code HabitJpaRepository#findByIdForUpdate}(:18-20)의
 * {@code PESSIMISTIC_WRITE}는 <b>습관 행</b>에 걸리고, {@code CreateHabitVerificationService}가 이걸
 * {@code :45-46}에서 <b>사이클을 읽기 전에</b>({@code :47}) 잡는다. 그래서 패자는 승자의
 * {@code @Transactional}(:43)이 커밋될 때까지 블록되고, 깨어나서 읽는 사이클은 이미
 * {@code completedDays=1}이다(READ_COMMITTED). 그 결과 {@code :78-80}의 기대 슬롯 가드에서
 * {@code expectedSlot = startDate.plusDays(1)} 즉 <b>내일</b>이 되어 {@code today}와 어긋나고, 여기서 V002로 끝난다.
 * <p>
 * 따라서 패자는 그 아래를 <b>한 줄도 실행하지 못한다</b> — {@code :82-83} 존재검사(V003)도,
 * {@code :132-138} {@code save()} try/catch(V003)도, 그 너머의 DB 유니크 제약도 도달 불가다.
 * 제약 자체는 실재하며 리포지토리 레벨에서 실제로 발화한다({@code HabitUniqueConstraintsIntegrationTest}가
 * V23의 {@code uk_habit_verifications_habit_date}로 검증). 이 엔드포인트가 그 앞에서 걸러질 뿐이다.
 *
 * <h2>측정 전 문서와의 차이 (이 PR에서 정정 완료)</h2>
 * {@code docs/spec/api-spec/habit.md}는 "선검사를 동시에 통과한 요청이 DB 유니크 제약에 걸리면 HB010"이라고
 * 적고 <b>있었으나</b>, 위 이유로 <b>동시 통과 자체가 성립하지 않는다</b>. 관측값은 HB010도 V003도 아닌 V002였고,
 * 그 실측에 맞춰 문서를 같은 PR에서 정정했다.
 *
 * <h2>{@code :82-83} V003는 이 엔드포인트로 도달 불가능하다</h2>
 * 위의 "패자가 V002에서 끝난다"와는 <b>별개의</b> 구조적 이유다. {@code :82-83}에 닿으려면
 * {@code expectedSlot == today}이면서 동시에 오늘자 인증 행이 이미 있어야 하는데,
 * {@code StartHabitCycleService:78-85}가 TODAY 시작 시 정확히 그 조건
 * ({@code existsByHabitIdAndTargetDate})을 막는다("좀비 사이클 방지"). 즉 그 상태 조합은 시스템이
 * 애초에 만들지 못한다. 이건 <b>코드 근거로 판정</b>한 것이며, 테스트로 만들지 않는다 — 시스템이 막아둔
 * 상태를 인위로 꽂아 넣으면 그 테스트가 증명하는 건 계약이 아니라 조작된 전제다.
 *
 * <h2>🔴 하네스 주의 — Flyway를 끄면 이 실험은 무효다</h2>
 * {@code uk_habit_verifications_habit_date}는 V23 마이그레이션 SQL로만 생성된다. 그래서 전용 컨테이너 +
 * {@code ddl-auto=validate}로 <b>Flyway를 켠 채</b> 돌린다({@code HabitUniqueConstraintsIntegrationTest} 패턴).
 * {@code E2eTestBase}는 {@code flyway.enabled=false} + {@code create-drop}이라 제약이 아예 없다 —
 * 거기서 나온 초록은 결과가 아니라 하네스 결함이므로 <b>베이스로 쓰지 말 것</b>.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration")
@Tag("integration")
@Testcontainers
class HabitVerificationConcurrentApiTest {

	private static final Logger log = LoggerFactory.getLogger(HabitVerificationConcurrentApiTest.class);

	@Container
	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
			.withDatabaseName("triagain_habit_dbltap")
			.withUsername("dbltapuser")
			.withPassword("dbltappass");

	@DynamicPropertySource
	static void configureProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		registry.add("spring.datasource.username", POSTGRES::getUsername);
		registry.add("spring.datasource.password", POSTGRES::getPassword);
		registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
		registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
	}

	@LocalServerPort
	private int port;

	@Autowired
	private HabitRepositoryPort habitRepositoryPort;

	@Autowired
	private HabitCycleRepositoryPort habitCycleRepositoryPort;

	@PersistenceContext
	private EntityManager entityManager;

	@Test
	@DisplayName("T1: 동시 2요청 — 승자 201 정확히 1건, 패자는 400 V002(제약까지 못 감)")
	void t1_concurrentDoubleTap() throws Exception {
		// Given
		String userId = "dbltap-t1-user";
		String habitId = givenActiveHabitWithFreshCycle(userId);

		// When — 두 스레드가 거의 동시에 인증 생성 요청
		List<Response> responses = fireConcurrently(userId, habitId);

		// Then
		assertOneWinnerAndLoserGetsV002("T1(동시)", habitId, responses.get(0), responses.get(1));
	}

	@Test
	@DisplayName("T2: 순차 2요청 — T1과 같은 경로(400 V002)로 끝난다, 경로가 갈리지 않는다")
	void t2_sequentialDoubleTap() {
		// Given
		String userId = "dbltap-t2-user";
		String habitId = givenActiveHabitWithFreshCycle(userId);

		// When — 완전히 순차로 2회 호출
		Response first = postVerification(userId, habitId, "순차 인증 1");
		Response second = postVerification(userId, habitId, "순차 인증 2");

		// Then
		assertOneWinnerAndLoserGetsV002("T2(순차)", habitId, first, second);
	}

	/**
	 * 두 요청을 같은 순간에 발사하고 응답 2건을 돌려준다.
	 * <p>
	 * 풀 종료를 {@code finally}에 둔다 — 어느 한쪽이 던지거나 타임아웃하면 non-daemon 워커가 살아남아
	 * Gradle 테스트 워커가 <b>원래 실패 대신 매달린다</b>. finally에는 assertion을 두지 않는다.
	 * 두면 그 assertion이 원래 실패를 덮어써서 고치려던 문제를 그대로 만든다.
	 */
	private List<Response> fireConcurrently(String userId, String habitId) throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(2);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		Callable<Response> task = () -> {
			ready.countDown();
			start.await();
			return postVerification(userId, habitId, "동시 인증");
		};
		try {
			Future<Response> f1 = executor.submit(task);
			Future<Response> f2 = executor.submit(task);
			assertThat(ready.await(10, TimeUnit.SECONDS)).as("두 스레드가 10초 안에 준비되지 않았다").isTrue();
			start.countDown();
			return List.of(f1.get(10, TimeUnit.SECONDS), f2.get(10, TimeUnit.SECONDS));
		} finally {
			executor.shutdownNow();
		}
	}

	/** ACTIVE 습관 + completedDays=0 IN_PROGRESS 사이클(오늘 시작·마감 +3일) 준비 — users 행은 FK가 없어 불필요 */
	private String givenActiveHabitWithFreshCycle(String userId) {
		Habit habit = Habit.create(
				userId, "매일 물 2L", HabitVerificationType.TEXT, LocalTime.of(23, 59, 59), null);
		String habitId = habitRepositoryPort.save(habit).getId();
		HabitCycle cycle = HabitCycle.start(
				habitId, userId, 1, LocalDate.now(), LocalDateTime.now().plusDays(3));
		habitCycleRepositoryPort.save(cycle);
		return habitId;
	}

	/** 인증 생성 요청 1건 — 정적 RestAssured 설정을 건드리지 않도록 요청마다 port를 지정한다(두 스레드가 공유) */
	private Response postVerification(String userId, String habitId, String textContent) {
		return RestAssured.given()
				.port(port)
				.contentType("application/json")
				.header("X-User-Id", userId)
				.body("{\"uploadSessionId\": null, \"textContent\": \"" + textContent + "\"}")
				.when()
				.post("/habits/{habitId}/verifications", habitId);
	}

	/** 승자 1건(201) + 패자 1건(400/V002) + habit_verifications 행 1건 + completedDays 1회 증가 검증 — 클래스 Javadoc의 계약이 정본 */
	private void assertOneWinnerAndLoserGetsV002(String label, String habitId, Response first, Response second) {
		logOutcome(label, first, second);
		List<Response> winners = Stream.of(first, second).filter(r -> r.statusCode() == 201).toList();
		List<Response> losers = Stream.of(first, second).filter(r -> r.statusCode() != 201).toList();

		assertThat(winners).as("%s — 201 CREATED는 정확히 1건이어야 한다", label).hasSize(1);
		assertThat(losers).hasSize(1);

		Response loser = losers.get(0);
		assertThat(loser.statusCode())
				.as("%s — 패자는 400이다(V002는 400). 409(V003/HB010)가 나오면 계약이 바뀐 것", label)
				.isEqualTo(400);
		assertThat(loser.jsonPath().getString("error.code"))
				.as("%s — 패자는 V002다. 습관 행 비관적 락 때문에 DB 유니크 제약(HB010)까지 못 간다", label)
				.isEqualTo("V002");

		assertThat(countVerifications(habitId))
				.as("%s — habit_verifications 행은 정확히 1건이다(결과 불변식). 어느 층이 막았는지는 위 V002가 말한다", label)
				.isEqualTo(1);

		HabitCycle cycle = habitCycleRepositoryPort
				.findByHabitIdAndStatus(habitId, HabitCycleStatus.IN_PROGRESS).orElseThrow();
		assertThat(cycle.getCompletedDays())
				.as("%s — completedDays는 정확히 1회만 증가해야 한다", label).isEqualTo(1);
	}

	/** 이 습관의 인증 행 개수 — habitId가 테스트마다 고유해 날짜 필터 없이도 이 테스트가 만든 행만 센다 */
	private long countVerifications(String habitId) {
		Number count = (Number) entityManager
				.createNativeQuery("SELECT COUNT(*) FROM habit_verifications WHERE habit_id = :id")
				.setParameter("id", habitId)
				.getSingleResult();
		return count.longValue();
	}

	/** 진단 로그 — 패자의 실제 HTTP status·error.code를 눈으로 확인하기 위한 출력 */
	private void logOutcome(String label, Response first, Response second) {
		log.warn("[{}] #1 status={} code={} body={}", label,
				first.statusCode(), first.jsonPath().getString("error.code"), first.asString());
		log.warn("[{}] #2 status={} code={} body={}", label,
				second.statusCode(), second.jsonPath().getString("error.code"), second.asString());
	}
}
