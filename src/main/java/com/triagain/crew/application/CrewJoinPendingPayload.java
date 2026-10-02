package com.triagain.crew.application;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectReader;

public record CrewJoinPendingPayload(String crewId, String userId, LocalDateTime joinedAt) {

	private static final Pattern CONFIRMED_AT = Pattern.compile(
		"[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}\\.[0-9]{3}\\+09:00");

	/** 세 문자열 필드와 승인 시각을 엄격히 검증 — 파싱 오류에 raw를 노출하지 않음 */
	public static CrewJoinPendingPayload parse(String raw, ObjectReader reader) {
		try {
			JsonNode payload = reader.readTree(raw);
			if (payload == null || !payload.isObject() || payload.size() != 3) {
				throw new IllegalArgumentException("Expected exactly three payload fields");
			}
			String crewId = requiredText(payload, "crewId");
			String userId = requiredText(payload, "userId");
			String confirmedAt = requiredText(payload, "confirmedAt");
			if (!CONFIRMED_AT.matcher(confirmedAt).matches()) {
				throw new IllegalArgumentException("Invalid confirmedAt format");
			}
			return new CrewJoinPendingPayload(crewId, userId, OffsetDateTime.parse(confirmedAt).toLocalDateTime());
		} catch (IOException | DateTimeParseException exception) {
			throw new IllegalArgumentException("Malformed crew join payload");
		}
	}

	private static String requiredText(JsonNode payload, String name) {
		JsonNode value = payload.get(name);
		if (value == null || !value.isTextual() || value.textValue().isBlank()) {
			throw new IllegalArgumentException("Invalid payload field: " + name);
		}
		return value.textValue();
	}
}
