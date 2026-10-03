package com.triagain.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import org.awaitility.core.ThrowingRunnable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.RedisListCommands.Direction;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.util.ClassUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.triagain.TriAgainApplication;
import com.triagain.acceptance.TestContainers;
import com.triagain.common.util.IdGenerator;
import com.triagain.crew.application.CrewJoinPendingWorker;
import com.triagain.crew.domain.model.Crew;
import com.triagain.crew.infra.redis.CrewJoinRedisAdapter;
import com.triagain.crew.infra.redis.CrewJoinRedisProperties;
import com.triagain.crew.infra.redis.RecordingWorkQueue;
import com.triagain.crew.infra.redis.RedisTestContainer;
import com.triagain.crew.port.out.CrewJoinRedisPort.Status;
import com.triagain.crew.port.out.CrewJoinWorkQueuePort;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.restassured.RestAssured;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;

/**
 * Phase 4 startup recovery — 실제 웹 ApplicationContext를 A→B 순차로 띄워 재기동 경계를 모델링한다.
 * E2eTestBase를 쓰지 않는다: create-drop·DatabaseCleanup이 기동 사이의 DB 증거를 지우기 때문이다.
 * 전용 DB에 스키마를 한 번만 만들고(ddl-auto=create) 각 기동은 ddl-auto=none으로 같은 Redis/DB·run을 이어 쓴다.
 * recovery 장애는 lifecycle 시작 전에 걸려 있어야 하므로 테스트 전용 BeanPostProcessor로 Port를 감싼다.
 * OS 프로세스 kill은 재현하지 않는다 — 같은 JVM의 context 종료·재생성 모델이다.
 */
