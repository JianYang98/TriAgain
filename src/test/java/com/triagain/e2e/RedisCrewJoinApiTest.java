package com.triagain.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.triagain.common.util.IdGenerator;
import com.triagain.crew.domain.model.Crew;
import com.triagain.crew.domain.model.CrewMember;
import com.triagain.crew.domain.vo.CrewStatus;
import com.triagain.crew.domain.vo.CrewVisibility;
import com.triagain.crew.domain.vo.VerificationType;
import com.triagain.crew.infra.redis.RedisTestContainer;

import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;

/**
 * REDIS_ASYNC 공개 가입 — 실제 HTTP·인증·예외 Handler·Redis·PostgreSQL 단일 컨텍스트.
 * Phase 2는 DB에 쓰지 않으므로 성공 후에도 DB 멤버·인원·version은 초기값이다.
 */
class RedisCrewJoinApiTest extends E2eTestBase {

	private static final String RUN_ID = "api-" + UUID.randomUUID();
	private static final String PREFIX = "triagain:crew-join:{api:" + RUN_ID + "}";
	private static final RedisScript<String> INIT_SCRIPT =
		RedisScript.of(new ClassPathResource("redis/crew-join/initialize-crew-join.lua"), String.class);

	@Autowired
	private StringRedisTemplate redis;

	@Autowired
	private ObjectMapper objectMapper;

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

	// ===== 헬퍼 =====

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
