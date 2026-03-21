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

    private NotificationMessageTemplate() {}

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

    private static String randomPick(List<String> messages) {
        return messages.get(ThreadLocalRandom.current().nextInt(messages.size()));
    }

    public record NotificationMessage(String title, String content) {}
}
