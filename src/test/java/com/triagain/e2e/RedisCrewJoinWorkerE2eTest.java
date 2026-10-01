package com.triagain.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.triagain.common.util.IdGenerator;
import com.triagain.crew.application.CrewJoinPendingWorker;
import com.triagain.crew.application.CrewJoinPersistenceService;
import com.triagain.crew.domain.model.Crew;
import com.triagain.crew.domain.model.CrewMember;
import com.triagain.crew.domain.vo.CrewRole;
import com.triagain.crew.domain.vo.CrewStatus;
import com.triagain.crew.domain.vo.CrewVisibility;
import com.triagain.crew.domain.vo.VerificationType;
import com.triagain.crew.infra.redis.RedisTestContainer;
import com.triagain.crew.port.out.CrewJoinRedisPort;
import com.triagain.crew.port.out.CrewJoinWorkQueuePort;
import com.triagain.crew.port.out.CrewRepositoryPort;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

/** 실제 Redis·PostgreSQL 경계 검증. lifecycle만 자동 기동 전에 격리하고 production worker를 명시 실행한다. */
class RedisCrewJoinWorkerE2eTest extends E2eTestBase {

	private static final String RUN_ID = "worker-" + UUID.randomUUID();
	private static final String PREFIX = "triagain:crew-join:{p3:" + RUN_ID + "}";
	private static final LocalDateTime CONFIRMED_AT = LocalDateTime.of(2026, 1, 2, 3, 4, 5, 678000000);
	private static final RedisScript<String> INIT_SCRIPT =
		RedisScript.of(new ClassPathResource("redis/crew-join/initialize-crew-join.lua"), String.class);

	@MockitoBean
	private CrewJoinPendingWorker isolatedWorker;
	@MockitoSpyBean
	private CrewRepositoryPort repository;
	@MockitoSpyBean
	private CrewJoinWorkQueuePort queue;
	@Autowired
	private CrewJoinRedisPort producer;
	@Autowired
	private CrewJoinPersistenceService persistence;
	@Autowired
	private ObjectMapper mapper;
	@Autowired
	private StringRedisTemplate redis;
	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private TransactionTemplate transactions;

