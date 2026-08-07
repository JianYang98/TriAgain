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
import com.triagain.crew.domain.model.Challenge;
import com.triagain.crew.domain.model.Crew;
import com.triagain.crew.domain.model.CrewMember;
import com.triagain.crew.domain.vo.ChallengeStatus;
import com.triagain.crew.domain.vo.CrewCategory;
import com.triagain.crew.domain.vo.CrewStatus;
import com.triagain.crew.domain.vo.CrewVisibility;
import com.triagain.crew.domain.vo.VerificationType;
import com.triagain.crew.port.out.ChallengeRepositoryPort;
import com.triagain.crew.port.out.CrewRepositoryPort;
import com.triagain.support.application.AddReactionService;
import com.triagain.support.port.in.AddReactionUseCase.AddReactionCommand;
import com.triagain.support.port.out.ReactionRepositoryPort;
import com.triagain.support.port.out.ReactionRepositoryPort.ReactionRow;
import com.triagain.user.domain.model.User;
import com.triagain.user.port.out.UserRepositoryPort;
import com.triagain.verification.application.CancelVerificationService;
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

	@Autowired
	private ChallengeRepositoryPort challengeRepositoryPort;

	@Autowired
	private CancelVerificationService cancelVerificationService;

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

	@Test
	@Transactional
	@DisplayName("E1-b — 인증을 취소해도 reactions 행은 남고, 요약 조회에서만 사라진다")
	void cancelVerification_keepsReactionRowButHidesItFromSummary() {
		// 취소를 서비스로 태운다(CANCELLED 행을 직접 저장하지 않는다) — 잠그려는 사고가 "취소 경로가
		// 리액션을 지우는 것"이라, 취소를 흉내 내면 그 사고를 원리적으로 못 잡는다.
		// API 대신 서비스인 이유: 단언이 전부 DB 레벨이고 HTTP 계약은 §7-2 가 따로 잠근다.
		String userId = "reaction-e1b-user";
		String crewId = givenCrewWithMember(userId, "RIT004");
		Challenge challenge = givenInProgressChallenge(userId, crewId);
		Verification approved = save(approvedVerification(userId, crewId, challenge.getId()));

		reactionRepositoryPort.upsertIfVerificationActive(
				IdGenerator.generate("RCTN"), approved.getId(), userId, "LIKE", LocalDateTime.now());
		assertThat(reactionJpaRepository.countByVerificationId(approved.getId()))
				.as("사전 조건: 리액션 1건 — 0이면 아래 잔존 단언이 무의미하다")
				.isEqualTo(1);

		cancelVerificationService.cancelVerification(approved.getId(), userId);

		assertThat(reactionJpaRepository.countByVerificationId(approved.getId()))
				.as("취소는 리액션을 지우지 않는다 — 하드 삭제로 바뀌면 여기가 터진다")
				.isEqualTo(1);
		assertThat(reactionRepositoryPort.findRowsByVerificationIdIn(List.of(approved.getId())))
				.as("요약 조회는 APPROVED 인증만 싣는다")
				.isEmpty();
	}

	@Test
	@Transactional
	@DisplayName("E9 — 복수 인증을 한 번에 조회하면 인증별로 갈리고, created_at → user_id 순으로 정렬된다")
	void findRows_forMultipleVerifications_splitsByVerificationAndKeepsOrderContract() {
		// api-spec crew.md 가 FE 에 "표시 순서는 서버 정렬을 따르며 클라이언트에서 재정렬하지 않는다"고
		// 약속했다. Postgres 는 ORDER BY 없이는 순서를 보장하지 않으므로 그 약속을 여기서 잠근다.
		// 인증 소유자를 둘로 나눈다 — uk_verifications_user_crew_date_active 가 같은 유저의
		// 같은 날 APPROVED 인증 2건을 막는다(피드도 원래 여러 크루원의 인증이 섞인 모습이다)
		String ownerA = "reaction-e9-ownerA";
		String ownerB = "reaction-e9-ownerB";
		String crewId = givenCrewWithMember(ownerA, "RIT005");
		givenMember(ownerB, crewId);
		Verification first = save(approvedVerification(ownerA, crewId));
		Verification second = save(approvedVerification(ownerB, crewId));

		LocalDateTime base = LocalDateTime.now();
		// 1차 키(created_at) — 늦게 만든 유저를 먼저 넣어 "삽입 순서"로는 못 맞추게 한다
		react(first.getId(), "reaction-e9-late", crewId, base.plusMinutes(1));
		react(first.getId(), "reaction-e9-early", crewId, base);
		// 2차 키(user_id) — created_at 을 같게 줘 동률을 만든다. 여기가 비결정성이 실제로 물리는 자리다
		react(second.getId(), "reaction-e9-bbb", crewId, base);
		react(second.getId(), "reaction-e9-aaa", crewId, base);

		List<ReactionRow> rows = reactionRepositoryPort.findRowsByVerificationIdIn(
				List.of(first.getId(), second.getId()));

		assertThat(rows).as("인증 2건 × 유저 2명").hasSize(4);
		assertThat(rowUserIds(rows, first.getId()))
				.as("1차 키: created_at 오름차순")
				.containsExactly("reaction-e9-early", "reaction-e9-late");
		assertThat(rowUserIds(rows, second.getId()))
				.as("2차 키: created_at 동률이면 user_id 오름차순")
				.containsExactly("reaction-e9-aaa", "reaction-e9-bbb");
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

	@Test
	@Transactional
	@DisplayName("E10 — 리액션을 남긴 유저가 탈퇴해도 리액션은 요약에 남는다(닉네임만 익명화)")
	void withdrawnUser_reactionSurvivesInSummary() {
		// 이 테스트의 목적은 감시선이다 — 요약 쿼리가 users 를 INNER JOIN 하므로, 탈퇴가 익명화가 아니라
		// 하드 삭제(또는 user_id 기준 리액션 삭제)로 바뀌는 순간 리액션이 요약에서 통째로 사라지고 여기가 터진다.
		String owner = "reaction-e10-owner";
		String reactor = "reaction-e10-reactor";
		String crewId = givenCrewWithMember(owner, "RIT006");
		// ⚠️ 탈퇴자를 solo-leader 로 두지 않는다 — deleteCrewWithAllData 가 크루를 통째로 지워 다른 이유로 실패한다
		givenMember(reactor, crewId);
		Verification approved = save(approvedVerification(owner, crewId));
		reactionRepositoryPort.upsertIfVerificationActive(
				IdGenerator.generate("RCTN"), approved.getId(), reactor, "LIKE", LocalDateTime.now());

		User withdrawn = userRepositoryPort.findById(reactor).orElseThrow();
		withdrawn.withdraw();
		userRepositoryPort.save(withdrawn);

		List<ReactionRow> rows = reactionRepositoryPort.findRowsByVerificationIdIn(List.of(approved.getId()));

		// 핵심 단언 — 리액션이 살아 있는가
		assertThat(rows).as("탈퇴는 익명화라 리액션은 남는다").hasSize(1);
		assertThat(rows.get(0).getUserId()).isEqualTo(reactor);
		// 부수 단언 — 라벨은 User.java:71 리터럴과 결합돼 있다(의도된 결합)
		assertThat(rows.get(0).getNickname()).as("닉네임만 익명화된다").isEqualTo("탈퇴한 사용자");
	}

	/** 크루 멤버 유저를 만들고 그 인증에 리액션을 남긴다 — created_at 을 직접 지정한다(정렬 계약 검증용) */
	private void react(String verificationId, String userId, String crewId, LocalDateTime createdAt) {
		givenMember(userId, crewId);
		reactionRepositoryPort.upsertIfVerificationActive(
				IdGenerator.generate("RCTN"), verificationId, userId, "LIKE", createdAt);
	}

	/** 요약 쿼리가 닉네임 때문에 users 를 JOIN 한다 — 유저 행이 없으면 리액션이 요약에서 사라진다 */
	private void givenMember(String userId, String crewId) {
		userRepositoryPort.save(User.of(userId, "KAKAO", userId + "@test.com", userId,
				null, null, null, LocalDateTime.now(), LocalDateTime.now(), null, 0));
		crewRepositoryPort.saveMember(CrewMember.createMember(userId, crewId));
	}

	private List<String> rowUserIds(List<ReactionRow> rows, String verificationId) {
		return rows.stream()
				.filter(row -> verificationId.equals(row.getVerificationId()))
				.map(ReactionRow::getUserId)
				.toList();
	}

	private Verification approvedVerification(String userId, String crewId) {
		return approvedVerification(userId, crewId, CHALLENGE_ID);
	}

	/** 취소 경로는 challenges 행을 실제로 읽으므로 그 경로만 실 challengeId 를 넘긴다 */
	private Verification approvedVerification(String userId, String crewId, String challengeId) {
		return Verification.createText(challengeId, userId, crewId, "인증", LocalDate.now(), 1, 1);
	}

	/** 취소는 completedDays 를 역연산하므로 1 이상이어야 한다(0이면 revertCompletion 이 0행) */
	private Challenge givenInProgressChallenge(String userId, String crewId) {
		return challengeRepositoryPort.save(Challenge.of(
				IdGenerator.generate("CHAL"), userId, crewId, 1, 3, 1, ChallengeStatus.IN_PROGRESS,
				LocalDate.now().minusDays(1), LocalDateTime.now().plusDays(3), LocalDateTime.now()));
	}

	private Verification cancelledVerification(String userId, String crewId) {
		return Verification.of(
				IdGenerator.generate("VRFY"), CHALLENGE_ID, userId, crewId, null, null, "취소된 인증",
				VerificationStatus.CANCELLED, 0, LocalDate.now(), 1, 1,
				ReviewStatus.NOT_REQUIRED, LocalDateTime.now());
	}
}