@Tag("e2e")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RedisCrewJoinRecoveryE2eTest {

	private static final String NAMESPACE = "p4";
	private static final OffsetDateTime BASE = OffsetDateTime.parse("2026-01-02T03:04:05.678+09:00");
	private static final RedisScript<String> INIT_SCRIPT =
		RedisScript.of(new ClassPathResource("redis/crew-join/initialize-crew-join.lua"), String.class);

	private final ObjectMapper mapper = new ObjectMapper();
	private final List<ConfigurableApplicationContext> contexts = new ArrayList<>();
	private final List<ListAppender<ILoggingEvent>> appenders = new ArrayList<>();
	private String jdbcUrl;
	private JdbcTemplate db;
	private LettuceConnectionFactory observerFactory;
	private StringRedisTemplate redis;
	private CrewJoinRedisProperties properties;
	private String crewId;

	@BeforeAll
	void prepareSchemaOnce() {
		String database = "p4_recovery_" + UUID.randomUUID().toString().replace("-", "");
		new JdbcTemplate(dataSource(TestContainers.getJdbcUrl())).execute("CREATE DATABASE " + database);
		jdbcUrl = TestContainers.getJdbcUrl().replaceFirst("/triagain_test", "/" + database);
		db = new JdbcTemplate(dataSource(jdbcUrl));
		// 다른 E2E와 같은 엔티티 기반 스키마. create(drop 없음) 뒤 닫고, 이후 기동은 스키마를 건드리지 않는다.
		start("PESSIMISTIC", "create", "schema", probe -> { }).close();
		observerFactory = new LettuceConnectionFactory(
			new RedisStandaloneConfiguration(RedisTestContainer.getHost(), RedisTestContainer.getPort()));
		observerFactory.afterPropertiesSet();
		redis = new StringRedisTemplate(observerFactory);
	}

	@AfterAll
	void closeObserver() {
		observerFactory.destroy();
	}

	@BeforeEach
	void newRunAndCrew() {
		properties = new CrewJoinRedisProperties(NAMESPACE, "rec-" + UUID.randomUUID());
		crewId = insertCrew();
		initRedis();
	}

	@AfterEach
	void closeContexts() {
		contexts.forEach(ConfigurableApplicationContext::close);
		contexts.clear();
		appenders.forEach(workerLogger()::detachAppender);
		appenders.clear();
		redis.delete(redis.keys(properties.keyPrefix() + ":*"));
	}

	@Test
	@DisplayName("P4-T1 processing이 비어 있으면 recovery는 nil로 끝나고 worker가 기존 pending을 DB에 반영한다")
	void p4t1_emptyProcessing_workerStartsAndDrainsExistingPending() {
		// Given — 이전 run의 미처리 pending 1건
		approve("u1", 1);
		// When
		Boot boot = boot(probe -> { });
		// Then
		assertThat(boot.logs()).contains("CREW_JOIN_RECOVERY_SUCCEEDED namespace=p4 runId=" + properties.runId()
			+ " confirmedMoves=0");
		awaitDrained(2);
		assertThat(boot.probe().recoverCalls()).isEqualTo(1);
		assertMember("u1", local(1));
		assertCompensationUntouched();
	}

	@Test
	@DisplayName("P4-T2·T4 복구한 원문은 그대로 claim되어 INSERT 1·인원 +1·원래 confirmedAt으로 commit된 뒤 같은 원문으로 ACK된다")
	void p4t2t4_recoveredRawReplaysIntoDbThenExactAck() {
		// Given — 재직렬화하면 달라지는 필드 순서·공백, DB 미반영
		String raw = "{ \"userId\" : \"u1\",\"confirmedAt\":\"2026-01-02T03:04:06.678+09:00\" ,  \"crewId\":\""
			+ crewId + "\" }";
		redis.opsForList().leftPush(properties.processingKey(), raw);
		// When
		Boot boot = boot(probe -> { });
		// Then
		awaitDrained(2);
		assertThat(boot.logs()).anyMatch(line -> line.endsWith("confirmedMoves=1"));
		assertThat(boot.probe().recoverCalls()).isEqualTo(2);
		assertThat(boot.probe().claimed()).containsExactly(raw);
		assertThat(boot.probe().acked()).containsExactly(raw);
		assertThat(boot.probe().ackResults()).containsExactly(1L);
		assertMember("u1", local(1));
		assertCompensationUntouched();
	}

	@Test
	@DisplayName("P4-T5 commit 후 ACK 전 실패한 원문은 다음 기동에서 replay 0행으로 ID·role·joinedAt·인원을 바꾸지 않고 ACK 1이다")
	void p4t5_committedButUnackedRaw_nextBootReplaysWithoutDoubleCount() {
		// Given — A: 실제 commit 뒤 ACK 전송 전 실패
		approve("u1", 1);
		Boot first = boot(probe -> probe.failFirstAck("before-send"));
		awaitStopped(first, "ACK");
		assertThat(processing()).containsExactly(rawOf("u1"));
		Map<String, Object> committed = memberRow("u1");
		assertThat(currentMembers()).isEqualTo(2);
		first.context().close();
		// When — B: 같은 run, Redis/DB 유지
		Boot second = boot(probe -> { });
		// Then
		awaitDrained(2);
		await().atMost(Duration.ofSeconds(5)).until(() -> second.probe().ackResults().size() == 1);
		assertThat(second.probe().ackResults()).containsExactly(1L);
		assertThat(second.logs()).anyMatch(line -> line.endsWith("confirmedMoves=1"));
		assertThat(memberRow("u1")).isEqualTo(committed);
		assertThat(currentMembers()).isEqualTo(2);
		assertThat(second.worker().isRunning()).isTrue();
		assertCompensationUntouched();
	}

	@Test
	@DisplayName("P4-T8 recovery가 실패해도 context는 기동하고 worker는 시작하지 않으며 context.start()로도 재시도하지 않는다")
	void p4t8_recoveryFailure_contextUpWorkerStoppedNoRetry() {
		// Given — processing [u1], pending [u2]
		approve("u1", 1);
		approve("u2", 2);
		claimAsPreviousRun(1);
		// When
		Boot boot = boot(probe -> probe.failRecoverAtCall(1));
		// Then
		assertThat(boot.context().isActive()).isTrue();
		assertRecoveryFailed(boot, 0);
		boot.context().start();
		holdsFor(Duration.ofMillis(1500), () -> {
			assertThat(boot.probe().recoverCalls()).as("context.start() 뒤 recovery 호출 수").isEqualTo(1);
			assertThat(boot.probe().claimAttempts()).as("claimRaw 호출 시도").isZero();
		});
		assertThat(boot.worker().isRunning()).isFalse();
		assertThat(processing()).containsExactly(rawOf("u1"));
		assertThat(pending()).containsExactly(rawOf("u2"));
		var health = RestAssured.given().baseUri(boot.baseUri()).get("/actuator/health").then().extract();
		assertThat(health.statusCode()).isEqualTo(200);
		assertCompensationUntouched();
	}

	@Test
	@DisplayName("P4-T9 recovery 실패 중에도 신규 공개 가입은 201로 Redis에 승인되고 DB projection은 멈춰 있다")
	void p4t9_recoveryFailure_admissionContinuesWhileDbStays() {
		// Given — 이전 run: processing [u1], pending [u2], Redis 인원 3·DB 인원 1
		approve("u1", 1);
		approve("u2", 2);
		claimAsPreviousRun(1);
		Boot boot = boot(probe -> probe.failRecoverAtCall(1));
		assertRecoveryFailed(boot, 0);
		List<Map<String, Object>> dbBefore = memberRows();
		// When — 서로 다른 신규 사용자 2명
		assertThat(join(boot, "n1").jsonPath().getInt("data.currentMembers")).isEqualTo(4);
		assertThat(join(boot, "n2").jsonPath().getInt("data.currentMembers")).isEqualTo(5);
		// Then — Redis 승인 증가(새 승인분은 pending 왼쪽, 이전 run 원문은 그대로)
		assertThat(redis.opsForZSet().score(crewKey("members"), "n2")).isEqualTo(5.0);
		assertThat(redis.opsForHash().get(crewKey("meta"), "seq")).isEqualTo("5");
		assertThat(users(pending())).containsExactly("n2", "n1", "u2");
		assertThat(processing()).containsExactly(rawOf("u1"));
		// Then — DB는 이전 snapshot 그대로, DB 기반 조회에서 신규 사용자는 비멤버
		assertThat(memberRows()).isEqualTo(dbBefore);
		assertThat(currentMembers()).isEqualTo(1);
		var detail = RestAssured.given().baseUri(boot.baseUri()).header("X-User-Id", "n1")
			.get("/crews/" + crewId).then().extract();
		assertThat(detail.statusCode()).isEqualTo(403);
		holdsFor(Duration.ofSeconds(1), () -> {
			assertThat(boot.probe().recoverCalls()).as("recovery 재시도").isEqualTo(1);
			assertThat(boot.probe().claimAttempts()).as("claimRaw 호출 시도").isZero();
		});
		assertCompensationUntouched();
	}

	@Test
	@DisplayName("P4-T10 A 기동의 부분 복구 실패 뒤 B 기동이 남은 processing을 이어 비우고 새 승인까지 A→F로 반영한다")
	void p4t10_partialFailureThenNextBootFinishesInFifo() {
		// Given — 승인 A..E, 이전 run의 claim 3건: processing [C,B,A], pending [E,D]
		for (String user : List.of("A", "B", "C", "D", "E")) {
			approve(user, user.charAt(0) - 'A' + 1);
		}
		claimAsPreviousRun(3);
		Boot first = boot(probe -> probe.failRecoverAtCall(2));
		assertRecoveryFailed(first, 1);
		assertThat(users(processing())).containsExactly("B", "A");
		assertThat(users(pending())).containsExactly("E", "D", "C");
		LocalDateTime joinedF = LocalDateTime.parse(join(first, "F").jsonPath().getString("data.joinedAt"));
		first.context().close();
		assertThat(users(pending())).containsExactly("F", "E", "D", "C");
		// When — 같은 run으로 재기동
		Boot second = boot(probe -> { });
		// Then
		assertThat(second.logs()).anyMatch(line -> line.endsWith("confirmedMoves=2"));
		awaitDrained(7);
		assertThat(users(second.probe().claimed())).containsExactly("A", "B", "C", "D", "E", "F");
		assertFinalConsistency(joinedF);
		assertCompensationUntouched();
	}

	@ParameterizedTest(name = "{0}")
	@ValueSource(strings = {"malformed", "invariant", "ack-zero", "ack-response-lost"})
	@DisplayName("P4-T13 복구한 작업의 parse/DB/ACK 실패는 Phase 3대로 중단하고 추가 ACK·claim·자동 recovery가 없다")
	void p4t13_replayFailuresKeepPhase3Contract(String failure) {
		// Given — processing [대상], pending [u2]
		approve("u2", 2);
		String target = failure.equals("malformed") ? "{malformed-payload" : rawOf("u1", 1);
		redis.opsForList().leftPush(properties.processingKey(), target);
		if (failure.equals("invariant")) {
			db.update("UPDATE crews SET max_members = current_members WHERE id = ?", crewId);
		}
		// When
		Boot boot = boot(probe -> probe.failFirstAck(failure.startsWith("ack-") ? failure.substring(4) : null));
		// Then
		awaitStopped(boot, failure.startsWith("ack-") ? "ACK" : failure.equals("malformed") ? "parse" : "DB");
		holdsFor(Duration.ofMillis(1500), () -> {
			assertThat(boot.probe().recoverCalls()).as("자동 recovery 없음").isEqualTo(2);
			assertThat(boot.probe().claimAttempts()).as("다음 pending claim 없음").isEqualTo(1);
		});
		assertThat(boot.probe().ackCalls()).isEqualTo(failure.startsWith("ack-") ? 1 : 0);
		assertThat(pending()).containsExactly(rawOf("u2"));
		if (failure.startsWith("ack-")) {
			// DB는 commit됨. ACK 0행·응답 유실의 processing 잔존 여부는 단정하지 않는다.
			assertMember("u1", local(1));
		} else {
			assertThat(processing()).containsExactly(target);
			assertThat(memberRows()).hasSize(1);
			assertThat(currentMembers()).isEqualTo(1);
		}
	}

	// ===== 기동 =====

	private Boot boot(Consumer<RecordingWorkQueue> faults) {
		RecordingWorkQueue[] probe = new RecordingWorkQueue[1];
		ConfigurableApplicationContext[] context = new ConfigurableApplicationContext[1];
		ListAppender<ILoggingEvent> logs = new ListAppender<>();
		// recovery 결과와 무관하게 refresh는 성공해야 한다 — 실패 전파를 단언 실패로 드러낸다.
		assertThatNoException().as("REDIS_ASYNC 앱 기동").isThrownBy(() -> context[0] = start("REDIS_ASYNC", "none",
			properties.runId(), wrapped -> {
				faults.accept(wrapped);
				probe[0] = wrapped;
			}, logs));
		contexts.add(context[0]);
		return new Boot(context[0], probe[0], logs);
	}

	private ConfigurableApplicationContext start(String strategy, String ddl, String runId,
		Consumer<RecordingWorkQueue> onQueue) {
		return start(strategy, ddl, runId, onQueue, new ListAppender<>());
	}

	private ConfigurableApplicationContext start(String strategy, String ddl, String runId,
		Consumer<RecordingWorkQueue> onQueue, ListAppender<ILoggingEvent> logs) {
		return new SpringApplicationBuilder(TriAgainApplication.class)
			.initializers(context -> {
				var beanFactory = context.getBeanFactory();
				beanFactory.addBeanPostProcessor(new QueueProbeInstaller(onQueue));
				// @SpringBootTest처럼 테스트 클래스 내부 @Configuration·@TestConfiguration을 스캔에서 제외한다.
				beanFactory.registerSingleton("testTypeExcludeFilter", BeanUtils.instantiateClass(ClassUtils
					.resolveClassName("org.springframework.boot.test.context.filter.TestTypeExcludeFilter", null)));
				// 로깅 시스템 초기화 뒤에 붙인다 — 그 전에 붙이면 기동 중 reset으로 떨어진다.
				logs.start();
				workerLogger().addAppender(logs);
				appenders.add(logs);
			})
			.registerShutdownHook(false)
			.run("--spring.profiles.active=integration", "--spring.main.banner-mode=off", "--server.port=0",
				"--spring.datasource.url=" + jdbcUrl, "--spring.datasource.username=" + TestContainers.getUsername(),
				"--spring.datasource.password=" + TestContainers.getPassword(),
				"--spring.datasource.driver-class-name=org.postgresql.Driver",
				"--spring.jpa.hibernate.ddl-auto=" + ddl, "--spring.flyway.enabled=false",
				"--spring.data.redis.host=" + RedisTestContainer.getHost(),
				"--spring.data.redis.port=" + RedisTestContainer.getPort(),
				"--triagain.crew.lock-strategy=" + strategy, "--triagain.crew.redis.namespace=" + NAMESPACE,
				"--triagain.crew.redis.run-id=" + runId);
	}

	private static Logger workerLogger() {
		return (Logger)LoggerFactory.getLogger(CrewJoinPendingWorker.class);
	}

	/** 실제 Port bean을 lifecycle 시작 전에 감싼다 — recovery 장애·호출 시도 관측용 */
	private record QueueProbeInstaller(Consumer<RecordingWorkQueue> onQueue) implements BeanPostProcessor {

		@Override
		public Object postProcessAfterInitialization(Object bean, String beanName) {
			if (bean instanceof CrewJoinWorkQueuePort port) {
				RecordingWorkQueue probe = new RecordingWorkQueue(port);
				onQueue.accept(probe);
				return probe;
			}
			return bean;
		}
	}

	private record Boot(ConfigurableApplicationContext context, RecordingWorkQueue probe,
		ListAppender<ILoggingEvent> appender) {

		CrewJoinPendingWorker worker() {
			return context.getBean(CrewJoinPendingWorker.class);
		}

		String baseUri() {
			return "http://localhost:" + context.getEnvironment().getProperty("local.server.port");
		}

		List<String> logs() {
			return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
		}
	}

	// ===== 단언 =====

	private void assertRecoveryFailed(Boot boot, int confirmedMoves) {
		holdsFor(Duration.ofMillis(500), () -> assertThat(boot.probe().claimAttempts()).as("claimRaw 호출 시도").isZero());
		assertThat(boot.logs()).filteredOn(line -> line.startsWith("CREW_JOIN_RECOVERY_FAILED")).singleElement()
			.asString().contains("namespace=p4", "runId=" + properties.runId(), "confirmedMoves=" + confirmedMoves,
				"workerStarted=false", "retryInProcess=false", "admissionBlockedByRecovery=false");
		assertThat(boot.logs()).noneMatch(line -> line.contains("RECOVERY_SUCCEEDED"));
		assertThat(boot.worker().isRunning()).isFalse();
		assertThat(boot.probe().recoverCalls()).isEqualTo(confirmedMoves + 1);
		assertThat(boot.probe().ackCalls()).isZero();
	}

	private static void holdsFor(Duration window, ThrowingRunnable assertion) {
		await().during(window).atMost(window.plusSeconds(2)).untilAsserted(assertion);
	}

	private void assertFinalConsistency(LocalDateTime joinedF) {
		assertThat(processing()).isEmpty();
		assertThat(pending()).isEmpty();
		List<String> dbUsers = memberRows().stream().map(row -> (String)row.get("user_id")).toList();
		assertThat(new HashSet<>(dbUsers)).isEqualTo(redis.opsForZSet().range(crewKey("members"), 0, -1));
		assertThat(db.queryForObject("SELECT count(*) FROM crew_members WHERE crew_id = ?", Integer.class, crewId))
			.isEqualTo(currentMembers()).isEqualTo(7);
		for (String user : List.of("A", "B", "C", "D", "E")) {
			assertMember(user, local(user.charAt(0) - 'A' + 1));
		}
		assertMember("F", joinedF);
	}

	private void assertMember(String userId, LocalDateTime joinedAt) {
		Map<String, Object> row = memberRow(userId);
		assertThat(row.get("role")).isEqualTo("MEMBER");
		assertThat(((java.sql.Timestamp)row.get("joined_at")).toLocalDateTime()).isEqualTo(joinedAt);
	}

	/** 매 기동 뒤 startup compensation·스케줄러가 fixture 크루를 건드리지 않았는지 확인 */
	private void assertCompensationUntouched() {
		Map<String, Object> crew = db.queryForMap("SELECT status, version, start_date FROM crews WHERE id = ?", crewId);
		assertThat(crew.get("status")).isEqualTo("RECRUITING");
		assertThat(crew.get("version")).isEqualTo(0L);
		assertThat(((java.sql.Date)crew.get("start_date")).toLocalDate()).isAfter(LocalDate.now());
	}

	private void awaitDrained(int members) {
		await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
			assertThat(currentMembers()).isEqualTo(members);
			assertThat(pending()).isEmpty();
			assertThat(processing()).isEmpty();
		});
	}

	private void awaitStopped(Boot boot, String stage) {
		await().atMost(Duration.ofSeconds(10)).until(() -> !boot.worker().isRunning());
		assertThat(boot.logs()).anyMatch(line -> line.startsWith("CREW_JOIN_WORKER_STOPPED stage=" + stage));
	}

	// ===== fixture (refresh 전, 별도 연결) =====

	private String insertCrew() {
		String id = IdGenerator.generate("CREW");
		// 미래 시작·먼 종료: 활성화/종료 보정과 가입 마감(종료 3일 전)에서 벗어난다. 다른 writer 없음.
		db.update("INSERT INTO crews (id, creator_id, name, goal, verification_content, verification_type, "
				+ "min_members, max_members, current_members, status, start_date, end_date, allow_late_join, "
				+ "invite_code, created_at, deadline_time, visibility, version) "
				+ "VALUES (?, 'leader', 'Recovery', 'goal', 'text', 'TEXT', 1, 10, 1, 'RECRUITING', ?, ?, true, ?, "
				+ "now(), '23:59:59', 'PUBLIC', 0)",
			id, LocalDate.now().plusDays(5), LocalDate.now().plusDays(20), Crew.generateInviteCode());
		db.update("INSERT INTO crew_members (id, user_id, crew_id, role, joined_at) "
			+ "VALUES (?, 'leader', ?, 'LEADER', '2026-01-01 00:00:00')", IdGenerator.generate("CRMB"), id);
		return id;
	}

	private void initRedis() {
		assertThat(redis.execute(INIT_SCRIPT, List.of(crewKey("members"), crewKey("meta")), "10", "[\"leader\"]"))
			.isEqualTo("OK");
	}

	/** 이전 실행의 실제 승인 Lua — members·seq·pending을 정합하게 만든다 */
	private void approve(String userId, int second) {
		var producer = new CrewJoinRedisAdapter(redis, properties, mapper);
		assertThat(producer.approve(crewId, userId, BASE.plusSeconds(second)).status()).isEqualTo(Status.JOIN_SUCCESS);
	}

	/** 이전 실행 worker의 claim(pending RIGHT → processing LEFT)을 재현 */
	private void claimAsPreviousRun(int count) {
		for (int i = 0; i < count; i++) {
			assertThat(redis.opsForList().move(properties.pendingKey(), Direction.RIGHT,
				properties.processingKey(), Direction.LEFT)).isNotNull();
		}
	}

	// ===== 관측 =====

	private ExtractableResponse<Response> join(Boot boot, String userId) {
		var response = RestAssured.given().baseUri(boot.baseUri()).contentType("application/json")
			.header("X-User-Id", userId).body(Map.of()).post("/crews/" + crewId + "/join").then().extract();
		assertThat(response.statusCode()).isEqualTo(201);
		return response;
	}

	private String rawOf(String userId) {
		return pending().stream().filter(raw -> raw.contains("\"" + userId + "\"")).findFirst()
			.or(() -> processing().stream().filter(raw -> raw.contains("\"" + userId + "\"")).findFirst())
			.orElseThrow();
	}

	private String rawOf(String userId, int second) {
		return "{\"crewId\":\"" + crewId + "\",\"userId\":\"" + userId + "\",\"confirmedAt\":\""
			+ BASE.plusSeconds(second) + "\"}";
	}

	private LocalDateTime local(int second) {
		return BASE.plusSeconds(second).toLocalDateTime();
	}

	private List<String> users(List<String> raws) {
		return raws.stream().map(raw -> {
			try {
				return mapper.readTree(raw).get("userId").asText();
			} catch (com.fasterxml.jackson.core.JsonProcessingException e) {
				throw new IllegalStateException(e);
			}
		}).toList();
	}

	private List<String> pending() {
		return redis.opsForList().range(properties.pendingKey(), 0, -1);
	}

	private List<String> processing() {
		return redis.opsForList().range(properties.processingKey(), 0, -1);
	}

	private Map<String, Object> memberRow(String userId) {
		return db.queryForMap("SELECT id, role, joined_at FROM crew_members WHERE crew_id = ? AND user_id = ?",
			crewId, userId);
	}

	private List<Map<String, Object>> memberRows() {
		return db.queryForList("SELECT id, user_id, role, joined_at FROM crew_members WHERE crew_id = ? ORDER BY id",
			crewId);
	}

	private int currentMembers() {
		return db.queryForObject("SELECT current_members FROM crews WHERE id = ?", Integer.class, crewId);
	}

	private String crewKey(String suffix) {
		return properties.keyPrefix() + ":crew:" + crewId + ":" + suffix;
	}

	private static DriverManagerDataSource dataSource(String url) {
		return new DriverManagerDataSource(url, TestContainers.getUsername(), TestContainers.getPassword());
	}
}
