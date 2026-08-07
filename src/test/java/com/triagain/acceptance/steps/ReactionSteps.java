package com.triagain.acceptance.steps;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

import com.triagain.acceptance.ScenarioContext;
import com.triagain.acceptance.adapter.FeedTestAdapter;
import com.triagain.acceptance.adapter.ReactionTestAdapter;
import com.triagain.verification.domain.model.Verification;
import com.triagain.verification.port.out.VerificationRepositoryPort;

import io.cucumber.java.Before;
import io.cucumber.java.ko.그러면;
import io.cucumber.java.ko.그리고;
import io.cucumber.java.ko.만일;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;

public class ReactionSteps {

	@LocalServerPort
	private int port;

	@Autowired
	private ScenarioContext scenarioContext;

	@Autowired
	private VerificationRepositoryPort verificationRepositoryPort;

	private ReactionTestAdapter reactionAdapter;
	private FeedTestAdapter feedAdapter;

	@Before
	public void setUp() {
		reactionAdapter = new ReactionTestAdapter(port);
		feedAdapter = new FeedTestAdapter(port);
	}

	// ===== 만일 (Given/When 겸용 — 좋아요를 누르는 행위) =====

	/** 성공·실패 공용 스텝 — 응답만 저장하고 상태코드는 여기서 단언하지 않는다(403·404 시나리오가 그대로 재사용) */
	@만일("{string}이/가 {string}의 인증에 좋아요를 누른다")
	public void 이_인증에_좋아요를_누른다(String actorUserId, String ownerUserId) {
		String verificationId = resolveVerificationId(ownerUserId);
		ExtractableResponse<Response> response = reactionAdapter.addReaction(actorUserId, verificationId, "LIKE");
		scenarioContext.setResponse(response);
	}

	/** 같은 이모지 재탭 = 취소 — 클라이언트는 DELETE로 구현한다(step2-api.md §2) */
	@만일("{string}이 그 좋아요를 다시 누른다")
	public void 그_좋아요를_다시_누른다(String actorUserId) {
		String verificationId = scenarioContext.getVerificationId();
		ExtractableResponse<Response> response = reactionAdapter.removeReaction(actorUserId, verificationId);
		scenarioContext.setResponse(response);
	}

	// ===== 그러면/그리고 (Then) =====

	@그러면("피드에서 그 인증의 좋아요는 {int}개다")
	public void 피드에서_그_인증의_좋아요는_N개다(int expected) {
		assertThat(totalReactionCount(scenarioContext.getVerificationId())).isEqualTo(expected);
	}

	/**
	 * 수정 전용 단언 — "그 인증"(원본 id)으로 세면 **공허하다**: 수정하면 원본은 CANCELLED 라 피드에서
	 * 통째로 사라지고 count 가 0으로 나온다. 리액션이 새 행으로 복사됐어도 그대로 통과한다.
	 * 그래서 <b>대체 인증(새 행)을 직접 찾아</b> 그쪽 reactions 가 비었는지 본다.
	 */
	@그러면("피드에 다시 뜬 {string}의 인증에는 좋아요가 없다")
	public void 피드에_다시_뜬_인증에는_좋아요가_없다(String ownerUserId) {
		String originalId = scenarioContext.getVerificationId();
		Map<String, Object> replacement = feedVerifications(scenarioContext.getUserId()).stream()
				.filter(verification -> ownerUserId.equals(verification.get("userId")))
				.findFirst()
				.orElseThrow(() -> new AssertionError(ownerUserId + "의 대체 인증이 피드에 없다 — 수정이 실패했다"));

		// 원본이 그대로 떠 있으면 아래 단언은 "수정 전 상태"를 보는 셈이라 무의미하다
		assertThat(replacement.get("id")).as("대체 인증은 원본과 다른 행이어야 한다").isNotEqualTo(originalId);
		assertThat(reactionSummaries(replacement)).as("대체 인증에는 좋아요가 따라오지 않는다").isEmpty();
	}

	@그리고("좋아요 누른 사람에 {string}이 표시된다")
	public void 좋아요_누른_사람에_이_표시된다(String nickname) {
		assertThat(reactionUserNicknames(scenarioContext.getVerificationId())).contains(nickname);
	}

