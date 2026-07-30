package com.triagain.support.domain.vo;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class NotificationMessageTemplate {

	private static final List<String> REMINDER_MESSAGES = List.of(
			"오늘도 작심삼일 한 걸음! 인증하러 갈까요?",
			"크루원들이 기다리고 있어요!",
			"아직 늦지 않았어요! 인증하러 가볼까요?"
	);

	private static final List<String> CREW_START_MESSAGES = List.of(
			"함께 작심삼일을 만들어가요!",
			"오늘부터 3일! 함께 시작해요"
	);

	private static final List<String> CHALLENGE_SUCCESS_MESSAGES = List.of(
			"3일 연속 인증 성공! 대단해요!",
			"작심삼일 달성! 다음 사이클도 도전해보세요!"
	);

	private static final List<String> CHALLENGE_FAILED_MESSAGES = List.of(
			"아쉽지만 다음에 다시 도전해요!",
			"괜찮아요, 다시 시작하면 돼요!"
	);

	private static final List<String> CREW_FIRST_VERIFICATION_MESSAGES = List.of(
			"{nickname}님이 오늘 우리 크루 첫 번째 인증자예요! 이어서 가볼까요?",
			"오늘의 선두주자는 {nickname}님! 나도 작심삼일 한 걸음 가요",
			"{nickname}님이 먼저 출발했어요. 크루원들과 함께 가요!",
			"오늘 우리 크루 첫 인증이 떴어요! 크루 시동 걸렸어요",
			"누군가 오늘 첫 번째로 인증했어요. 나도 이어서 가볼까요?",
			"우리 크루 오늘도 시작됐어요! 인증하러 가볼까요?"
	);

	private NotificationMessageTemplate() {
	}

	/** 리마인더 메시지 랜덤 선택 — title에 크루명 prefix 포함 */
	public static NotificationMessage reminder(String crewName) {
		String content = randomPick(REMINDER_MESSAGES);
		return new NotificationMessage(crewName + " | 인증 마감 임박!", content);
	}

	/** 크루 시작 메시지 랜덤 선택 — title에 크루명 prefix 포함 */
	public static NotificationMessage crewStarted(String crewName) {
		String content = randomPick(CREW_START_MESSAGES);
		return new NotificationMessage(crewName + " | 크루 시작!", content);
	}

	/** 챌린지 성공 메시지 — 작심삼일 달성 */
	public static NotificationMessage challengeSuccess(String crewName) {
		String content = randomPick(CHALLENGE_SUCCESS_MESSAGES);
		return new NotificationMessage(crewName + " | 축하해요! 작심삼일 달성!", content);
	}

	/** 챌린지 실패 메시지 — 미인증으로 실패 */
	public static NotificationMessage challengeFailed(String crewName) {
		String content = randomPick(CHALLENGE_FAILED_MESSAGES);
		return new NotificationMessage(crewName + " | 아쉽지만 다음에 다시 도전!", content);
	}

	/** 첫 인증 모닝콜 메시지 — title에 크루명 prefix, content는 {nickname} 치환(없는 줄은 no-op) */
	public static NotificationMessage crewFirstVerification(String crewName, String nickname) {
		String content = randomPick(CREW_FIRST_VERIFICATION_MESSAGES).replace("{nickname}", nickname);
		return new NotificationMessage(crewName + " | 오늘 첫 인증 등장!", content);
	}

	private static String randomPick(List<String> messages) {
		return messages.get(ThreadLocalRandom.current().nextInt(messages.size()));
	}

	public record NotificationMessage(String title, String content) {}
}
