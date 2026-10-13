package com.triagain.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.triagain.common.util.IdGenerator;
import com.triagain.crew.application.CrewJoinPendingWorker;
import com.triagain.crew.domain.model.Crew;
import com.triagain.crew.domain.model.CrewMember;
import com.triagain.crew.domain.vo.CrewStatus;
import com.triagain.crew.domain.vo.CrewVisibility;
import com.triagain.crew.domain.vo.VerificationType;
import com.triagain.crew.infra.redis.CrewJoinRunConsistency;
import com.triagain.crew.infra.redis.RedisTestContainer;
import com.triagain.crew.port.out.CrewJoinRedisPort;

import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;

/**
 * REDIS_ASYNC 공개 가입 — 실제 HTTP·인증·예외 Handler·Redis·PostgreSQL 단일 컨텍스트.
 * Phase 2는 DB에 쓰지 않으므로 성공 후에도 DB 멤버·인원·version은 초기값이다.
 */
class RedisCrewJoinApiTest extends E2eTestBase {

	/** context 시작 전에 worker를 대체하여 Phase 2 pending-only 단언 보존 */
	@MockitoBean
	private CrewJoinPendingWorker isolatedWorker;

	private static final String RUN_ID = "api-" + UUID.randomUUID();
	private static final String PREFIX = "triagain:crew-join:{api:" + RUN_ID + "}";
	private static final RedisScript<String> INIT_SCRIPT =
		RedisScript.of(new ClassPathResource("redis/crew-join/initialize-crew-join.lua"), String.class);

	@Autowired
	private StringRedisTemplate redis;

	@Autowired
	private ObjectMapper objectMapper;

	/** 기본은 실제 Adapter 호출. P2-T9에서만 응답 실패·연결 실패를 주입한다(테스트마다 자동 reset) */
	@MockitoSpyBean
	private CrewJoinRedisPort crewJoinRedisPort;

	@DynamicPropertySource
	static void redisAsync(DynamicPropertyRegistry registry) {
		// 부모의 DB·flyway 설정은 그대로, Redis 주소(부모는 닫힌 포트)와 전략·실험 key만 덮는다
		registry.add("spring.data.redis.host", RedisTestContainer::getHost);
		registry.add("spring.data.redis.port", RedisTestContainer::getPort);
		registry.add("triagain.crew.lock-strategy", () -> "REDIS_ASYNC");
		registry.add("triagain.crew.redis.namespace", () -> "api");
		registry.add("triagain.crew.redis.run-id", () -> RUN_ID);
	}

	@Test
	@DisplayName("준비된 크루에 가입하면 201·Redis 인원을 주고, joinedAt은 pending confirmedAt과 같은 서울 시각이며 DB는 그대로다")
	void join_success_recordsRedisOnly() throws Exception {
		// Given: 정원 3, 리더 1명(DB·Redis 동일)
		String leaderId = "leader-" + UUID.randomUUID();
		String userId = "user-" + UUID.randomUUID();
		createUser(leaderId);
		createUser(userId);
		Crew crew = savePublicCrew(leaderId, CrewStatus.RECRUITING, LocalDate.now().plusDays(20),
			CrewVisibility.PUBLIC);
		initRedis(crew, leaderId);

		// When
		ExtractableResponse<Response> response = join(userId, crew.getId());

		// Then: 응답
		assertThat(response.statusCode()).isEqualTo(201);
		assertThat(response.jsonPath().getString("data.userId")).isEqualTo(userId);
		assertThat(response.jsonPath().getString("data.crewId")).isEqualTo(crew.getId());
		assertThat(response.jsonPath().getString("data.role")).isEqualTo("MEMBER");
		assertThat(response.jsonPath().getInt("data.currentMembers")).isEqualTo(2);
		LocalDateTime joinedAt = LocalDateTime.parse(response.jsonPath().getString("data.joinedAt"));

		// Then: Redis — 신규 score 2, pending 정확히 1건, 같은 시각의 +09:00 밀리초 문자열
		assertThat(redis.opsForZSet().score(membersKey(crew), userId)).isEqualTo(2.0);
		List<Map<String, Object>> payloads = pendingFor(crew.getId());
		assertThat(payloads).hasSize(1);
		String confirmedAt = (String)payloads.get(0).get("confirmedAt");
		assertThat(confirmedAt).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}\\+09:00");
		assertThat(OffsetDateTime.parse(confirmedAt).toLocalDateTime()).isEqualTo(joinedAt);