	@그리고("{string}에게는 내가 좋아요 한 것으로 보인다")
	public void 에게는_내가_좋아요_한_것으로_보인다(String viewerUserId) {
		Map<String, Object> entry = feedEntry(viewerUserId, scenarioContext.getVerificationId())
				.orElseThrow(() -> new AssertionError("피드에서 대상 인증을 찾을 수 없다"));
		boolean reactedByMe = reactionSummaries(entry).stream()
				.anyMatch(summary -> Boolean.TRUE.equals(summary.get("reactedByMe")));
		assertThat(reactedByMe).isTrue();
	}

	@그러면("피드 어디에서도 그 인증과 좋아요는 보이지 않는다")
	public void 피드_어디에서도_그_인증과_좋아요는_보이지_않는다() {
		assertThat(feedEntry(scenarioContext.getVerificationId())).isEmpty();
	}

	@그러면("피드에서 {string}의 인증에 {string}의 좋아요가 여전히 보인다")
	public void 피드에서_의_인증에_의_좋아요가_여전히_보인다(String ownerUserId, String reactorNickname) {
		String verificationId = resolveVerificationId(ownerUserId);
		assertThat(reactionUserNicknames(verificationId)).contains(reactorNickname);
	}

	// ===== Helper Methods =====

	/** "그 인증"의 id — 캐시돼 있으면 재사용, 없으면(취소 전) 활성 인증을 조회해 캐시한다 */
	private String resolveVerificationId(String ownerUserId) {
		String cached = scenarioContext.getVerificationId();
		if (cached != null) {
			return cached;
		}
		String crewId = scenarioContext.getCrewId();
		Verification verification = verificationRepositoryPort
				.findActiveByUserIdAndCrewIdAndTargetDate(ownerUserId, crewId, LocalDate.now())
				.orElseThrow(() -> new IllegalStateException(ownerUserId + "의 활성 인증을 찾을 수 없다"));
		scenarioContext.setVerificationId(verification.getId());
		return verification.getId();
	}

	private List<Map<String, Object>> feedVerifications(String viewerUserId) {
		return feedAdapter.getFeed(viewerUserId, scenarioContext.getCrewId())
				.jsonPath().getList("data.verifications");
	}

	private Optional<Map<String, Object>> feedEntry(String viewerUserId, String verificationId) {
		return feedVerifications(viewerUserId).stream()
				.filter(verification -> verificationId.equals(verification.get("id")))
				.findFirst();
	}

	/** 취소·수정으로 "그 인증"이 피드에서 사라진 경우도 있다 — 조회 시엔 크루원이면 누구든 상관없어 시나리오 유저를 쓴다 */
	private Optional<Map<String, Object>> feedEntry(String verificationId) {
		return feedEntry(scenarioContext.getUserId(), verificationId);
	}

	@SuppressWarnings("unchecked")
	private List<Map<String, Object>> reactionSummaries(Map<String, Object> feedEntry) {
		Object reactions = feedEntry.get("reactions");
		return reactions == null ? List.of() : (List<Map<String, Object>>) reactions;
	}

	@SuppressWarnings("unchecked")
	private List<Map<String, Object>> reactionUsers(Map<String, Object> reactionSummary) {
		Object users = reactionSummary.get("users");
		return users == null ? List.of() : (List<Map<String, Object>>) users;
	}

	/** 피드에 "그 인증"이 없으면(취소·수정으로 비노출) 좋아요 0개로 취급한다 */
	private int totalReactionCount(String verificationId) {
		return feedEntry(verificationId)
				.map(entry -> reactionSummaries(entry).stream()
						.mapToInt(summary -> ((Number) summary.get("count")).intValue())
						.sum())
				.orElse(0);
	}

	private List<String> reactionUserNicknames(String verificationId) {
		return feedEntry(verificationId)
				.map(entry -> reactionSummaries(entry).stream()
						.flatMap(summary -> reactionUsers(summary).stream())
						.map(user -> (String) user.get("nickname"))
						.toList())
				.orElse(List.of());
	}
}
