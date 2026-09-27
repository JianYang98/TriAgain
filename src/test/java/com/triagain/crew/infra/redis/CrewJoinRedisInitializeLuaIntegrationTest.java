package com.triagain.crew.infra.redis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.data.redis.core.script.RedisScript;

/** 크루 가입 Redis 사전 초기화 Lua — 실제 Redis로 N·score·seq·capacity와 재시드/부분 상태/입력 거부, 동시 init, run 격리 검증 */
@Tag("e2e")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CrewJoinRedisInitializeLuaIntegrationTest {

	private static final RedisScript<String> INIT_SCRIPT =
		RedisScript.of(new ClassPathResource("redis/crew-join/initialize-crew-join.lua"), String.class);

	private LettuceConnectionFactory connectionFactory;
	private StringRedisTemplate redis;

	@BeforeAll
	void setUp() {
		connectionFactory = new LettuceConnectionFactory(
			new RedisStandaloneConfiguration(RedisTestContainer.getHost(), RedisTestContainer.getPort()));
		connectionFactory.afterPropertiesSet();
		redis = new StringRedisTemplate(connectionFactory);
	}

	@AfterAll
	void tearDown() {
		connectionFactory.destroy();
	}

	@Test
	@DisplayName("비어 있는 크루를 초기화하면 정렬된 N명에 score 1..N, seq=N, capacity 복사, initialized=1이고 pending은 만들지 않는다")
	void init_createsMembersAndMeta() {
		// Given
		String prefix = newPrefix();
		Keys keys = keys(prefix, "CREW-1");

		// When
		String result = init(keys, "5", "[\"u-a\",\"u-b\",\"u-c\"]");

		// Then
		assertThat(result).isEqualTo("OK");
		assertThat(scores(keys.members())).containsExactly("u-a=1.0", "u-b=2.0", "u-c=3.0");
		assertThat(redis.<String, String>opsForHash().entries(keys.meta()))
			.containsExactlyInAnyOrderEntriesOf(Map.of("capacity", "5", "seq", "3", "initialized", "1"));
		assertThat(scanKeys(prefix)).containsExactlyInAnyOrder(keys.members(), keys.meta());
	}

	@Test
	@DisplayName("이미 초기화된 크루를 다시 초기화하면 ALREADY_EXISTS_OR_PARTIAL이고 상태가 바뀌지 않는다")
	void init_rejectsReseed() {
		// Given: 초기화 후 신규 승인 한 건이 반영된 상태를 흉내 낸다 (seq·members가 DB보다 앞섬)
		Keys keys = keys(newPrefix(), "CREW-1");
		init(keys, "5", "[\"u-a\"]");
		redis.opsForHash().put(keys.meta(), "seq", "2");
		redis.opsForZSet().add(keys.members(), "u-new", 2);
		Snapshot before = snapshot(keys);

		// When
		String result = init(keys, "5", "[\"u-a\"]");

		// Then
		assertThat(result).isEqualTo("ALREADY_EXISTS_OR_PARTIAL");
		assertThat(snapshot(keys)).isEqualTo(before);
	}

	@Test
	@DisplayName("meta만 있거나 members만 있는 부분 상태는 거부하고 어떤 key도 바꾸지 않는다")
	void init_rejectsPartialState() {
		// Given
		Keys metaOnly = keys(newPrefix(), "CREW-1");
		redis.opsForHash().put(metaOnly.meta(), "capacity", "5");
		Keys membersOnly = keys(newPrefix(), "CREW-1");
		redis.opsForZSet().add(membersOnly.members(), "u-x", 1);
		Snapshot metaOnlyBefore = snapshot(metaOnly);
		Snapshot membersOnlyBefore = snapshot(membersOnly);

		// When & Then
		assertThat(init(metaOnly, "5", "[\"u-a\"]")).isEqualTo("ALREADY_EXISTS_OR_PARTIAL");
		assertThat(snapshot(metaOnly)).isEqualTo(metaOnlyBefore);
		assertThat(init(membersOnly, "5", "[\"u-a\"]")).isEqualTo("ALREADY_EXISTS_OR_PARTIAL");
		assertThat(snapshot(membersOnly)).isEqualTo(membersOnlyBefore);
	}

	@ParameterizedTest(name = "capacity={0}, users={1}")
	@CsvSource(delimiter = '|', value = {
		"0|[\"u-a\"]",
		"-1|[\"u-a\"]",
		"1.5|[\"u-a\"]",
		"abc|[\"u-a\"]",
		"5|[]",
		"5|not-json",
		"5|{\"u\":\"u-a\"}",
		"5|[1,2]",
		"5|[\"\"]",
		"5|[\"u-b\",\"u-a\"]",
		"5|[\"u-a\",\"u-a\"]",
		"2|[\"u-a\",\"u-b\",\"u-c\"]"
	})
	@DisplayName("잘못된 capacity·멤버 목록(빈 목록·비문자열·미정렬·중복·정원 초과)은 INVALID_INPUT이고 key를 만들지 않는다")
	void init_rejectsInvalidInput(String capacity, String usersJson) {
		// Given
		String prefix = newPrefix();
		Keys keys = keys(prefix, "CREW-1");

		// When
		String result = init(keys, capacity, usersJson);

		// Then
		assertThat(result).isEqualTo("INVALID_INPUT");
		assertThat(scanKeys(prefix)).isEmpty();
	}

	@Test
	@DisplayName("members와 meta key가 같은 크루의 쌍이 아니면 INVALID_INPUT이고 key를 만들지 않는다")
	void init_rejectsMismatchedKeys() {
		// Given
		String prefix = newPrefix();
		Keys mismatched = new Keys(prefix + ":crew:CREW-1:members", prefix + ":crew:CREW-2:meta");

		// When
		String result = init(mismatched, "5", "[\"u-a\"]");

		// Then
		assertThat(result).isEqualTo("INVALID_INPUT");
		assertThat(scanKeys(prefix)).isEmpty();
	}

	@Test
	@DisplayName("같은 크루를 동시에 20번 초기화하면 정확히 하나만 성공하고 최종 상태는 한 번 초기화한 것과 같다")
	void init_concurrentOnlyOneSucceeds() throws Exception {
		// Given
		Keys keys = keys(newPrefix(), "CREW-1");
		int threads = 20;
		CountDownLatch ready = new CountDownLatch(threads);
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(threads);
		List<Future<String>> futures = new ArrayList<>();

		// When
		for (int i = 0; i < threads; i++) {
			futures.add(executor.submit(() -> {
				ready.countDown();
				start.await();
				return init(keys, "5", "[\"u-a\",\"u-b\"]");
			}));
		}
		ready.await();
		start.countDown();
		List<String> results = new ArrayList<>();
		for (Future<String> future : futures) {
			results.add(future.get());
		}
		executor.shutdown();

		// Then
		assertThat(results).filteredOn("OK"::equals).hasSize(1);
		assertThat(results).filteredOn("ALREADY_EXISTS_OR_PARTIAL"::equals).hasSize(threads - 1);
		assertThat(scores(keys.members())).containsExactly("u-a=1.0", "u-b=2.0");
		assertThat(redis.opsForHash().get(keys.meta(), "seq")).isEqualTo("2");
	}

	@Test
	@DisplayName("run이 다르면 같은 crewId도 독립이고, run prefix SCAN으로 지우면 다른 run은 남는다")
	void runs_areIsolatedAndCleanupIsRunScoped() {
		// Given
		String runA = newPrefix();
		String runB = newPrefix();
		Keys crewInA = keys(runA, "CREW-1");
		Keys crewInB = keys(runB, "CREW-1");
		assertThat(init(crewInA, "5", "[\"u-a\"]")).isEqualTo("OK");
		assertThat(init(crewInB, "3", "[\"u-b\",\"u-c\"]")).isEqualTo("OK");
		Snapshot bBefore = snapshot(crewInB);

		// When: scripts/crew-join-redis.sh cleanup과 같은 패턴(<prefix>:*)으로 run A만 삭제
		Set<String> runAKeys = scanKeys(runA);
		redis.delete(runAKeys);

		// Then
		assertThat(runAKeys).containsExactlyInAnyOrder(crewInA.members(), crewInA.meta());
		assertThat(scanKeys(runA)).isEmpty();
		assertThat(snapshot(crewInB)).isEqualTo(bBefore);
		assertThat(scores(crewInB.members())).containsExactly("u-b=1.0", "u-c=2.0");
	}

	private String init(Keys keys, String capacity, String usersJson) {
		return redis.execute(INIT_SCRIPT, List.of(keys.members(), keys.meta()), capacity, usersJson);
	}

	private static String newPrefix() {
		return "triagain:crew-join:{test:run-" + UUID.randomUUID() + "}";
	}

	private static Keys keys(String prefix, String crewId) {
		return new Keys(prefix + ":crew:" + crewId + ":members", prefix + ":crew:" + crewId + ":meta");
	}

	private List<String> scores(String membersKey) {
		Set<TypedTuple<String>> tuples = redis.opsForZSet().rangeWithScores(membersKey, 0, -1);
		return tuples.stream().map(t -> t.getValue() + "=" + t.getScore()).toList();
	}

	private Set<String> scanKeys(String prefix) {
		Set<String> found = new HashSet<>();
		try (Cursor<String> cursor = redis.scan(ScanOptions.scanOptions().match(prefix + ":*").count(1000).build())) {
			cursor.forEachRemaining(found::add);
		}
		return found;
	}

	private Snapshot snapshot(Keys keys) {
		return new Snapshot(scores(keys.members()), redis.<String, String>opsForHash().entries(keys.meta()));
	}

	private record Keys(String members, String meta) {
	}

	private record Snapshot(List<String> members, Map<String, String> meta) {
	}
}