		// Then: DB 무변경
		Crew stored = crewRepositoryPort.findById(crew.getId()).orElseThrow();
		assertThat(stored.getCurrentMembers()).isEqualTo(1);
		assertThat(stored.getVersion()).isEqualTo(crew.getVersion());
		assertThat(stored.getMembers()).extracting(CrewMember::getUserId).containsExactly(leaderId);
	}

	@Test
	@DisplayName("크루 없음·비공개·가입 불가 상태·마감 경과는 Redis 전에 기존 코드로 거절한다")
	void join_preChecks_existingErrorCodes() {
		// Given
		String leaderId = "leader-" + UUID.randomUUID();
		createUser(leaderId);
		Crew privateCrew = savePublicCrew(leaderId, CrewStatus.RECRUITING, LocalDate.now().plusDays(20),
			CrewVisibility.PRIVATE);
		Crew completed = savePublicCrew(leaderId, CrewStatus.COMPLETED, LocalDate.now().plusDays(20),
			CrewVisibility.PUBLIC);
		Crew deadlinePassed = savePublicCrew(leaderId, CrewStatus.RECRUITING, LocalDate.now().plusDays(2),
			CrewVisibility.PUBLIC);
		Map<String, String> before = redisSnapshot();

		// When & Then
		assertError(join("user-x", "CREW-NOT-EXIST"), 404, "CR001");
		assertError(join("user-x", privateCrew.getId()), 400, "CR022");
		assertError(join("user-x", completed.getId()), 400, "CR003");
		assertError(join("user-x", deadlinePassed.getId()), 400, "CR008");
		assertThat(redisSnapshot()).isEqualTo(before);
	}

	@Test
	@DisplayName("유효한 초대코드 가입은 REDIS_ASYNC에서 500/C002이고 Redis·DB 모두 바뀌지 않는다")
	void joinByInviteCode_rejected() throws Exception {
		// Given
		String leaderId = "leader-" + UUID.randomUUID();
		String userId = "user-" + UUID.randomUUID();
		createUser(leaderId);
		createUser(userId);
		Crew crew = savePublicCrew(leaderId, CrewStatus.RECRUITING, LocalDate.now().plusDays(20),
			CrewVisibility.PUBLIC);
		initRedis(crew, leaderId);
		Map<String, String> before = redisSnapshot();

		// When
		ExtractableResponse<Response> response = authPost(userId, "/crews/join",
			Map.of("inviteCode", crew.getInviteCode()));

		// Then
		assertError(response, 500, "C002");
		assertThat(redisSnapshot()).isEqualTo(before);
		Crew stored = crewRepositoryPort.findById(crew.getId()).orElseThrow();
		assertThat(stored.getCurrentMembers()).isEqualTo(1);
		assertThat(stored.getMembers()).hasSize(1);
	}

	@Test
	@DisplayName("Redis 준비가 안 된 크루는 해당 가입만 500/C002이고 앱 health는 200 UP이다")
	void join_notInitialized_onlyThatRequestFails() {
		// Given
		String leaderId = "leader-" + UUID.randomUUID();
		createUser(leaderId);
		Crew crew = savePublicCrew(leaderId, CrewStatus.RECRUITING, LocalDate.now().plusDays(20),
			CrewVisibility.PUBLIC);
		Map<String, String> before = redisSnapshot();

		// When
		ExtractableResponse<Response> response = join("user-x", crew.getId());

		// Then: 이 크루 key는 만들지 않고, 같은 run의 다른 key(pending 포함)도 그대로
		assertError(response, 500, "C002");
		assertThat(redisSnapshot()).isEqualTo(before);
		assertThat(redisKeys()).noneMatch(key -> key.contains(crew.getId()));
		ExtractableResponse<Response> health = givenRequest().when().get("/actuator/health").then().extract();
		assertThat(health.statusCode()).isEqualTo(200);
		assertThat(health.jsonPath().getString("status")).isEqualTo("UP");
	}

	@Test
	@DisplayName("P2-T4 정원 경합 — N=1·정원 5에 30명이 동시에 가입하면 정확히 4명만 201, 나머지는 CR002이고 정합성이 맞는다")
	void capacityRace_exactlyRemainingSeatsSucceed() throws Exception {
		// Given
		Crew crew = preparedCrew(5);
		List<String> users = newUsers(30);

		// When
		Map<String, ExtractableResponse<Response>> responses = concurrentJoin(crew.getId(), users);

		// Then: 모든 정상 응답을 모아 대조
		Set<String> succeeded = usersWithStatus(responses, 201);
		assertThat(succeeded).hasSize(4);
		assertThat(errorCodesExcept(responses, succeeded)).hasSize(26).containsOnly("409:CR002");
		assertThat(redis.opsForZSet().zCard(membersKey(crew))).isEqualTo(5L);
		List<String> newMembers = CrewJoinRunConsistency.assertCrew(redis, PREFIX, crew.getId(), initialOf(crew));
		assertThat(Set.copyOf(newMembers)).isEqualTo(succeeded);

		// Then: 경합 종료 뒤 FULL 단독 요청은 아무것도 바꾸지 않는다
		String outsider = newUsers(1).get(0);
		Map<String, String> before = redisSnapshot();
		assertError(join(outsider, crew.getId()), 409, "CR002");
		assertThat(redisSnapshot()).isEqualTo(before);
	}

	@Test
	@DisplayName("P2-T4 마지막 한 자리 — 남은 1자리에 10명이 동시에 가입하면 정확히 1명만 승인된다")
	void lastSeatRace_exactlyOneWins() throws Exception {
		// Given: 정원 2, 리더 1명 → 1자리
		Crew crew = preparedCrew(2);
		List<String> users = newUsers(10);

		// When
		Map<String, ExtractableResponse<Response>> responses = concurrentJoin(crew.getId(), users);

		// Then
		Set<String> succeeded = usersWithStatus(responses, 201);
		assertThat(succeeded).hasSize(1);
		assertThat(errorCodesExcept(responses, succeeded)).hasSize(9).containsOnly("409:CR002");
		assertThat(CrewJoinRunConsistency.assertCrew(redis, PREFIX, crew.getId(), initialOf(crew)))
			.containsExactlyElementsOf(succeeded);
	}

	@Test
	@DisplayName("P2-T5 중복 경합 — 같은 사용자가 동시에 10번 가입하면 201 1건·CR004 9건, pending 1건이고 이후 중복 요청은 상태를 바꾸지 않는다")
	void duplicateRace_onlyOneApproval() throws Exception {
		// Given: 정원 2 → 이 사용자가 들어가면 가득 찬다
		Crew crew = preparedCrew(2);
		String userId = newUsers(1).get(0);

		// When
		List<ExtractableResponse<Response>> responses = concurrent(10, () -> join(userId, crew.getId()));

		// Then
		assertThat(responses).filteredOn(r -> r.statusCode() == 201).hasSize(1);
		assertThat(responses).filteredOn(r -> r.statusCode() != 201)
			.hasSize(9).allSatisfy(r -> assertError(r, 409, "CR004"));
		assertThat(pendingFor(crew.getId())).hasSize(1);
		CrewJoinRunConsistency.assertCrew(redis, PREFIX, crew.getId(), initialOf(crew));

		// Then: 가득 찬 크루의 기존 멤버 단독 요청 → 정원(CR002)보다 중복(CR004)이 먼저, 상태 불변
		Map<String, String> before = redisSnapshot();
		assertError(join(userId, crew.getId()), 409, "CR004");
		assertThat(redisSnapshot()).isEqualTo(before);
	}

	@Test
	@DisplayName("P2-T9 응답 유실 — 서버 적용 후 응답 실패 모델(실제 TCP 장애 재현 아님): 500이어도 Redis엔 승인·pending이 있고 재요청은 CR004")
	void responseLostAfterApproval_retryIsDuplicate() throws Exception {
		// Given: 실제 approve를 끝낸 뒤 예외를 던지는 wrapper (첫 호출만)
		Crew crew = preparedCrew(5);
		String userId = newUsers(1).get(0);
		doAnswer(invocation -> {
			invocation.callRealMethod();
			throw new IllegalStateException("TIMEOUT_OR_UNKNOWN: simulated response loss after approval");
		}).doCallRealMethod().when(crewJoinRedisPort).approve(any(), any(), any());

		// When
		ExtractableResponse<Response> first = join(userId, crew.getId());

		// Then: 201이 없어도 승인·pending은 존재 — 201 수와 승인 수가 달라도 된다
		assertError(first, 500, "C002");
		assertThat(redis.opsForZSet().score(membersKey(crew), userId)).isEqualTo(2.0);
		assertThat(pendingFor(crew.getId())).hasSize(1);

		// Then: 재요청은 첫 응답을 재생하지 않고 중복으로 거절, pending은 그대로 1건
		assertError(join(userId, crew.getId()), 409, "CR004");
		assertThat(pendingFor(crew.getId())).hasSize(1);
		CrewJoinRunConsistency.assertCrew(redis, PREFIX, crew.getId(), initialOf(crew));
	}

	@Test
	@DisplayName("P2-T9 연결 실패 — Redis에 연결할 수 없으면 500/C002이고 DB 전략으로 fallback하지 않는다")
	void connectionFailure_noDbFallback() throws Exception {
		// Given: Adapter가 연결 실패를 번역한 것과 같은 예외 주입 (공유 Redis 컨테이너는 내리지 않는다)
		Crew crew = preparedCrew(5);
		String userId = newUsers(1).get(0);
		doThrow(new IllegalStateException("CONNECTION: crew join Redis unavailable",
			new RedisConnectionFailureException("simulated")))
			.when(crewJoinRedisPort).approve(any(), any(), any());
		Map<String, String> before = redisSnapshot();

		// When
		ExtractableResponse<Response> response = join(userId, crew.getId());

		// Then
		assertError(response, 500, "C002");
		assertThat(redisSnapshot()).isEqualTo(before);
		Crew stored = crewRepositoryPort.findById(crew.getId()).orElseThrow();
		assertThat(stored.getCurrentMembers()).isEqualTo(1);
		assertThat(stored.getMembers()).extracting(CrewMember::getUserId).containsExactly(crew.getCreatorId());
	}

	// ===== 헬퍼 =====

	/** DB·Redis 모두 리더 1명인 공개 모집 크루 */
	private Crew preparedCrew(int maxMembers) throws Exception {
		String leaderId = "leader-" + UUID.randomUUID();
		createUser(leaderId);
		Crew crew = Crew.of(
			IdGenerator.generate("CREW"), leaderId, "Redis 경합 크루", "목표",
			"인증 내용", VerificationType.TEXT, maxMembers, 1, CrewStatus.RECRUITING,
			LocalDate.now().minusDays(1), LocalDate.now().plusDays(20), true,
			Crew.generateInviteCode(), LocalDateTime.now(),
			Crew.DEFAULT_DEADLINE_TIME, null, CrewVisibility.PUBLIC, 0L, List.of());
		crewRepositoryPort.save(crew);
		crewRepositoryPort.saveMember(CrewMember.createLeader(leaderId, crew.getId()));
		Crew saved = crewRepositoryPort.findById(crew.getId()).orElseThrow();
		initRedis(saved, leaderId);
		return saved;
	}

	private static List<String> initialOf(Crew crew) {
		return List.of(crew.getCreatorId());
	}

	private List<String> newUsers(int count) {
		List<String> users = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			String userId = "user-" + UUID.randomUUID();
			createUser(userId);
			users.add(userId);
		}
		return users;
	}

	/** 사용자별 동시 가입 — 응답을 전부 모은다 */
	private Map<String, ExtractableResponse<Response>> concurrentJoin(String crewId, List<String> users)
		throws Exception {
		List<ExtractableResponse<Response>> responses = new ArrayList<>();
		List<Callable<ExtractableResponse<Response>>> calls = new ArrayList<>();
		for (String userId : users) {
			calls.add(() -> join(userId, crewId));
		}
		responses.addAll(runTogether(calls));
		Map<String, ExtractableResponse<Response>> byUser = new HashMap<>();
		for (int i = 0; i < users.size(); i++) {
			byUser.put(users.get(i), responses.get(i));
		}
		return byUser;
	}

	private List<ExtractableResponse<Response>> concurrent(int count,
		Callable<ExtractableResponse<Response>> call) throws Exception {
		return runTogether(Collections.nCopies(count, call));
	}

	/** 준비 래치로 동시에 출발시키고 모든 응답을 기다린다 */
	private static <T> List<T> runTogether(List<Callable<T>> calls) throws Exception {
		CountDownLatch ready = new CountDownLatch(calls.size());
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(calls.size());
		try {
			List<Future<T>> futures = new ArrayList<>();
			for (Callable<T> call : calls) {
				futures.add(executor.submit(() -> {
					ready.countDown();
					start.await();
					return call.call();
				}));
			}
			ready.await();
			start.countDown();
			List<T> results = new ArrayList<>();
			for (Future<T> future : futures) {
				results.add(future.get(30, TimeUnit.SECONDS));
			}
			return results;
		} finally {
			executor.shutdownNow();
		}
	}

	private static Set<String> usersWithStatus(Map<String, ExtractableResponse<Response>> responses, int status) {
		Set<String> users = new HashSet<>();
		responses.forEach((userId, response) -> {
			if (response.statusCode() == status) {
				users.add(userId);
			}
		});
		return users;
	}

	/** 성공자 외 응답을 "status:code"로 — 상태 코드까지 함께 단언한다 */
	private static List<String> errorCodesExcept(Map<String, ExtractableResponse<Response>> responses,
		Set<String> excluded) {
		List<String> codes = new ArrayList<>();
		responses.forEach((userId, response) -> {
			if (!excluded.contains(userId)) {
				codes.add(response.statusCode() + ":" + response.jsonPath().getString("error.code"));
			}
		});
		return codes;
	}

	private ExtractableResponse<Response> join(String userId, String crewId) {
		return authPost(userId, "/crews/" + crewId + "/join", Map.of());
	}

	private static void assertError(ExtractableResponse<Response> response, int status, String code) {
		assertThat(response.statusCode()).isEqualTo(status);
		assertThat(response.jsonPath().getString("error.code")).isEqualTo(code);
	}

	private Crew savePublicCrew(String leaderId, CrewStatus status, LocalDate endDate, CrewVisibility visibility) {
		Crew crew = Crew.of(
			IdGenerator.generate("CREW"), leaderId, "Redis 가입 크루", "목표",
			"인증 내용", VerificationType.TEXT, 3, 1, status,
			LocalDate.now().minusDays(1), endDate, true,
			Crew.generateInviteCode(), LocalDateTime.now(),
			Crew.DEFAULT_DEADLINE_TIME, null, visibility, 0L, List.of());
		crewRepositoryPort.save(crew);
		crewRepositoryPort.saveMember(CrewMember.createLeader(leaderId, crew.getId()));
		return crewRepositoryPort.findById(crew.getId()).orElseThrow();
	}

	/** Step B 초기화 Lua로 DB와 같은 초기 상태(리더 1명, score 1, seq 1)를 만든다 */
	private void initRedis(Crew crew, String leaderId) throws Exception {
		List<String> keys = List.of(membersKey(crew), PREFIX + ":crew:" + crew.getId() + ":meta");
		String result = redis.execute(INIT_SCRIPT, keys,
			String.valueOf(crew.getMaxMembers()), objectMapper.writeValueAsString(List.of(leaderId)));
		assertThat(result).isEqualTo("OK");
	}

	private static String membersKey(Crew crew) {
		return PREFIX + ":crew:" + crew.getId() + ":members";
	}

	private List<Map<String, Object>> pendingFor(String crewId) throws Exception {
		List<Map<String, Object>> matched = new ArrayList<>();
		for (String raw : redis.opsForList().range(PREFIX + ":pending", 0, -1)) {
			@SuppressWarnings("unchecked")
			Map<String, Object> payload = objectMapper.readValue(raw, Map.class);
			if (crewId.equals(payload.get("crewId"))) {
				matched.add(payload);
			}
		}
		return matched;
	}

	private Set<String> redisKeys() {
		return redis.keys(PREFIX + ":*");
	}

	/** run 전체 key의 자료형·내용 스냅샷 (pending은 다른 테스트 작업을 포함하므로 길이·내용을 통째로 비교) */
	private Map<String, String> redisSnapshot() {
		Map<String, String> snapshot = new TreeMap<>();
		for (String key : redisKeys()) {
			String value = switch (redis.type(key)) {
				case ZSET -> String.valueOf(redis.opsForZSet().rangeWithScores(key, 0, -1).stream()
					.map(t -> t.getValue() + "=" + t.getScore()).toList());
				case HASH -> String.valueOf(new TreeMap<>(redis.opsForHash().entries(key)));
				case LIST -> String.valueOf(redis.opsForList().range(key, 0, -1));
				default -> redis.type(key).code();
			};
			snapshot.put(key, value);
		}
		return snapshot;
	}
}