	private final Logger workerLogger = (Logger)LoggerFactory.getLogger(CrewJoinPendingWorker.class);
	private ListAppender<ILoggingEvent> workerLogs;
	private CrewJoinPendingWorker worker;
	private Crew crew;

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		registry.add("spring.data.redis.host", RedisTestContainer::getHost);
		registry.add("spring.data.redis.port", RedisTestContainer::getPort);
		registry.add("triagain.crew.lock-strategy", () -> "REDIS_ASYNC");
		registry.add("triagain.crew.redis.namespace", () -> "p3");
		registry.add("triagain.crew.redis.run-id", () -> RUN_ID);
	}

	@BeforeEach
	void fixture() {
		redis.delete(redis.keys(PREFIX + ":*"));
		createUser("leader");
		createUser("member");
		crew = Crew.of(IdGenerator.generate("CREW"), "leader", "Worker test", "goal", "text",
			VerificationType.TEXT, 5, 1, CrewStatus.RECRUITING, LocalDate.now().plusDays(2),
			LocalDate.now().plusDays(20), true, Crew.generateInviteCode(), LocalDateTime.now(),
			Crew.DEFAULT_DEADLINE_TIME, null, CrewVisibility.PUBLIC, 0L, List.of());
		repository.save(crew);
		repository.saveMember(CrewMember.createLeader("leader", crew.getId()));
		// 미래 시작일 fixture: startup compensation·자정 활성화 대상이 아니며 run 중 다른 writer 없음.
		assertThat(crew.getStartDate()).isAfter(LocalDate.now());
		worker = new CrewJoinPendingWorker(queue, persistence, mapper, "p3", RUN_ID);
		workerLogs = new ListAppender<>();
		workerLogs.start();
		workerLogger.addAppender(workerLogs);
	}

	@AfterEach
	void stopWorker() {
		worker.stop();
		workerLogger.detachAppender(workerLogs);
		assertThat(worker.isRunning()).isFalse();
		// 격리된 테스트 fixture만 정리. 실패한 실험 run의 복구 절차가 아니다.
		redis.delete(redis.keys(PREFIX + ":*"));
	}

	@Test
	void p3t1_fifoAndExactRawAck() {
		String first = raw("member");
		String second = first.replace("{", "{ ").replace("member", "second");
		String third = "{\"userId\":\"third\",\"confirmedAt\":\"2026-01-02T03:04:05.678+09:00\","
			+ "\"crewId\":\"" + crew.getId() + "\"}";
		for (String value : List.of(first, second, third)) {
			push(value);
		}
		assertThat(queue.claimRaw(Duration.ofSeconds(1))).isEqualTo(first);
		assertThat(queue.claimRaw(Duration.ofSeconds(1))).isEqualTo(second);
		assertThat(queue.claimRaw(Duration.ofSeconds(1))).isEqualTo(third);
		assertThat(processing()).containsExactly(third, second, first);
		assertThat(queue.ackRaw(second.replace("{ ", "{"))).isZero();
		assertThat(queue.ackRaw(second)).isEqualTo(1);
		assertThat(queue.ackRaw(second)).isZero();
		assertThat(processing()).containsExactly(third, first);
	}

	@Test
	void p3t1_luaApprovalOrderMatchesConsumerFifo() throws Exception {
		initializeRedis();
		for (String userId : List.of("first", "second", "third")) {
			producer.approve(crew.getId(), userId, CONFIRMED_AT.atOffset(ZoneOffset.ofHours(9)));
		}
		for (String userId : List.of("first", "second", "third")) {
			String claimed = queue.claimRaw(Duration.ofSeconds(1));
			assertThat(claimed).isNotNull();
			assertThat(mapper.readTree(claimed).get("userId").asText()).isEqualTo(userId);
			assertThat(queue.ackRaw(claimed)).isEqualTo(1);
		}
		assertThat(pending()).isEmpty();
		assertThat(processing()).isEmpty();
	}

	@Test
	void p3t2_multipleNilCyclesThenWorkAndIdleShutdown() {
		AtomicInteger nils = new AtomicInteger();
		doAnswer(invocation -> {
			Object result = invocation.callRealMethod();
			if (result == null) {
				nils.incrementAndGet();
			}
			return result;
		}).when(queue).claimRaw(any());
		long started = System.nanoTime();
		worker.start();
		await().atMost(Duration.ofSeconds(8)).until(() -> nils.get() >= 3);
		assertThat(Duration.ofNanos(System.nanoTime() - started)).isGreaterThan(Duration.ofSeconds(2));
		assertThat(worker.isRunning()).isTrue();
		push(raw("member"));
		awaitCommittedAndAcked();
		worker.stop();
		assertThat(worker.isRunning()).isFalse();
		assertThat(workerLogs.list).noneMatch(event -> event.getFormattedMessage().contains("WORKER_STOPPED"));
		int claims = mockingDetails(queue).getInvocations().size();
		push(raw("next"));
		assertThat(pending()).containsExactly(raw("next"));
		assertThat(mockingDetails(queue).getInvocations()).hasSize(claims);
	}

	@Test
	void p3t3_newMembershipCommitsBeforeExactAck() {
		doAnswer(invocation -> {
			assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
			assertThat(count()).isEqualTo(2);
			assertThat(memberRows()).isEqualTo(1);
			return invocation.callRealMethod();
		}).when(queue).ackRaw(any());
		push(raw("member"));
		worker.start();
		awaitCommittedAndAcked();
		verify(queue).ackRaw(raw("member"));
		assertStoredMembership(CONFIRMED_AT);
	}

	@Test
	void p3t4_duplicatePairPreservesCountRoleAndTime() {
		assertThat(persistence.persist(crew.getId(), "member", CONFIRMED_AT)).isEqualTo(1);
		assertThat(persistence.persist(crew.getId(), "member", CONFIRMED_AT.plusDays(1))).isZero();
		assertStoredMembership(CONFIRMED_AT);
		CrewMember leader = repository.findById(crew.getId()).orElseThrow().getMembers().stream()
			.filter(member -> member.getRole() == CrewRole.LEADER).findFirst().orElseThrow();
		assertThat(persistence.persist(crew.getId(), "leader", CONFIRMED_AT)).isZero();
		CrewMember unchanged = repository.findById(crew.getId()).orElseThrow().getMembers().stream()
			.filter(member -> member.getUserId().equals("leader")).findFirst().orElseThrow();
		assertThat(unchanged.getRole()).isEqualTo(CrewRole.LEADER);
		assertThat(unchanged.getJoinedAt()).isEqualTo(leader.getJoinedAt());
		assertThat(count()).isEqualTo(2);
	}

	@Test
	void p3t5_primaryKeyCollisionIsNotTargetDuplicate() {
		CrewMember leader = repository.findById(crew.getId()).orElseThrow().getMembers().get(0);
		CrewMember collision = CrewMember.of(leader.getId(), "member", crew.getId(), CrewRole.MEMBER, CONFIRMED_AT);
		assertThatThrownBy(() -> transactions.execute(status -> repository.insertMemberIfAbsent(collision)))
			.isInstanceOf(DataIntegrityViolationException.class);
		assertUnchangedDb();
	}

	@ParameterizedTest
	@ValueSource(strings = {"length", "connection", "sql"})
	void p3t5_otherDbErrorsAreNotReplay(String failure) {
		String payload = raw(failure.equals("length") ? "x".repeat(65) : "member");
		if (failure.equals("connection")) {
			doThrow(new DataAccessResourceFailureException("injected connection failure"))
				.when(repository).insertMemberIfAbsent(any());
		} else if (failure.equals("sql")) {
			doAnswer(invocation -> jdbc.update("INSERT INTO nonexistent_p3_table VALUES (1)"))
				.when(repository).insertMemberIfAbsent(any());
		}
		failRun(payload);
		assertUnchangedDb();
	}

	@ParameterizedTest
	@ValueSource(strings = {"missing", "full"})
	void p3t6_zeroIncrementRollsBackInsertAsInvariantFailure(String scenario) {
		String crewId = crew.getId();
		if (scenario.equals("missing")) {
			crewId = "CREW-MISSING";
		} else {
			jdbc.update("UPDATE crews SET max_members = current_members WHERE id = ?", crewId);
		}
		String target = crewId;
		assertThatThrownBy(() -> persistence.persist(target, "member", CONFIRMED_AT))
			.isInstanceOf(IllegalStateException.class).hasMessageContaining("invariant mismatch");
		failRun(raw("member").replace(crew.getId(), crewId));
		assertUnchangedDb();
	}

	@ParameterizedTest
	@ValueSource(strings = {"update", "commit"})
	void p3t7_failureAfterInsertRollsBackAndDoesNotAckOrClaimNext(String boundary) {
		doAnswer(invocation -> {
			assertThat(memberRows()).isEqualTo(1); // 同 transaction 안에서 INSERT는 이미 실행됨
			if (boundary.equals("update")) {
				throw new IllegalStateException("injected update failure");
			}
			Object result = invocation.callRealMethod();
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void beforeCommit(boolean readOnly) {
					throw new IllegalStateException("confirmed rollback before commit");
				}
			});
			return result;
		}).when(repository).incrementMembersIfNotFull(crew.getId());
		failRun(raw("member"));
		assertUnchangedDb();
	}

	@ParameterizedTest
	@ValueSource(strings = {"before-send", "zero", "response-lost"})
	void p3t8_ackFailureKeepsCommittedDbWithoutRetry(String boundary) {
		doAnswer(invocation -> {
			if (boundary.equals("before-send")) {
				throw new IllegalStateException("ACK not sent");
			}
			invocation.callRealMethod(); // zero/response lost에서는 서버가 이미 raw를 제거한 모델
			if (boundary.equals("zero")) {
				return invocation.callRealMethod(); // 실제 LREM 0행
			}
			throw new IllegalStateException("ACK response lost");
		}).when(queue).ackRaw(any());
		push(raw("member"));
		push(raw("next"));
		worker.start();
		awaitStopped();
		assertStoredMembership(CONFIRMED_AT);
		assertThat(processing()).containsExactlyElementsOf(boundary.equals("before-send")
			? List.of(raw("member")) : List.of());
		assertThat(pending()).containsExactly(raw("next"));
		verify(queue, times(1)).claimRaw(any());
		verify(queue, times(1)).ackRaw(raw("member"));
		assertThat(persistence.persist(crew.getId(), "member", CONFIRMED_AT)).isZero();
		assertStoredMembership(CONFIRMED_AT);
	}

	@ParameterizedTest
	@ValueSource(strings = {"before-send", "response-lost"})
	void p3t2_claimExceptionStopsWithoutRetry(String boundary) {
		doAnswer(invocation -> {
			if (boundary.equals("response-lost")) {
				invocation.callRealMethod();
			}
			throw new org.springframework.dao.QueryTimeoutException("injected unknown claim result");
		}).when(queue).claimRaw(any());
		push(raw("member"));
		push(raw("next"));
		worker.start();
		awaitStopped();
		verify(queue, times(1)).claimRaw(any());
		verify(queue, never()).ackRaw(any());
		verify(repository, never()).insertMemberIfAbsent(any());
		assertUnchangedDb();
		// 주입 경계를 아는 테스트만 실제 상태를 단언한다. worker는 어느 상태인지 추정하지 않는다.
		assertThat(processing()).containsExactlyElementsOf(boundary.equals("response-lost")
			? List.of(raw("member")) : List.of());
		assertThat(pending()).containsExactlyElementsOf(boundary.equals("response-lost")
			? List.of(raw("next")) : List.of(raw("next"), raw("member")));
	}

	@Test
	void p3t7_commitResultUnknownDoesNotAckEvenWhenDbCommitted() {
		CrewJoinPersistenceService uncertain = spy(persistence);
		doAnswer(invocation -> {
			invocation.callRealMethod();
			throw new IllegalStateException("commit response lost model");
		}).when(uncertain).persist(any(), any(), any());
		worker = new CrewJoinPendingWorker(queue, uncertain, mapper, "p3", RUN_ID);
		failRun(raw("member"));
		assertStoredMembership(CONFIRMED_AT);
		verify(uncertain, times(1)).persist(any(), any(), any());
	}

	@Test
	void p3t9_malformedPreservesRawAndNextPending() {
		failRun("{malformed-payload");
		verify(repository, never()).insertMemberIfAbsent(any());
		assertUnchangedDb();
	}

	@Test
	void p3t11_http201ThenDbVisibilityAfterWorkerCommit() throws Exception {
		initializeRedis();
		var response = authPost("member", "/crews/" + crew.getId() + "/join", Map.of());
		assertThat(response.statusCode()).isEqualTo(201);
		LocalDateTime joinedAt = LocalDateTime.parse(response.jsonPath().getString("data.joinedAt"));
		assertThat(pending()).hasSize(1);
		assertUnchangedDb();
		assertThat(authGet("member", "/crews/" + crew.getId()).statusCode()).isEqualTo(403);
		assertThat(authGet("member", "/crews").jsonPath().getList("data.id", String.class)).isEmpty();
		worker.start();
		awaitCommittedAndAcked();
		var detail = authGet("member", "/crews/" + crew.getId());
		assertThat(detail.statusCode()).isEqualTo(200);
		assertThat(detail.jsonPath().getInt("data.currentMembers")).isEqualTo(2);
		assertThat(authGet("member", "/crews").jsonPath().getList("data.id", String.class))
			.containsExactly(crew.getId());
		assertStoredMembership(joinedAt);
	}

	private void initializeRedis() throws Exception {
		assertThat(redis.execute(INIT_SCRIPT,
			List.of(PREFIX + ":crew:" + crew.getId() + ":members", PREFIX + ":crew:" + crew.getId() + ":meta"),
			"5", mapper.writeValueAsString(List.of("leader")))).isEqualTo("OK");
	}

	private void failRun(String raw) {
		push(raw);
		push(raw("next"));
		worker.start();
		awaitStopped();
		assertThat(processing()).containsExactly(raw);
		assertThat(pending()).containsExactly(raw("next"));
		verify(queue, times(1)).claimRaw(any());
		verify(queue, never()).ackRaw(any());
	}

	private void assertStoredMembership(LocalDateTime joinedAt) {
		assertThat(count()).isEqualTo(2);
		List<CrewMember> members = repository.findById(crew.getId()).orElseThrow().getMembers();
		assertThat(members).hasSize(2);
		CrewMember member = members.stream().filter(value -> value.getUserId().equals("member"))
			.findFirst().orElseThrow();
		assertThat(member.getId()).startsWith("CRMB");
		assertThat(member.getRole()).isEqualTo(CrewRole.MEMBER);
		assertThat(member.getJoinedAt()).isEqualTo(joinedAt);
	}

	private void assertUnchangedDb() {
		assertThat(count()).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM crew_members WHERE user_id <> 'leader'", Integer.class))
			.isZero();
	}

	private int count() {
		return jdbc.queryForObject("SELECT current_members FROM crews WHERE id = ?", Integer.class, crew.getId());
	}

	private int memberRows() {
		return jdbc.queryForObject("SELECT count(*) FROM crew_members WHERE crew_id = ? AND user_id = 'member'",
			Integer.class, crew.getId());
	}

	private void awaitCommittedAndAcked() {
		await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
			assertThat(count()).isEqualTo(2);
			assertThat(pending()).isEmpty();
			assertThat(processing()).isEmpty();
		});
	}

	private void awaitStopped() {
		await().atMost(Duration.ofSeconds(5)).until(() -> !worker.isRunning());
	}

	private void push(String raw) {
		redis.opsForList().leftPush(PREFIX + ":pending", raw);
	}

	private List<String> pending() {
		return redis.opsForList().range(PREFIX + ":pending", 0, -1);
	}

	private List<String> processing() {
		return redis.opsForList().range(PREFIX + ":processing", 0, -1);
	}

	private String raw(String userId) {
		return "{ \"userId\":\"" + userId + "\",\"confirmedAt\":\"2026-01-02T03:04:05.678+09:00\","
			+ "\"crewId\":\"" + crew.getId() + "\"}";
	}
}
