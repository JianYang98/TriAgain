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
import org.springframework.transaction.support.TransactionTemplate;

import com.triagain.common.util.IdGenerator;
import com.triagain.crew.domain.model.Crew;
import com.triagain.crew.domain.model.CrewMember;
import com.triagain.crew.domain.vo.CrewCategory;
import com.triagain.crew.domain.vo.CrewStatus;
import com.triagain.crew.domain.vo.CrewVisibility;
import com.triagain.crew.domain.vo.VerificationType;
import com.triagain.crew.port.out.CrewRepositoryPort;
import com.triagain.user.domain.model.User;
import com.triagain.user.port.out.UserRepositoryPort;
import com.triagain.verification.domain.model.Verification;
import com.triagain.verification.domain.vo.ReviewStatus;
import com.triagain.verification.domain.vo.VerificationStatus;
import com.triagain.verification.port.out.VerificationRepositoryPort;

import io.restassured.RestAssured;
import io.restassured.response.Response;

/**
 * 리액션 API 계층 테스트 — 유저 여정으로 쓸 수 없는 API 방어·에러 계약을 잠근다(step1-biz-logic.md §7-2).
 * <p>
 * ⚠️ {@code test}(H2) 프로파일 금지 — H2 는 {@code ON CONFLICT} 를 실행하지 못한다(42000).
 * {@link ReactionIntegrationTestBase}(전용 컨테이너 + Flyway ON + validate)를 상속한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ReactionApiTest extends ReactionIntegrationTestBase {

	/** error-messages.properties 실값 — 추측 금지, 파일에서 복사했다 */
	private static final String MSG_EMOJI_REQUIRED = "이모지는 필수입니다.";
	private static final String MSG_EMOJI_NOT_SUPPORTED = "지원하지 않는 이모지입니다.";
	private static final String MSG_TARGET_NOT_FOUND = "반응을 남길 인증을 찾을 수 없습니다.";

	@LocalServerPort
	private int port;

	@Autowired
	private CrewRepositoryPort crewRepositoryPort;

	@Autowired
	private VerificationRepositoryPort verificationRepositoryPort;

	@Autowired
	private ReactionJpaRepository reactionJpaRepository;

	@Autowired
	private UserRepositoryPort userRepositoryPort;

	@Autowired
	private TransactionTemplate transactionTemplate;

	@Test
	@DisplayName("E3 연타 — 같은 유저가 같은 이모지(LIKE)를 2회 PUT하면 200 · reactions 행 1개 · count 1")
	void addReaction_sameEmojiTwice_upsertsSingleRowWithCountOne() {
		String userId = "reaction-api-user1";
		String verificationId = givenApprovedVerificationWithCrewMember(userId);

		putReaction(userId, verificationId, "LIKE");
		Response second = putReaction(userId, verificationId, "LIKE");

		assertThat(second.statusCode()).isEqualTo(200);
		assertThat(reactionJpaRepository.countByVerificationId(verificationId)).isEqualTo(1);

		List<Map<String, Object>> reactions = second.jsonPath().getList("data.reactions");
		assertThat(reactions).hasSize(1);
		assertThat(reactions.get(0).get("emojiType")).isEqualTo("LIKE");
		assertThat(((Number) reactions.get(0).get("count")).intValue()).isEqualTo(1);
	}

	@Test
	@DisplayName("E1-d — 리액션이 달린 인증이 취소된 뒤 DELETE하면 200이고 요약은 빈 배열이다")
	void removeReaction_onCancelledVerification_returnsOkWithEmptySummary() {
		// ⚠️ 남의 리액션이 **남아 있는 상태**여야 한다. 내 것만 두면 DELETE 가 그걸 지워서
		// 빈 배열이 필터와 무관하게 참이 되고(2026-08-07 실험으로 확인), 그러면 E4 와 같아진다.
		// 남의 행이 남아야 "취소된 인증의 요약은 항상 비어 있다"(step2 §3)가 실제로 검증된다.
		String userId = "reaction-api-user2";
		String other = "reaction-api-user2b";
		String crewId = givenCrewWithLeader(userId);
		givenMember(crewId, other);
		String verificationId = givenApprovedVerification(crewId, userId);
		putReaction(userId, verificationId, "LIKE");
		putReaction(other, verificationId, "LIKE");
		assertThat(reactionJpaRepository.countByVerificationId(verificationId))
				.as("사전 조건: 리액션 2건 — 내 것을 지워도 1건이 남아야 필터가 검증된다")
				.isEqualTo(2);
		// cancelIfApproved 는 @Modifying 이라 호출자 트랜잭션이 필요하다 — API 테스트는 트랜잭션 밖이다
		transactionTemplate.execute(status -> verificationRepositoryPort.cancelIfApproved(verificationId));

		Response response = deleteReaction(userId, verificationId);

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(reactionJpaRepository.countByVerificationId(verificationId))
				.as("남의 리액션 행은 그대로 남아 있다 — 아래 빈 배열은 삭제가 아니라 필터의 결과여야 한다")
				.isEqualTo(1);
		assertThat(response.jsonPath().getList("data.reactions"))
				.as("비노출 인증의 반응은 응답 바디에도 실리지 않는다(step2 §5 불변식 4)")
				.isEmpty();
	}

	@Test
	@DisplayName("리액션 2건 중 내 것만 DELETE하면 응답 요약이 count 1 · reactedByMe false 로 갱신된다")
	void removeReaction_withOtherUsersReaction_reflectsDeletionInResponseBody() {
		// derived delete(③)와 네이티브 요약 조회(④)의 flush 순서를 잠근다.
		// 여기가 깨지면 프로덕션 삭제를 @Modifying(flushAutomatically = true) 네이티브로 바꿔야 한다는 신호다.
		String owner = "reaction-api-user3";
		String other = "reaction-api-user4";
		String crewId = givenCrewWithLeader(owner);
		givenMember(crewId, other);
		String verificationId = givenApprovedVerification(crewId, owner);

		putReaction(owner, verificationId, "LIKE");
		putReaction(other, verificationId, "LIKE");

		Response response = deleteReaction(owner, verificationId);

		assertThat(response.statusCode()).isEqualTo(200);
		List<Map<String, Object>> reactions = response.jsonPath().getList("data.reactions");
		assertThat(reactions).hasSize(1);
		assertThat(((Number) reactions.get(0).get("count")).intValue()).isEqualTo(1);
		assertThat(reactions.get(0).get("reactedByMe")).isEqualTo(false);
	}

	@Test
	@DisplayName("E4 — 남긴 리액션이 없어도 DELETE는 200이다(멱등)")
	void removeReaction_withoutExistingReaction_isIdempotent() {
		String userId = "reaction-api-user5";
		String verificationId = givenApprovedVerificationWithCrewMember(userId);

		Response response = deleteReaction(userId, verificationId);

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.jsonPath().getList("data.reactions")).isEmpty();
	}

	@Test
	@DisplayName("S006 — 비활성 이모지로 PUT하면 400이고 메시지가 enum 이름이 아니다")
	void addReaction_withInactiveEmoji_returns400WithResolvedMessage() {
		String userId = "reaction-api-user6";
		String verificationId = givenApprovedVerificationWithCrewMember(userId);

		Response response = putReaction(userId, verificationId, "FIRE");

		assertThat(response.statusCode()).isEqualTo(400);
		String message = response.jsonPath().getString("error.message");
		assertThat(message).isNotEqualTo("EMOJI_NOT_SUPPORTED").isEqualTo(MSG_EMOJI_NOT_SUPPORTED);
	}

	@Test
	@DisplayName("S003 — emojiType이 비어 있거나 누락이면 400이고 메시지가 enum 이름이 아니다")
	void addReaction_withBlankEmoji_returns400WithResolvedMessage() {
		String userId = "reaction-api-user7";
		String verificationId = givenApprovedVerificationWithCrewMember(userId);

		Response blank = putReaction(userId, verificationId, "");
		Response missing = putReactionWithBody(userId, verificationId, "{}");

		assertThat(blank.statusCode()).isEqualTo(400);
		assertThat(blank.jsonPath().getString("error.message"))
				.isNotEqualTo("EMOJI_REQUIRED").isEqualTo(MSG_EMOJI_REQUIRED);
		// 필드 누락은 Jackson 이 null 을 바인딩하는 다른 경로다 — 400 이 아니라 500 이 나올 수 있다
		assertThat(missing.statusCode()).isEqualTo(400);
		assertThat(missing.jsonPath().getString("error.message"))
				.isNotEqualTo("EMOJI_REQUIRED").isEqualTo(MSG_EMOJI_REQUIRED);
	}

	@Test
	@DisplayName("S005 — 취소된 인증과 미존재 인증에 PUT하면 같은 404가 나온다(사유 미구분)")
	void addReaction_onCancelledVerification_returns404WithResolvedMessage() {
		String userId = "reaction-api-user8";
		String crewId = givenCrewWithLeader(userId);
		String verificationId = givenCancelledVerification(crewId, userId);

		Response cancelled = putReaction(userId, verificationId, "LIKE");
		// 불변식 5 — 서버는 취소와 미존재를 구분하지 않는다. 한쪽만 검증하면 그 '구분 안 함'이 안 잠긴다
		Response missing = putReaction(userId, "VRFY-does-not-exist", "LIKE");

		assertThat(cancelled.statusCode()).isEqualTo(404);
		assertThat(cancelled.jsonPath().getString("error.message"))
				.isNotEqualTo("REACTION_TARGET_NOT_FOUND").isEqualTo(MSG_TARGET_NOT_FOUND);
		assertThat(missing.statusCode()).as("미존재도 같은 404·같은 코드여야 한다").isEqualTo(404);
		assertThat(missing.jsonPath().getString("error.code"))
				.isEqualTo(cancelled.jsonPath().getString("error.code"));
	}

	@Test
	@DisplayName("PUT 200 응답 data 가 §6 계약 4필드(emojiType·count·reactedByMe·users)를 담는다")
	void addReaction_returnsUpdatedSummaryMatchingResponseContract() {
		// FE 가 피드 재조회 없이 카드를 갱신하는 근거다 — count 만으로는 버튼 상태(reactedByMe)와 닉네임이 안 잠긴다
		String userId = "reaction-api-user9";
		String verificationId = givenApprovedVerificationWithCrewMember(userId);

		Response response = putReaction(userId, verificationId, "LIKE");

		assertThat(response.statusCode()).isEqualTo(200);
		List<Map<String, Object>> reactions = response.jsonPath().getList("data.reactions");
		assertThat(reactions).hasSize(1);
		assertThat(reactions.get(0).get("emojiType")).isEqualTo("LIKE");
		assertThat(((Number) reactions.get(0).get("count")).intValue()).isEqualTo(1);
		assertThat(reactions.get(0).get("reactedByMe")).isEqualTo(true);

		List<Map<String, Object>> users = response.jsonPath().getList("data.reactions[0].users");
		assertThat(users).hasSize(1);
		assertThat(users.get(0).get("userId")).isEqualTo(userId);
		assertThat(users.get(0).get("nickname")).isEqualTo(nicknameOf(userId));
	}

	// ===== 시드 헬퍼 =====

	/** 크루 + 리더 멤버 + APPROVED 인증 — 단일 유저 시나리오용 */
	private String givenApprovedVerificationWithCrewMember(String userId) {
		String crewId = givenCrewWithLeader(userId);
		return givenApprovedVerification(crewId, userId);
	}

	/**
	 * ACTIVE 크루 + 리더 멤버 생성.
	 * {@code Crew.create}는 startDate가 미래여야 하는 신규-생성 검증을 거쳐 진행 중 크루를 표현할 수 없으므로
	 * DB 복원용 {@code Crew.of}를 쓴다(선례: {@code CreateVerificationServiceSlotDeadlineIntegrationTest}).
	 */
	private String givenCrewWithLeader(String userId) {
		saveUser(userId);
		String crewId = IdGenerator.generate("CREW");
		Crew crew = Crew.of(
				crewId, userId, "리액션 API 테스트 크루", "목표", "인증 내용",
				VerificationType.TEXT, 10, 1, CrewStatus.ACTIVE,
				LocalDate.now(), LocalDate.now().plusDays(3), false,
				Crew.generateInviteCode(), LocalDateTime.now(), LocalTime.of(23, 59, 59),
				CrewCategory.ETC, CrewVisibility.PRIVATE, 0L, List.of());
		crewRepositoryPort.save(crew);
		crewRepositoryPort.saveMember(CrewMember.createLeader(userId, crewId));
		return crewId;
	}

	/** 크루에 일반 멤버 추가 — 멤버십 검증을 통과해야 리액션을 남길 수 있다 */
	private void givenMember(String crewId, String userId) {
		saveUser(userId);
		crewRepositoryPort.saveMember(CrewMember.createMember(userId, crewId));
	}

	private String givenApprovedVerification(String crewId, String userId) {
		Verification verification = Verification.createText(
				"CHAL-reaction-api", userId, crewId, "인증", LocalDate.now(), 1, 1);
		return verificationRepositoryPort.save(verification).getId();
	}

	private String givenCancelledVerification(String crewId, String userId) {
		Verification verification = Verification.of(
				IdGenerator.generate("VRFY"), "CHAL-reaction-api", userId, crewId, null, null, "취소된 인증",
				VerificationStatus.CANCELLED, 0, LocalDate.now(), 1, 1,
				ReviewStatus.NOT_REQUIRED, LocalDateTime.now());
		return verificationRepositoryPort.save(verification).getId();
	}

	/** 요약 쿼리가 닉네임 때문에 users 를 JOIN 한다 — 유저 행이 없으면 리액션이 요약에서 사라진다 */
	private void saveUser(String userId) {
		userRepositoryPort.save(User.of(userId, "KAKAO", userId + "@test.com", nicknameOf(userId),
				null, null, null, LocalDateTime.now(), LocalDateTime.now(), null, 0));
	}

	/** userId 와 다른 값이어야 한다 — 같으면 users[].nickname 단언이 user_id 를 되읽어도 통과한다 */
	private static String nicknameOf(String userId) {
		return "닉네임-" + userId;
	}

	// ===== HTTP 헬퍼 =====

	/** 리액션 PUT 호출 — 요청마다 port를 지정해 RestAssured 정적 설정에 기대지 않는다 */
	private Response putReaction(String userId, String verificationId, String emojiType) {
		return putReactionWithBody(userId, verificationId, "{\"emojiType\": \"" + emojiType + "\"}");
	}

	private Response putReactionWithBody(String userId, String verificationId, String body) {
		return RestAssured.given()
				.port(port)
				.contentType("application/json")
				.header("X-User-Id", userId)
				.body(body)
				.when()
				.put("/verifications/{verificationId}/reactions", verificationId);
	}

	private Response deleteReaction(String userId, String verificationId) {
		return RestAssured.given()
				.port(port)
				.header("X-User-Id", userId)
				.when()
				.delete("/verifications/{verificationId}/reactions", verificationId);
	}
}
