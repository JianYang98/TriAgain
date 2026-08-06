package com.triagain.support.infra;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.triagain.common.util.IdGenerator;
import com.triagain.support.application.AddReactionService;
import com.triagain.support.port.in.AddReactionUseCase.AddReactionCommand;
import com.triagain.verification.domain.model.Verification;
import com.triagain.verification.domain.vo.ReviewStatus;
import com.triagain.verification.domain.vo.VerificationStatus;
import com.triagain.verification.port.out.VerificationRepositoryPort;

/**
 * 리액션 upsert의 실 DB 의미론 — RED 테스트(step1-biz-logic.md §7-3). 포트 교정 전이라
 * {@code AddReactionService}는 스켈레톤(저장 없이 빈 요약 반환)이다. RED-2는 그래서 단언 실패로
 * 떨어지는 것이 목적이고, RED-1b는 스켈레톤이 아무것도 안 넣으므로 지금도 통과한다 — 포트 교정 후
 * 두 테스트 모두 그린이 되어야 한다.
 */
class ReactionIntegrationTest extends ReactionIntegrationTestBase {

	private static final String CHALLENGE_ID = "CHAL-reaction-it";

	@Autowired
	private AddReactionService addReactionService;

	@Autowired
	private VerificationRepositoryPort verificationRepositoryPort;

	@Autowired
	private ReactionJpaRepository reactionJpaRepository;

	@Test
	@DisplayName("RED-1b — CANCELLED 인증에 upsert 시도해도 reactions 행이 생기지 않는다")
	void addReaction_onCancelledVerification_createsNoRow() {
		// given
		Verification cancelled = save(cancelledVerification("react-it-user1", "react-it-crew1"));

		// when
		addReactionService.addReaction(new AddReactionCommand(cancelled.getId(), "react-it-user1", "LIKE"));

		// then
		assertThat(reactionJpaRepository.countByVerificationId(cancelled.getId())).isZero();
	}

	@Test
	@DisplayName("RED-2 — 같은 유저가 다른 이모지로 2회 upsert하면 행 1개·마지막 이모지만 남는다")
	void addReaction_sameUserDifferentEmojiTwice_upsertsSingleRowWithLatestEmoji() {
		// given
		Verification approved = save(approvedVerification("react-it-user2", "react-it-crew2"));
		String userId = "react-it-user2";

		// when — 같은 유저가 LIKE → FIRE 순으로 upsert
		addReactionService.addReaction(new AddReactionCommand(approved.getId(), userId, "LIKE"));
		addReactionService.addReaction(new AddReactionCommand(approved.getId(), userId, "FIRE"));

		// then
		assertThat(reactionJpaRepository.countByVerificationId(approved.getId())).isEqualTo(1);
		String emoji = reactionJpaRepository.findByVerificationIdAndUserId(approved.getId(), userId)
				.orElseThrow()
				.toDomain()
				.getEmoji();
		assertThat(emoji).isEqualTo("FIRE");
	}

	private Verification save(Verification verification) {
		return verificationRepositoryPort.save(verification);
	}

	private Verification approvedVerification(String userId, String crewId) {
		return Verification.createText(CHALLENGE_ID, userId, crewId, "인증", LocalDate.now(), 1, 1);
	}

	private Verification cancelledVerification(String userId, String crewId) {
		return Verification.of(
				IdGenerator.generate("VRFY"), CHALLENGE_ID, userId, crewId, null, null, "취소된 인증",
				VerificationStatus.CANCELLED, 0, LocalDate.now(), 1, 1,
				ReviewStatus.NOT_REQUIRED, LocalDateTime.now());
	}
}
