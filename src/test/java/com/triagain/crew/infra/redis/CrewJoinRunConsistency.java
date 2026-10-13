package com.triagain.crew.infra.redis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.LongStream;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 실험 종료 정합성 검사 (P2-T14) — 테스트 전용, 런타임 코드 아님. 읽기만 하고 복구·재생성하지 않는다.
 * 초기화(score 1..N, seq=N) + JOIN-only 전제에서: seq = ZCARD = N+K, 신규 score = N+1..N+K,
 * 신규 멤버 집합 = 해당 crew pending의 userId 집합, pending 건수 = K, (crewId,userId)별 정확히 1건.
 */
public final class CrewJoinRunConsistency {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private CrewJoinRunConsistency() {
	}

	/** 크루 하나 검사 — 통과하면 신규 승인 사용자(K명)를 승인 순서대로 돌려준다. 어긋나면 AssertionError */
	public static List<String> assertCrew(StringRedisTemplate redis, String prefix, String crewId,
		List<String> initialSortedMembers) {
		String crewKey = prefix + ":crew:" + crewId;
		List<String> members = new ArrayList<>();
		List<Long> scores = new ArrayList<>();
		for (TypedTuple<String> tuple : redis.opsForZSet().rangeWithScores(crewKey + ":members", 0, -1)) {
			members.add(tuple.getValue());
			scores.add(tuple.getScore().longValue());
		}
		int initial = initialSortedMembers.size();
		assertThat(members.subList(0, Math.min(initial, members.size())))
			.as("초기 멤버는 score 1..N 그대로").isEqualTo(initialSortedMembers);
		List<String> newMembers = members.subList(initial, members.size());
		String seq = (String)redis.opsForHash().get(crewKey + ":meta", "seq");
		assertThat(seq).as("seq = ZCARD = N+K").isEqualTo(String.valueOf(members.size()));
		assertThat(scores).as("score = 1..N+K, 빈 순번·중복 없음")
			.isEqualTo(LongStream.rangeClosed(1, members.size()).boxed().toList());

		List<String> pendingUsers = pendingUsers(redis, prefix, crewId);
		assertThat(pendingUsers).as("pending 건수 = K").hasSize(newMembers.size());
		assertThat(new HashSet<>(pendingUsers)).as("(crewId,userId)별 정확히 1건").hasSize(pendingUsers.size());
		assertThat(new HashSet<>(pendingUsers)).as("pending 사용자 = 신규 멤버").isEqualTo(new HashSet<>(newMembers));
		return List.copyOf(newMembers);
	}

	/** run 공용 pending에 예상 밖 crewId가 없는지 — 크루별 assertCrew와 합치면 LLEN = ΣK */
	public static void assertNoUnexpectedCrew(StringRedisTemplate redis, String prefix, Set<String> crewIds) {
		for (String raw : redis.opsForList().range(prefix + ":pending", 0, -1)) {
			assertThat(crewIds).as("예상 밖 crew의 작업").contains((String)parse(raw).get("crewId"));
		}
	}

	private static List<String> pendingUsers(StringRedisTemplate redis, String prefix, String crewId) {
		List<String> users = new ArrayList<>();
		for (String raw : redis.opsForList().range(prefix + ":pending", 0, -1)) {
			Map<String, Object> payload = parse(raw);
			if (crewId.equals(payload.get("crewId"))) {
				assertThat(payload).as("payload는 String 3필드").hasSize(3)
					.containsKeys("crewId", "userId", "confirmedAt")
					.allSatisfy((key, value) -> assertThat(value).isInstanceOf(String.class));
				users.add((String)payload.get("userId"));
			}
		}
		return users;
	}

	private static Map<String, Object> parse(String raw) {
		try {
			return MAPPER.readValue(raw, new TypeReference<>() { });
		} catch (JsonProcessingException e) {
			throw new AssertionError("pending payload is not JSON: " + raw, e);
		}
	}
}
