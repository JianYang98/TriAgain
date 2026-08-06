package com.triagain.support.infra;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import com.triagain.common.util.IdGenerator;
import com.triagain.crew.domain.model.Crew;
import com.triagain.crew.domain.model.CrewMember;
import com.triagain.crew.domain.vo.CrewCategory;
import com.triagain.crew.domain.vo.CrewStatus;
import com.triagain.crew.domain.vo.CrewVisibility;
import com.triagain.crew.domain.vo.VerificationType;
import com.triagain.crew.port.out.CrewRepositoryPort;
import com.triagain.verification.domain.model.Verification;
import com.triagain.verification.port.out.VerificationRepositoryPort;

import io.restassured.RestAssured;
import io.restassured.response.Response;

/**
 * PUT /verifications/{id}/reactions — E3 연타(v1 유일 중복 경로): 같은 유저·같은 인증·같은 이모지(LIKE)를
 * 연속 2회 PUT하면 200 · {@code reactions} 행 1개 · 요약 count 1이어야 한다(step1-biz-logic.md §7-2).
 * 활성 이모지 세트가 LIKE 하나뿐이라 "다른 이모지로 교체"는 API로 도달 불가 — 이게 v1에서 유저가 실제로
 * 밟는 유일한 중복 경로다.
 * <p>
 * ⚠️ {@code AddReactionService}는 아직 스켈레톤(저장 없이 빈 요약 반환)이라 이 테스트는 현재 단언 실패로
 * 떨어지는 것이 정상이다 — 포트 교정 후 그린이 되어야 한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ReactionApiTest extends ReactionIntegrationTestBase {

	@LocalServerPort
	private int port;

	@Autowired
	private CrewRepositoryPort crewRepositoryPort;

	@Autowired
	private VerificationRepositoryPort verificationRepositoryPort;

	@Autowired
	private ReactionJpaRepository reactionJpaRepository;

	@Test
	@DisplayName("E3 연타 — 같은 유저가 같은 이모지(LIKE)를 2회 PUT하면 200 · reactions 행 1개 · count 1")
	void addReaction_sameEmojiTwice_upsertsSingleRowWithCountOne() {
		// given
		String userId = "reaction-api-user1";
		String verificationId = givenApprovedVerificationWithCrewMember(userId);

		// when — 같은 유저·같은 인증·같은 이모지(LIKE)로 연속 2회 PUT
		putReaction(userId, verificationId, "LIKE");
		Response second = putReaction(userId, verificationId, "LIKE");

		// then
		assertThat(second.statusCode()).isEqualTo(200);
		assertThat(reactionJpaRepository.countByVerificationId(verificationId)).isEqualTo(1);

		List<Map<String, Object>> reactions = second.jsonPath().getList("data.reactions");
		assertThat(reactions).hasSize(1);
		assertThat(reactions.get(0).get("emojiType")).isEqualTo("LIKE");
		assertThat(((Number) reactions.get(0).get("count")).intValue()).isEqualTo(1);
	}

	/**
	 * 크루 리더 멤버 + APPROVED 인증 시드 — 리액션을 남기는 유효한 크루 컨텍스트 재현.
	 * {@code Crew.create}는 startDate가 미래여야 하는 신규-생성 검증을 거치므로(진행 중 크루를 표현할 수 없음),
	 * DB 복원용 {@code Crew.of}로 이미 ACTIVE인 크루를 직접 구성한다(선례:
	 * {@code CreateVerificationServiceSlotDeadlineIntegrationTest}).
	 */
	private String givenApprovedVerificationWithCrewMember(String userId) {
		String crewId = IdGenerator.generate("CREW");
		Crew crew = Crew.of(
				crewId, userId, "리액션 API 테스트 크루", "목표", "인증 내용",
				VerificationType.TEXT, 10, 1, CrewStatus.ACTIVE,
				LocalDate.now(), LocalDate.now().plusDays(3), false,
				"RCTAPI", LocalDateTime.now(), LocalTime.of(23, 59, 59),
				CrewCategory.ETC, CrewVisibility.PRIVATE, 0L, List.of());
		crewRepositoryPort.save(crew);
		crewRepositoryPort.saveMember(CrewMember.createLeader(userId, crewId));

		Verification verification = Verification.createText(
				"CHAL-reaction-api", userId, crew.getId(), "인증", LocalDate.now(), 1, 1);
		return verificationRepositoryPort.save(verification).getId();
	}

	/** 리액션 PUT 호출 — 요청마다 port를 지정해 RestAssured 정적 설정에 기대지 않는다 */
	private Response putReaction(String userId, String verificationId, String emojiType) {
		return RestAssured.given()
				.port(port)
				.contentType("application/json")
				.header("X-User-Id", userId)
				.body("{\"emojiType\": \"" + emojiType + "\"}")
				.when()
				.put("/verifications/{verificationId}/reactions", verificationId);
	}
}
