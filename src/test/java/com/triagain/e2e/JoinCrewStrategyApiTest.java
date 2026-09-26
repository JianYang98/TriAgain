package com.triagain.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import com.triagain.common.util.IdGenerator;
import com.triagain.crew.application.CrewLockProperties;
import com.triagain.crew.application.CrewLockProperties.LockStrategy;
import com.triagain.crew.domain.model.Crew;
import com.triagain.crew.domain.model.CrewMember;
import com.triagain.crew.domain.vo.CrewStatus;
import com.triagain.crew.domain.vo.CrewVisibility;
import com.triagain.crew.domain.vo.VerificationType;

import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;

@Tag("e2e")
class JoinCrewStrategyApiTest {

	@Nested
	@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = "triagain.crew.lock-strategy=PESSIMISTIC")
	class Pessimistic extends DatabaseStrategy {
		@Override
		LockStrategy expectedStrategy() {
			return LockStrategy.PESSIMISTIC;
		}
	}

	@Nested
	@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = "triagain.crew.lock-strategy=OPTIMISTIC")
	class Optimistic extends DatabaseStrategy {
		@Override
		LockStrategy expectedStrategy() {
			return LockStrategy.OPTIMISTIC;
		}
	}

	@Nested
	@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = "triagain.crew.lock-strategy=CONDITIONAL")
	class Conditional extends DatabaseStrategy {
		@Override
		LockStrategy expectedStrategy() {
			return LockStrategy.CONDITIONAL;
		}
	}

	@Nested
	@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = "triagain.crew.lock-strategy=REDIS_ASYNC")
	class RedisAsync extends JoinFixture {

		@ParameterizedTest
		@EnumSource(CrewVisibility.class)
		@DisplayName("Redis 공개·초대 가입은 500 C002로 거부하고 DB를 변경하지 않는다")
		void redisAsync_rejectsBothRoutesWithoutWrites(CrewVisibility visibility) {
			// Given — 잘못된 DB fallback이면 실제로 가입 가능한 크루·사용자
			assertThat(lockProperties.getLockStrategy()).isEqualTo(LockStrategy.REDIS_ASYNC);
			Crew crew = seedCrew(visibility);

			// When & Then
			for (int attempt = 0; attempt < 2; attempt++) {
				var response = join(crew);
				assertThat(response.statusCode()).isEqualTo(500);
				assertThat(response.jsonPath().getString("error.code")).isEqualTo("C002");
				assertMembership(crew, 1, 0);
			}
		}
	}

	abstract static class DatabaseStrategy extends JoinFixture {

		abstract LockStrategy expectedStrategy();

		@ParameterizedTest
		@EnumSource(CrewVisibility.class)
		@DisplayName("기존 DB 전략은 공개·초대 가입을 저장하고 중복 요청의 인원은 늘리지 않는다")
		void joinsAndRejectsDuplicate(CrewVisibility visibility) {
			// Given
			assertThat(lockProperties.getLockStrategy()).isEqualTo(expectedStrategy());
			Crew crew = seedCrew(visibility);

			// When
			var joined = join(crew);

			// Then
			assertThat(joined.statusCode()).isEqualTo(201);
			assertThat(joined.jsonPath().getString("data.userId")).isEqualTo("phase1-member");
			assertThat(joined.jsonPath().getString("data.crewId")).isEqualTo(crew.getId());
			assertThat(joined.jsonPath().getString("data.role")).isEqualTo("MEMBER");
			assertThat(joined.jsonPath().getInt("data.currentMembers")).isEqualTo(2);
			assertThat(joined.jsonPath().getString("data.joinedAt")).isNotBlank();
			assertMembership(crew, 2, 1);

			var duplicate = join(crew);
			assertThat(duplicate.statusCode()).isEqualTo(409);
			assertThat(duplicate.jsonPath().getString("error.code")).isEqualTo("CR004");
			assertMembership(crew, 2, 1);
		}
	}

	abstract static class JoinFixture extends E2eTestBase {

		@Autowired
		protected CrewLockProperties lockProperties;

		@Autowired
		private JdbcTemplate jdbcTemplate;

		protected Crew seedCrew(CrewVisibility visibility) {
			createUser("phase1-leader");
			createUser("phase1-member");
			String crewId = IdGenerator.generate("CREW");
			Crew crew = Crew.of(crewId, "phase1-leader", "전략 검증", "목표", "인증", VerificationType.TEXT,
				5, 1, CrewStatus.RECRUITING, LocalDate.now().plusDays(1), LocalDate.now().plusDays(30),
				false, Crew.generateInviteCode(), LocalDateTime.now(), Crew.DEFAULT_DEADLINE_TIME,
				null, visibility, 0L, List.of());
			crewRepositoryPort.save(crew);
			crewRepositoryPort.saveMember(CrewMember.createLeader("phase1-leader", crewId));
			return crew;
		}

		protected ExtractableResponse<Response> join(Crew crew) {
			return crew.isPublic()
				? authPost("phase1-member", "/crews/" + crew.getId() + "/join", Map.of())
				: authPost("phase1-member", "/crews/join", Map.of("inviteCode", crew.getInviteCode()));
		}

		protected void assertMembership(Crew crew, int total, int joined) {
			assertThat(jdbcTemplate.queryForObject("SELECT current_members FROM crews WHERE id = ?",
				Integer.class, crew.getId())).isEqualTo(total);
			assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM crew_members WHERE crew_id = ?",
				Integer.class, crew.getId())).isEqualTo(total);
			assertThat(jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM crew_members WHERE crew_id = ? AND user_id = ?",
				Integer.class, crew.getId(), "phase1-member")).isEqualTo(joined);
		}
	}
}
