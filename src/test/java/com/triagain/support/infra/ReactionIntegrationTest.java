package com.triagain.support.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import com.triagain.common.exception.BusinessException;
import com.triagain.common.exception.ErrorCode;
import com.triagain.common.util.IdGenerator;
import com.triagain.crew.domain.model.Crew;
import com.triagain.crew.domain.model.CrewMember;
import com.triagain.crew.domain.vo.CrewCategory;
import com.triagain.crew.domain.vo.CrewStatus;
import com.triagain.crew.domain.vo.CrewVisibility;
import com.triagain.crew.domain.vo.VerificationType;
import com.triagain.crew.port.out.CrewRepositoryPort;
import com.triagain.support.application.AddReactionService;
import com.triagain.support.port.in.AddReactionUseCase.AddReactionCommand;
import com.triagain.support.port.out.ReactionRepositoryPort;
import com.triagain.support.port.out.ReactionRepositoryPort.ReactionRow;
import com.triagain.user.domain.model.User;
import com.triagain.user.port.out.UserRepositoryPort;
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

	@Autowired
	private CrewRepositoryPort crewRepositoryPort;

	@Autowired
	private UserRepositoryPort userRepositoryPort;

	@Autowired
	private ReactionRepositoryPort reactionRepositoryPort;

	@Test
	@DisplayName("RED-1b — CANCELLED 인증에 upsert 시도해도 reactions 행이 생기지 않는다")
	void addReaction_onCancelledVerification_createsNoRow() {
		// given
		String crewId1 = givenCrewWithMember("react-it-user1", "RCTIT1");
		Verification cancelled = save(cancelledVerification("react-it-user1", crewId1));

		// when — 취소된 인증이라 S005 로 막힌다(E1-a)
		assertThatThrownBy(() -> addReactionService.addReaction(
				new AddReactionCommand(cancelled.getId(), "react-it-user1", "LIKE")))
				.isInstanceOf(BusinessException.class)
				.hasMessageContaining(ErrorCode.REACTION_TARGET_NOT_FOUND.name());

		// then — 차단만이 아니라 행이 실제로 안 생겼는지가 이 테스트의 몫이다
		assertThat(reactionJpaRepository.countByVerificationId(cancelled.getId())).isZero();
	}

	@Test
	@DisplayName("RED-2 — 같은 유저가 다른 이모지로 2회 upsert하면 행 1개·마지막 이모지만 남는다")
	@Transactional
	void addReaction_sameUserDifferentEmojiTwice_upsertsSingleRowWithLatestEmoji() {
		// given
		String crewId2 = givenCrewWithMember("react-it-user2", "RCTIT2");
		Verification approved = save(approvedVerification("react-it-user2", crewId2));
		String userId = "react-it-user2";

		// when — 같은 유저가 LIKE → FIRE 순으로 upsert.
		// 서비스가 아니라 포트를 직접 호출한다: 활성 세트가 LIKE 하나뿐이라(EmojiType) 서비스 경로로는
		// '다른 이모지로 교체'에 도달할 수 없다(S006). 여기서 잠그는 건 upsert 의 교체 의미론이다.
		// 유저가 실제로 밟는 중복 경로(같은 이모지 연타)는 ReactionApiTest 가 API 계층에서 잠근다.
		LocalDateTime now = LocalDateTime.now();
		reactionRepositoryPort.upsertIfVerificationActive(
				IdGenerator.generate("RCTN"), approved.getId(), userId, "LIKE", now);
		reactionRepositoryPort.upsertIfVerificationActive(
				IdGenerator.generate("RCTN"), approved.getId(), userId, "FIRE", now);

		// then
		assertThat(reactionJpaRepository.countByVerificationId(approved.getId())).isEqualTo(1);
		// 엔티티 게터를 늘리지 않고 기존 요약 projection 으로 읽는다(엔티티는 스키마 선언 전용)
		List<ReactionRow> rows = reactionRepositoryPort.findRowsByVerificationIdIn(List.of(approved.getId()));
		assertThat(rows).hasSize(1);
		assertThat(rows.get(0).getEmoji()).isEqualTo("FIRE");
	}

	private Verification save(Verification verification) {
		return verificationRepositoryPort.save(verification);
	}

	/**
	 * 크루·리더 멤버·유저 행을 실제로 만든다.
	 * <p>
	 * 문자열 crewId 만으로는 부족하다 — PUT 흐름 ③이 CrewMembershipPort.validateMembership 을 태우고,
	 * 요약 쿼리는 닉네임 때문에 users 를 JOIN 한다. 스켈레톤 시절엔 두 경로가 없어 드러나지 않았다.
	 */
	private String givenCrewWithMember(String userId, String inviteCode) {
		userRepositoryPort.save(User.of(userId, "KAKAO", userId + "@test.com", userId,
				null, null, null, LocalDateTime.now(), LocalDateTime.now(), null, 0));

		String crewId = IdGenerator.generate("CREW");
		crewRepositoryPort.save(Crew.of(
				crewId, userId, "리액션 통합 테스트 크루", "목표", "인증 내용",
				VerificationType.TEXT, 10, 1, CrewStatus.ACTIVE,
				LocalDate.now(), LocalDate.now().plusDays(3), false,
				inviteCode, LocalDateTime.now(), LocalTime.of(23, 59, 59),
				CrewCategory.ETC, CrewVisibility.PRIVATE, 0L, List.of()));
		crewRepositoryPort.saveMember(CrewMember.createLeader(userId, crewId));
		return crewId;
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
