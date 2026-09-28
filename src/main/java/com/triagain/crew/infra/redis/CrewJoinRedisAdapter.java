package com.triagain.crew.infra.redis;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.triagain.crew.port.out.CrewJoinRedisPort;

import lombok.RequiredArgsConstructor;

/**
 * Redis 가입 승인 Adapter — key 조립·payload 직렬화·approve.lua 실행·반환 해석.
 * DB 전략에서도 Bean은 존재하지만 생성 시 네트워크·검증을 하지 않는다(호출은 REDIS_ASYNC에서만).
 */
@Component
@RequiredArgsConstructor
public class CrewJoinRedisAdapter implements CrewJoinRedisPort {

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static final RedisScript<List<Object>> APPROVE_SCRIPT = (RedisScript)RedisScript.of(
		new ClassPathResource("redis/crew-join/approve.lua"), List.class);

	private static final ZoneOffset SEOUL_OFFSET = ZoneOffset.ofHours(9);
	private static final DateTimeFormatter CONFIRMED_AT_FORMAT =
		DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
	private static final Status[] CODES = {
		Status.JOIN_SUCCESS, Status.ALREADY_JOINED, Status.CREW_FULL, Status.NOT_INITIALIZED, Status.INVALID_STATE};

	private final StringRedisTemplate redisTemplate;
	private final CrewJoinRedisProperties properties;
	private final ObjectMapper objectMapper;

	/** 승인 Lua 1회 실행 — 업무 결과는 Status, 인프라·직렬화·예상 밖 반환은 IllegalStateException */
	@Override
	public Approval approve(String crewId, String userId, OffsetDateTime confirmedAt) {
		// 실행 오류 뒤 상태 검사는 run 단위라 모든 내부 실패 메시지에 namespace/runId/crewId를 남긴다
		String where = " (namespace=" + properties.namespace() + ", runId=" + properties.runId()
			+ ", crewId=" + crewId + ")";
		String confirmedAtText = formatConfirmedAt(confirmedAt, where);
		String payload = serialize(new PendingPayload(crewId, userId, confirmedAtText), where);
		String prefix = "triagain:crew-join:{" + properties.namespace() + ":" + properties.runId() + "}";
		String crewKey = prefix + ":crew:" + crewId;
		List<String> keys = List.of(crewKey + ":members", crewKey + ":meta", prefix + ":pending");
		return interpret(execute(keys, where, crewId, userId, confirmedAtText, payload), where);
	}

	private List<Object> execute(List<String> keys, String where, String... args) {
		try {
			return redisTemplate.execute(APPROVE_SCRIPT, keys, (Object[])args);
		} catch (RedisConnectionFailureException e) {
			throw new IllegalStateException("CONNECTION: crew join Redis unavailable" + where, e);
		} catch (QueryTimeoutException e) {
			throw new IllegalStateException("TIMEOUT_OR_UNKNOWN: crew join Redis result unknown" + where, e);
		} catch (DataAccessException e) {
			throw new IllegalStateException("SCRIPT_ERROR: crew join approve script failed" + where, e);
		}
	}

	private static Approval interpret(List<Object> reply, String where) {
		if (reply == null || reply.size() != 2
				|| !(reply.get(0) instanceof Long code) || !(reply.get(1) instanceof Long members)
				|| code < 0 || code >= CODES.length) {
			throw new IllegalStateException("SCRIPT_ERROR: unexpected approve reply " + reply + where);
		}
		Status status = CODES[code.intValue()];
		boolean success = status == Status.JOIN_SUCCESS;
		if (success ? members <= 0 : members != 0) {
			throw new IllegalStateException("SCRIPT_ERROR: unexpected member count " + reply + where);
		}
		return new Approval(status, members.intValue());
	}

	private static String formatConfirmedAt(OffsetDateTime confirmedAt, String where) {
		// XXX는 UTC면 'Z'를 낸다 — pending 계약은 +09:00 고정이라 다른 offset은 받지 않는다
		if (!SEOUL_OFFSET.equals(confirmedAt.getOffset())) {
			throw new IllegalStateException("SERIALIZATION: confirmedAt must be +09:00 but was " + confirmedAt + where);
		}
		return CONFIRMED_AT_FORMAT.format(confirmedAt);
	}

	private String serialize(PendingPayload payload, String where) {
		try {
			return objectMapper.writeValueAsString(payload);
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("SERIALIZATION: crew join payload" + where, e);
		}
	}

	/** pending 작업 payload — 세 필드 모두 String. 필드 추가 금지(향후 worker 계약) */
	record PendingPayload(String crewId, String userId, String confirmedAt) {
	}
}
