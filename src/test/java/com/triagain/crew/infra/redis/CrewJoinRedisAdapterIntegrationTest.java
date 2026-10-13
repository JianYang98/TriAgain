package com.triagain.crew.infra.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.connection.DataType;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.triagain.crew.application.CrewJoinPendingWorker;
import com.triagain.crew.application.CrewJoinPersistenceService;
import com.triagain.crew.port.out.CrewJoinRedisPort.Approval;
import com.triagain.crew.port.out.CrewJoinRedisPort.Status;
import com.triagain.crew.port.out.CrewJoinWorkQueuePort;

/** 승인 Lua + Adapter, 복구 LMOVE — 실제 Redis로 승인·payload 형식·방어·인프라 실패와 startup recovery FIFO를 검증 */
@Tag("e2e")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CrewJoinRedisAdapterIntegrationTest {

	private static final RedisScript<String> INIT_SCRIPT =
		RedisScript.of(new ClassPathResource("redis/crew-join/initialize-crew-join.lua"), String.class);
	@SuppressWarnings({"unchecked", "rawtypes"})
	private static final RedisScript<List<Object>> APPROVE_SCRIPT =
		(RedisScript)RedisScript.of(new ClassPathResource("redis/crew-join/approve.lua"), List.class);
	private static final OffsetDateTime CONFIRMED_AT = OffsetDateTime.parse("2026-09-27T14:05:23.481+09:00");
	private static final String CONFIRMED_AT_TEXT = "2026-09-27T14:05:23.481+09:00";
	private static final String CREW = "CREW-1";

	private final ObjectMapper objectMapper = new ObjectMapper();
	private LettuceConnectionFactory connectionFactory;
	private StringRedisTemplate redis;
	private String runId;
	private CrewJoinRedisAdapter adapter;

	@BeforeAll
	void setUpRedis() {
		connectionFactory = new LettuceConnectionFactory(
			new RedisStandaloneConfiguration(RedisTestContainer.getHost(), RedisTestContainer.getPort()));
		connectionFactory.afterPropertiesSet();
		redis = new StringRedisTemplate(connectionFactory);
	}

	@AfterAll
	void tearDown() {
		connectionFactory.destroy();
	}

	@BeforeEach
	void newRun() {
		runId = "run-" + UUID.randomUUID();
		adapter = new CrewJoinRedisAdapter(redis, new CrewJoinRedisProperties("test", runId), objectMapper);
	}

	@Test
	@DisplayName("초기 N=2에서 신규 승인하면 score·seq·인원이 3이고 pending에 정확히 3개 String 필드 payload 1건이 쌓인다")
	void approve_newMember_recordsSeqMemberAndPending() throws Exception {
		// Given: 0밀리초와 JSON 특수문자(따옴표·백슬래시·유니코드) userId
		initCrew(3, "u-a", "u-leader");
		String userId = "u-\"quote\\back-한글";
		OffsetDateTime zeroMillis = OffsetDateTime.parse("2026-09-27T14:05:23+09:00");

		// When
		Approval approval = adapter.approve(CREW, userId, zeroMillis);

		// Then
		assertThat(approval).isEqualTo(new Approval(Status.JOIN_SUCCESS, 3));
		assertThat(redis.opsForZSet().score(key("members"), userId)).isEqualTo(3.0);
		assertThat(redis.opsForHash().get(key("meta"), "seq")).isEqualTo("3");
		List<String> pending = redis.opsForList().range(pendingKey(), 0, -1);
		assertThat(pending).hasSize(1);
		Map<String, Object> payload = objectMapper.readValue(pending.get(0), new TypeReference<>() { });
		assertThat(payload).containsExactlyInAnyOrderEntriesOf(Map.of(
			"crewId", CREW, "userId", userId, "confirmedAt", "2026-09-27T14:05:23.000+09:00"));
	}

	@Test
	@DisplayName("기존 멤버는 ALREADY_JOINED, 정원이 찬 크루는 CREW_FULL이고 두 경우 모두 어떤 key도 바뀌지 않는다")
	void approve_duplicateOrFull_changesNothing() {
		// Given: 정원 2에 2명 (가득 참)
		initCrew(2, "u-a", "u-b");
		Map<String, Object> before = snapshot();

		// When & Then: 가득 찬 상태의 기존 멤버도 중복이 먼저다
		assertThat(adapter.approve(CREW, "u-a", CONFIRMED_AT)).isEqualTo(new Approval(Status.ALREADY_JOINED, 0));
		assertThat(adapter.approve(CREW, "u-new", CONFIRMED_AT)).isEqualTo(new Approval(Status.CREW_FULL, 0));
		assertThat(snapshot()).isEqualTo(before);
	}

	@Test
	@DisplayName("초기화되지 않은 크루는 NOT_INITIALIZED이고 key를 만들지 않는다")
	void approve_notInitialized_createsNothing() {
		// When
		Approval approval = adapter.approve(CREW, "u-new", CONFIRMED_AT);

		// Then
		assertThat(approval).isEqualTo(new Approval(Status.NOT_INITIALIZED, 0));
		assertThat(redis.keys("triagain:crew-join:{test:" + runId + "}:*")).isEmpty();
	}

	@ParameterizedTest(name = "{0}")
	@ValueSource(strings = {
		"pending-wrongtype", "seq-not-number", "seq-not-zcard", "top-score-not-seq",
		"initialized-missing", "members-missing", "meta-missing"})
	@DisplayName("손상된 상태는 쓰기 전에 INVALID_STATE로 거절하고 모든 key가 그대로다")
	void approve_corruptedState_rejectedBeforeWrite(String corruption) {
		// Given
		initCrew(5, "u-a", "u-b");
		corrupt(corruption);
		Map<String, Object> before = snapshot();

		// When
		Approval approval = adapter.approve(CREW, "u-new", CONFIRMED_AT);

		// Then
		assertThat(approval).isEqualTo(new Approval(Status.INVALID_STATE, 0));
		assertThat(snapshot()).isEqualTo(before);
	}

	@ParameterizedTest(name = "{0}")
	@ValueSource(strings = {
		"not-json",
		"[\"CREW-1\",\"u-new\",\"2026-09-27T14:05:23.481+09:00\"]",
		"{\"crewId\":\"CREW-1\",\"userId\":\"u-new\"}",
		"{\"crewId\":\"CREW-1\",\"userId\":\"u-other\",\"confirmedAt\":\"2026-09-27T14:05:23.481+09:00\"}",
		"{\"crewId\":\"CREW-1\",\"userId\":\"u-new\",\"confirmedAt\":\"2026-09-27T14:05:23.481+09:00\",\"seq\":\"3\"}",
		"{\"crewId\":\"CREW-1\",\"userId\":\"u-new\",\"confirmedAt\":1}"})
	@DisplayName("payload가 JSON이 아니거나 세 String 필드·ARGV와 일치하지 않으면 쓰기 전에 INVALID_STATE다")
	void approveScript_invalidPayload_rejectedBeforeWrite(String payload) {
		// Given
		initCrew(5, "u-a");
		Map<String, Object> before = snapshot();

		// When
		List<Object> reply = redis.execute(APPROVE_SCRIPT, List.of(key("members"), key("meta"), pendingKey()),
			CREW, "u-new", CONFIRMED_AT_TEXT, payload);

		// Then
		assertThat(reply).containsExactly(4L, 0L);
		assertThat(snapshot()).isEqualTo(before);
	}

	@Test
	@DisplayName("다른 크루의 key를 섞어 보내면 INVALID_STATE이고 아무것도 쓰지 않는다")
	void approveScript_mismatchedKeys_rejected() {
		// Given
		initCrew(5, "u-a");
		Map<String, Object> before = snapshot();
		String payload = "{\"crewId\":\"CREW-2\",\"userId\":\"u-new\",\"confirmedAt\":\"" + CONFIRMED_AT_TEXT + "\"}";

		// When
		List<Object> reply = redis.execute(APPROVE_SCRIPT, List.of(key("members"), key("meta"), pendingKey()),
			"CREW-2", "u-new", CONFIRMED_AT_TEXT, payload);

		// Then
		assertThat(reply).containsExactly(4L, 0L);
		assertThat(snapshot()).isEqualTo(before);
	}

	@Test
	@DisplayName("Redis에 연결할 수 없으면 Status가 아니라 CONNECTION IllegalStateException이다")
	void approve_connectionFailure_throws() {
		// Given
		LettuceConnectionFactory closed =
			new LettuceConnectionFactory(new RedisStandaloneConfiguration("localhost", 1));
		closed.afterPropertiesSet();
		CrewJoinRedisAdapter unreachable = new CrewJoinRedisAdapter(
			new StringRedisTemplate(closed), new CrewJoinRedisProperties("test", runId), objectMapper);

		// When & Then
		try {
			assertThatThrownBy(() -> unreachable.approve(CREW, "u-new", CONFIRMED_AT))
				.isExactlyInstanceOf(IllegalStateException.class)
				.hasMessageStartingWith("CONNECTION")
				.hasMessageContaining("namespace=test, runId=" + runId + ", crewId=" + CREW)
				.hasCauseInstanceOf(RedisConnectionFailureException.class);
		} finally {
			closed.destroy();
		}
	}

	@Test
	@DisplayName("Redis가 스크립트 쓰기를 거부하면(OOM) Status가 아니라 SCRIPT_ERROR IllegalStateException이다")
	void approve_redisErrorReply_throws() {
		// Given: noeviction + maxmemory 1바이트 → 첫 쓰기 명령(HINCRBY)에서 OOM error reply
		initCrew(5, "u-a");
		Map<String, Object> before = snapshot();
		configSet("maxmemory", "1");

		// When & Then
		try {
			assertThatThrownBy(() -> adapter.approve(CREW, "u-new", CONFIRMED_AT))
				.isExactlyInstanceOf(IllegalStateException.class)
				.hasMessageStartingWith("SCRIPT_ERROR")
				.hasMessageContaining("runId=" + runId)
				.rootCause().hasMessageContaining("OOM");
		} finally {
			configSet("maxmemory", "0");
		}
		assertThat(snapshot()).isEqualTo(before);
	}

	@Test
	@DisplayName("offset이 +09:00이 아닌 confirmedAt은 Z 등으로 직렬화하지 않고 거부한다")
	void approve_nonSeoulOffset_rejected() {
		// Given
		initCrew(5, "u-a");

		// When & Then
		assertThatThrownBy(() -> adapter.approve(CREW, "u-new", OffsetDateTime.parse("2026-09-27T05:05:23.481Z")))
			.isExactlyInstanceOf(IllegalStateException.class)
			.hasMessageStartingWith("SERIALIZATION");
		assertThat(redis.opsForHash().get(key("meta"), "seq")).isEqualTo("1");
	}

	@Test
	@DisplayName("순차 승인 A→B→C의 pending은 왼쪽부터 C,B,A이고 오른쪽 기준(향후 RIGHT 소비 순서)으로 A,B,C다")
	void pending_isLpushFifoLayout() throws Exception {
		// Given
		initCrew(5, "u-leader");

		// When
		for (String user : List.of("u-A", "u-B", "u-C")) {
			assertThat(adapter.approve(CREW, user, CONFIRMED_AT).status()).isEqualTo(Status.JOIN_SUCCESS);
		}

		// Then: 소비 명령 없이 LRANGE로만 본다
		List<String> leftToRight = new ArrayList<>();
		for (String raw : redis.opsForList().range(pendingKey(), 0, -1)) {
			leftToRight.add((String)objectMapper.readValue(raw, Map.class).get("userId"));
		}
		assertThat(leftToRight).containsExactly("u-C", "u-B", "u-A");
		List<String> rightToLeft = new ArrayList<>(leftToRight);
		Collections.reverse(rightToLeft);
		assertThat(rightToLeft).containsExactly("u-A", "u-B", "u-C");
	}

	@Test
	@DisplayName("초기 N이 다른 크루 3개를 한 run에서 승인하면 crew별 payload 수=K, run LLEN=ΣK, 예상 밖 crew가 없다")
	void sharedPending_perCrewCountsAndRunTotal() {
		// Given: N=1·2·3
		Map<String, List<String>> initial = Map.of(
			"CREW-A", List.of("a-1"),
			"CREW-B", List.of("b-1", "b-2"),
			"CREW-C", List.of("c-1", "c-2", "c-3"));
		initial.forEach((crewId, users) -> initCrew(crewId, 6, users.toArray(String[]::new)));
		Map<String, Integer> joins = Map.of("CREW-A", 3, "CREW-B", 1, "CREW-C", 2);

		// When: 크루를 번갈아 승인
		for (int i = 0; i < 3; i++) {
			for (Map.Entry<String, Integer> entry : joins.entrySet()) {
				if (i < entry.getValue()) {
					adapter.approve(entry.getKey(), entry.getKey() + "-new-" + i, CONFIRMED_AT);
				}
			}
		}

		// Then
		for (Map.Entry<String, List<String>> entry : initial.entrySet()) {
			assertThat(CrewJoinRunConsistency.assertCrew(redis, prefix(), entry.getKey(), entry.getValue()))
				.hasSize(joins.get(entry.getKey()));
		}
		CrewJoinRunConsistency.assertNoUnexpectedCrew(redis, prefix(), initial.keySet());
		assertThat(redis.opsForList().size(pendingKey())).isEqualTo(6L);
	}

	@Test
	@DisplayName("최종 정합성 검사는 정상 run에서 통과하고, pending 한 건을 지우면 실패하며 자동으로 되살리지 않는다")
	void consistencyCheck_detectsLostPending() {
		// Given: 정상 run — N=2, 신규 3명
		List<String> initialMembers = List.of("u-a", "u-b");
		initCrew(6, initialMembers.toArray(String[]::new));
		for (String user : List.of("u-x", "u-y", "u-z")) {
			adapter.approve(CREW, user, CONFIRMED_AT);
		}
		assertThat(CrewJoinRunConsistency.assertCrew(redis, prefix(), CREW, initialMembers))
			.containsExactly("u-x", "u-y", "u-z");

		// When: 테스트에서 pending 한 건 유실을 만든다 (members에는 남아 있음)
		String lost = redis.opsForList().index(pendingKey(), 0);
		assertThat(redis.opsForList().remove(pendingKey(), 1, lost)).isEqualTo(1L);

		// Then: 같은 검사가 실패하고, 유실분은 재생성되지 않는다
		assertThatThrownBy(() -> CrewJoinRunConsistency.assertCrew(redis, prefix(), CREW, initialMembers))
			.isInstanceOf(AssertionError.class)
			.hasMessageContaining("pending 건수 = K");
		assertThat(redis.opsForList().size(pendingKey())).isEqualTo(2L);
		assertThat(adapter.approve(CREW, "u-x", CONFIRMED_AT)).isEqualTo(new Approval(Status.ALREADY_JOINED, 0));
		assertThat(redis.opsForList().size(pendingKey())).isEqualTo(2L);
	}

	@Test
	@DisplayName("명령 timeout은 제한시간 안에 TIMEOUT_OR_UNKNOWN으로 끝나고, 결과는 미확정이다(서버가 뒤늦게 적용할 수 있다)")
	void approve_commandTimeout_resultUnknown() throws Exception {
		// Given: command timeout 500ms 전용 연결 + 다른 연결에서 쓰기 명령을 1500ms 멈춘다
		initCrew(5, "u-a");
		LettuceConnectionFactory shortTimeout = new LettuceConnectionFactory(
			new RedisStandaloneConfiguration(RedisTestContainer.getHost(), RedisTestContainer.getPort()),
			LettuceClientConfiguration.builder().commandTimeout(Duration.ofMillis(500)).build());
		shortTimeout.afterPropertiesSet();
		CrewJoinRedisAdapter slow = new CrewJoinRedisAdapter(
			new StringRedisTemplate(shortTimeout), new CrewJoinRedisProperties("test", runId), objectMapper);
		// 스크립트 캐시를 미리 채운다 — 비어 있으면 pause 해제 후 EVALSHA가 NOSCRIPT로 끝나 적용 여부가 실행 순서에 좌우된다
		assertThat(slow.approve("CREW-WARM", "u-warm", CONFIRMED_AT).status()).isEqualTo(Status.NOT_INITIALIZED);
		redis.execute((RedisCallback<Object>)connection ->
			connection.execute("CLIENT", "PAUSE".getBytes(), "1500".getBytes(), "WRITE".getBytes()));

		// When & Then
		try {
			long started = System.nanoTime();
			assertThatThrownBy(() -> slow.approve(CREW, "u-late", CONFIRMED_AT))
				.isExactlyInstanceOf(IllegalStateException.class)
				.hasMessageStartingWith("TIMEOUT_OR_UNKNOWN");
			assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofMillis(1500));
			// pause가 풀리면 서버는 이미 받은 스크립트를 실행한다 → timeout은 "미가입"의 증거가 아니다
			// (연결을 먼저 닫으면 서버가 대기 명령을 버리므로 확인 뒤에 닫는다)
			assertThat(waitForMember("u-late")).isTrue();
			assertThat(redis.opsForList().size(pendingKey())).isEqualTo(1L);
		} finally {
			shortTimeout.destroy();
		}
	}

	@Test
	@DisplayName("P4-T1 processing이 없으면 LMOVE는 정상 nil이고 빈 LIST를 만들지 않으며 기존 pending은 그대로다")
	void recover_absentProcessing_returnsNilWithoutCreatingKey() throws Exception {
		// Given
		redis.opsForList().rightPush(pendingKey(), payload("u-d"));
		try (CrewJoinWorkQueueAdapter queue = workQueue()) {
			// When
			String reply = queue.recoverOneRaw();
			// Then
			assertThat(reply).isNull();
			assertThat(redis.hasKey(processingKey())).isFalse();
			assertThat(redis.opsForList().range(pendingKey(), 0, -1)).containsExactly(payload("u-d"));
		}
	}

	@Test
	@DisplayName("P4-T2 복구한 raw는 필드 순서·공백·이스케이프·유니코드까지 바이트 그대로 pending 오른쪽에 붙는다")
	void recover_oneRaw_preservesExactBytes() throws Exception {
		// Given — 재직렬화하면 달라지는 원문
		String raw = "{ \"userId\" : \"u-\\\"q\\\\한글\" ,\"crewId\":\"CREW-1\",  "
			+ "\"confirmedAt\":\"2026-09-27T14:05:23.481+09:00\" }\t ";
		redis.opsForList().rightPush(processingKey(), raw);
		redis.opsForList().rightPush(pendingKey(), payload("u-e"));
		try (CrewJoinWorkQueueAdapter queue = workQueue()) {
			// When
			assertThat(queue.recoverOneRaw()).isEqualTo(raw);
			assertThat(queue.recoverOneRaw()).isNull();
		}
		// Then
		byte[] stored = redis.execute((RedisCallback<byte[]>)connection ->
			connection.listCommands().lIndex(pendingKey().getBytes(StandardCharsets.UTF_8), -1));
		assertThat(stored).isEqualTo(raw.getBytes(StandardCharsets.UTF_8));
		assertThat(redis.opsForList().range(pendingKey(), 0, -1)).containsExactly(payload("u-e"), raw);
		assertThat(redis.hasKey(processingKey())).isFalse();
	}

	@Test
	@DisplayName("P4-T3 [C,B,A]/[E,D] 복구는 [E,D,C,B,A]이고 중간에 LPUSH된 F까지 A→F 순으로 claim된다")
	void recover_multipleRemainders_preservesFifoWithInterleavedApproval() throws Exception {
		// Given — 배열은 모두 LEFT→RIGHT. processing의 C가 가장 최근 claim이다.
		initCrew(10, "u-leader");
		redis.opsForList().rightPushAll(processingKey(), payload("C"), payload("B"), payload("A"));
		redis.opsForList().rightPushAll(pendingKey(), payload("E"), payload("D"));
		try (CrewJoinWorkQueueAdapter queue = workQueue()) {
			// When — C 이동 직후 새 승인 F가 LPUSH된다.
			assertThat(queue.recoverOneRaw()).isEqualTo(payload("C"));
			assertThat(users(processingKey())).containsExactly("B", "A");
			assertThat(users(pendingKey())).containsExactly("E", "D", "C");
			assertThat(adapter.approve(CREW, "F", CONFIRMED_AT).status()).isEqualTo(Status.JOIN_SUCCESS);
			assertThat(queue.recoverOneRaw()).isEqualTo(payload("B"));
			assertThat(queue.recoverOneRaw()).isEqualTo(payload("A"));
			assertThat(queue.recoverOneRaw()).isNull();
			// Then
			assertThat(users(pendingKey())).containsExactly("F", "E", "D", "C", "B", "A");
			List<String> consumed = new ArrayList<>();
			for (int i = 0; i < 6; i++) {
				consumed.add(userOf(queue.claimRaw(Duration.ofSeconds(1))));
			}
			assertThat(consumed).containsExactly("A", "B", "C", "D", "E", "F");
		}
	}

	@Test
	@DisplayName("P4-T14 pending이 WRONGTYPE이면 LMOVE 오류를 nil로 숨기지 않고 두 key를 고치지 않으며 worker는 시작하지 않는다")
	void recover_wrongTypeDestination_throwsAndWorkerDoesNotStart() throws Exception {
		// Given
		redis.opsForList().rightPush(processingKey(), payload("A"));
		redis.opsForValue().set(pendingKey(), "corrupted");
		try (CrewJoinWorkQueueAdapter queue = workQueue()) {
			// When & Then — 오류 응답
			assertThatThrownBy(queue::recoverOneRaw).isInstanceOf(RedisSystemException.class)
				.rootCause().hasMessageContaining("WRONGTYPE");
			assertThat(redis.opsForList().range(processingKey(), 0, -1)).containsExactly(payload("A"));
			assertThat(redis.opsForValue().get(pendingKey())).isEqualTo("corrupted");
			RecordingWorkQueue probe = new RecordingWorkQueue(queue);
			CrewJoinPendingWorker worker = worker(probe);
			worker.start();
			assertThat(worker.isRunning()).isFalse();
			assertThat(probe.claimAttempts()).isZero();
			// 한계 기록 — source가 없으면 Redis는 destination type 검사 전에 nil을 반환한다(7.4.11).
			redis.delete(processingKey());
			assertThat(queue.recoverOneRaw()).isNull();
		}
	}

	@Test
	@DisplayName("P4-T6 C 이동 뒤 다음 recovery가 전송 전에 실패하면 [B,A]/[E,D,C]로 남고 claim·DB·ACK·추가 recovery가 없다")
	void recover_partialFailureBeforeSend_leavesStateAndNoConsumption() throws Exception {
		// Given
		redis.opsForList().rightPushAll(processingKey(), payload("C"), payload("B"), payload("A"));
		redis.opsForList().rightPushAll(pendingKey(), payload("E"), payload("D"));
		CrewJoinPersistenceService persistence = mock(CrewJoinPersistenceService.class);
		try (RecordingWorkQueue probe = new RecordingWorkQueue(workQueue()).failRecoverAtCall(2)) {
			CrewJoinPendingWorker worker = new CrewJoinPendingWorker(probe, persistence, objectMapper, "test", runId);
			// When
			worker.start();
			worker.start();
			// Then
			assertThat(worker.isRunning()).isFalse();
			assertThat(users(processingKey())).containsExactly("B", "A");
			assertThat(users(pendingKey())).containsExactly("E", "D", "C");
			assertThat(probe.recoverCalls()).isEqualTo(2);
			assertThat(probe.claimAttempts()).isZero();
			assertThat(probe.ackCalls()).isZero();
			verifyNoInteractions(persistence);
		}
	}

	@Test
	@DisplayName("P4-T11 마지막 nil 응답을 반환하기 직전까지 claim 시도·DB·ACK가 0이고, 반환 뒤에만 A→E로 소비한다")
	void recover_noClaimUntilFinalNilReturned() throws Exception {
		// Given — 실제 LMOVE 응답 경계마다 그 시점의 claim 시도 수를 기록하고, nil 응답은 반환 전에 붙잡는다.
		redis.opsForList().rightPushAll(processingKey(), payload("C"), payload("B"), payload("A"));
		redis.opsForList().rightPushAll(pendingKey(), payload("E"), payload("D"));
		CrewJoinPersistenceService persistence = mock(CrewJoinPersistenceService.class);
		when(persistence.persist(any(), any(), any())).thenReturn(1);
		CountDownLatch atNil = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		List<Integer> claimsAtReply = new CopyOnWriteArrayList<>();
		ExecutorService starter = Executors.newSingleThreadExecutor();
		try (RecordingWorkQueue probe = new RecordingWorkQueue(workQueue())) {
			probe.onRecoverReply(reply -> holdNil(reply, probe, claimsAtReply, atNil, release));
			CrewJoinPendingWorker worker = new CrewJoinPendingWorker(probe, persistence, objectMapper, "test", runId);
			try {
				// When
				Future<?> started = starter.submit(worker::start);
				assertThat(atNil.await(5, TimeUnit.SECONDS)).isTrue();
				// Then — 복구 완료 확인 직전: 소비 경로 호출 0, Redis는 이미 [E,D,C,B,A]
				assertThat(claimsAtReply).containsExactly(0, 0, 0, 0);
				await().during(Duration.ofMillis(300)).atMost(Duration.ofSeconds(1))
					.until(() -> probe.claimAttempts() == 0 && probe.ackCalls() == 0);
				verifyNoInteractions(persistence);
				assertThat(users(pendingKey())).containsExactly("E", "D", "C", "B", "A");
				release.countDown();
				started.get(5, TimeUnit.SECONDS);
				await().atMost(Duration.ofSeconds(8)).until(() -> probe.ackCalls() == 5);
				assertThat(probe.claimed().stream().map(this::userOf)).containsExactly("A", "B", "C", "D", "E");
			} finally {
				release.countDown();
				worker.stop();
			}
		} finally {
			starter.shutdownNow();
		}
	}

	private static void holdNil(String reply, RecordingWorkQueue probe, List<Integer> claimsAtReply,
		CountDownLatch atNil, CountDownLatch release) {
		claimsAtReply.add(probe.claimAttempts());
		if (reply == null) {
			atNil.countDown();
			try {
				assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
			} catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
			}
		}
	}

	private CrewJoinWorkQueueAdapter workQueue() {
		return new CrewJoinWorkQueueAdapter(connectionFactory, new CrewJoinRedisProperties("test", runId));
	}

	private CrewJoinPendingWorker worker(CrewJoinWorkQueuePort queue) {
		return new CrewJoinPendingWorker(queue, mock(CrewJoinPersistenceService.class), objectMapper, "test", runId);
	}

	private List<String> users(String listKey) {
		return redis.opsForList().range(listKey, 0, -1).stream().map(this::userOf).toList();
	}

	private String userOf(String raw) {
		try {
			return objectMapper.readTree(raw).get("userId").asText();
		} catch (com.fasterxml.jackson.core.JsonProcessingException e) {
			throw new IllegalStateException(e);
		}
	}

	private static String payload(String user) {
		return "{\"crewId\":\"" + CREW + "\",\"userId\":\"" + user
			+ "\",\"confirmedAt\":\"" + CONFIRMED_AT_TEXT + "\"}";
	}

	private String processingKey() {
		return prefix() + ":processing";
	}

	private boolean waitForMember(String userId) throws InterruptedException {
		for (int i = 0; i < 40; i++) {
			if (redis.opsForZSet().score(key("members"), userId) != null) {
				return true;
			}
			Thread.sleep(100);
		}
		return false;
	}

	private void initCrew(int capacity, String... sortedUsers) {
		initCrew(CREW, capacity, sortedUsers);
	}

	private void initCrew(String crewId, int capacity, String... sortedUsers) {
		try {
			String users = objectMapper.writeValueAsString(sortedUsers);
			List<String> keys = List.of(crewKey(crewId, "members"), crewKey(crewId, "meta"));
			assertThat(redis.execute(INIT_SCRIPT, keys, String.valueOf(capacity), users))
				.isEqualTo("OK");
		} catch (com.fasterxml.jackson.core.JsonProcessingException e) {
			throw new IllegalStateException(e);
		}
	}

	private void corrupt(String corruption) {
		switch (corruption) {
			case "pending-wrongtype" -> redis.opsForValue().set(pendingKey(), "x");
			case "seq-not-number" -> redis.opsForHash().put(key("meta"), "seq", "abc");
			case "seq-not-zcard" -> redis.opsForHash().put(key("meta"), "seq", "3");
			case "top-score-not-seq" -> redis.opsForZSet().add(key("members"), "u-b", 7);
			case "initialized-missing" -> redis.opsForHash().delete(key("meta"), "initialized");
			case "members-missing" -> redis.delete(key("members"));
			case "meta-missing" -> redis.delete(key("meta"));
			default -> throw new IllegalArgumentException(corruption);
		}
	}

	private Map<String, Object> snapshot() {
		DataType pendingType = redis.type(pendingKey());
		Object pending = switch (pendingType) {
			case LIST -> redis.opsForList().range(pendingKey(), 0, -1);
			case STRING -> redis.opsForValue().get(pendingKey());
			default -> pendingType.code();
		};
		return Map.of(
			"members", String.valueOf(redis.opsForZSet().rangeWithScores(key("members"), 0, -1).stream()
				.map(t -> t.getValue() + "=" + t.getScore()).toList()),
			"meta", redis.<String, String>opsForHash().entries(key("meta")),
			"pending", pending);
	}

	private void configSet(String name, String value) {
		redis.execute((RedisCallback<Void>)connection -> {
			connection.serverCommands().setConfig(name, value);
			return null;
		});
	}

	private String key(String suffix) {
		return crewKey(CREW, suffix);
	}

	private String crewKey(String crewId, String suffix) {
		return prefix() + ":crew:" + crewId + ":" + suffix;
	}

	private String prefix() {
		return "triagain:crew-join:{test:" + runId + "}";
	}

	private String pendingKey() {
		return prefix() + ":pending";
	}
}
