package com.triagain.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import com.triagain.acceptance.TestContainers;
import com.triagain.crew.domain.model.Crew;
import com.triagain.crew.domain.model.CrewMember;
import com.triagain.crew.infra.redis.RedisTestContainer;

/**
 * scripts/crew-join-redis.sh를 문서대로 직접 실행(실행 비트 필요)해 기존 싱글턴 PostgreSQL·Redis 컨테이너에 붙인다.
 * 호스트 의존: bash, jq, docker CLI.
 */
class CrewJoinRedisScriptTest extends E2eTestBase {

	private static final Path SCRIPT = Path.of("scripts/crew-join-redis.sh").toAbsolutePath();
	private static final String NAMESPACE = "script";
	/** TestContainers의 withDatabaseName 값 */
	private static final String DB_NAME = "triagain_test";

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private LettuceConnectionFactory connectionFactory;
	private StringRedisTemplate redis;
	private String runId;
	private String prefix;

	@BeforeAll
	void connectRedis() {
		connectionFactory = new LettuceConnectionFactory(
			new RedisStandaloneConfiguration(RedisTestContainer.getHost(), RedisTestContainer.getPort()));
		connectionFactory.afterPropertiesSet();
		redis = new StringRedisTemplate(connectionFactory);
	}

	@AfterAll
	void closeRedis() {
		connectionFactory.destroy();
	}

	@AfterEach
	void cleanupRun() {
		redis.delete(scanKeys());
	}

	@Test
	@DisplayName("DB snapshot으로 init 후 preflight가 성공하고 score는 snapshot(COLLATE \"C\") 순서 1..N, meta·빈 pending이다")
	void init_thenPreflight_succeeds() throws Exception {
		// Given: 리더 u-b + 멤버 a-y, B-x → 바이트 순서 B-x < a-y < u-b
		newRun();
		Crew crew = createActiveCrew("u-b");
		crewRepositoryPort.saveMember(CrewMember.createMember("a-y", crew.getId()));
		crewRepositoryPort.saveMember(CrewMember.createMember("B-x", crew.getId()));
		jdbcTemplate.update("UPDATE crews SET current_members = 3 WHERE id = ?", crew.getId());

		// When
		Result init = run("init", crew.getId());
		Result preflight = run("preflight", crew.getId());

		// Then
		assertThat(init.exitCode()).as(init.output()).isZero();
		assertThat(preflight.exitCode()).as(preflight.output()).isZero();
		assertThat(redis.opsForZSet().rangeWithScores(key(crew, "members"), 0, -1))
			.extracting(t -> t.getValue() + "=" + t.getScore())
			.containsExactly("B-x=1.0", "a-y=2.0", "u-b=3.0");
		assertThat(redis.<String, String>opsForHash().entries(key(crew, "meta")))
			.containsExactlyInAnyOrderEntriesOf(Map.of("capacity", "10", "seq", "3", "initialized", "1"));
		assertThat(redis.opsForList().size(prefix + ":pending")).isZero();
	}

	@Test
	@DisplayName("DB snapshot이 어긋나면(current_members≠멤버 행 수) init은 종료 코드 5이고 이 run의 Redis key를 만들지 않는다")
	void init_snapshotMismatch_exits5WithoutKeys() throws Exception {
		// Given: 멤버 행은 리더 1명인데 current_members=2
		newRun();
		Crew crew = createActiveCrew("u-a");
		jdbcTemplate.update("UPDATE crews SET current_members = 2 WHERE id = ?", crew.getId());

		// When
		Result init = run("init", crew.getId());

		// Then
		assertThat(init.exitCode()).as(init.output()).isEqualTo(5);
		assertThat(scanKeys()).isEmpty();
	}

	private void newRun() {
		runId = "t-" + UUID.randomUUID();
		prefix = "triagain:crew-join:{" + NAMESPACE + ":" + runId + "}";
	}

	private String key(Crew crew, String suffix) {
		return prefix + ":crew:" + crew.getId() + ":" + suffix;
	}

	/** 스크립트를 프로그램으로 직접 실행 — `bash script`로 감싸지 않으므로 실행 비트가 없으면 실패한다 */
	private Result run(String command, String crewId) throws IOException, InterruptedException {
		ProcessBuilder builder = new ProcessBuilder(SCRIPT.toString(), command, NAMESPACE, runId, crewId)
			.redirectErrorStream(true);
		builder.environment().put("PSQL", "docker exec -i " + TestContainers.getContainerId()
			+ " psql -U " + TestContainers.getUsername() + " -d " + DB_NAME);
		builder.environment().put("REDIS_CLI", "docker exec -i " + RedisTestContainer.getContainerId() + " redis-cli");
		Process process = builder.start();
		if (!process.waitFor(60, TimeUnit.SECONDS)) {
			process.destroyForcibly();
			throw new AssertionError("script timed out: " + command);
		}
		String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
		return new Result(process.exitValue(), output);
	}

	private Set<String> scanKeys() {
		Set<String> found = new HashSet<>();
		try (Cursor<String> cursor = redis.scan(ScanOptions.scanOptions().match(prefix + ":*").count(1000).build())) {
			cursor.forEachRemaining(found::add);
		}
		return found;
	}

	private record Result(int exitCode, String output) {
	}
}
