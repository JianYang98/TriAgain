package com.triagain.support.domain.vo;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class NotificationMessageTemplate {

    private static final List<String> REMINDER_MESSAGES = List.of(
            "오늘도 작심삼일 한 걸음! 인증하러 갈까요?",
            "{crewName} 크루원들이 기다리고 있어요!",
            "아직 늦지 않았어요! 인증하러 가볼까요?"
    );

    private static final List<String> CREW_START_MESSAGES = List.of(
            "{crewName} 크루가 시작됐어요! 첫 인증 도전해볼까요?",
            "오늘부터 3일! {crewName}에서 함께 시작해요"
    );

    private NotificationMessageTemplate() {}

    /** 리마인더 메시지 랜덤 선택 — crewName 치환 */
    public static NotificationMessage reminder(String crewName) {
        String content = randomPick(REMINDER_MESSAGES).replace("{crewName}", crewName);
        return new NotificationMessage("인증 마감 임박!", content);
    }

    /** 크루 시작 메시지 랜덤 선택 — crewName 치환 */
    public static NotificationMessage crewStarted(String crewName) {
        String content = randomPick(CREW_START_MESSAGES).replace("{crewName}", crewName);
        return new NotificationMessage("크루 시작!", content);
    }

    private static String randomPick(List<String> messages) {
        return messages.get(ThreadLocalRandom.current().nextInt(messages.size()));
    }

    public record NotificationMessage(String title, String content) {}
}
