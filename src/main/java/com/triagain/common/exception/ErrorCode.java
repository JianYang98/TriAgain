package com.triagain.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // Common
    INVALID_INPUT(400, "C001", "잘못된 입력값입니다."),
    INTERNAL_SERVER_ERROR(500, "C002", "서버 내부 오류가 발생했습니다."),
    RESOURCE_NOT_FOUND(404, "C003", "요청한 리소스를 찾을 수 없습니다."),

    // User
    USER_NOT_FOUND(404, "U001", "사용자를 찾을 수 없습니다."),

    // Crew
    CREW_NOT_FOUND(404, "CR001", "크루를 찾을 수 없습니다."),
    CREW_FULL(400, "CR002", "크루 정원이 가득 찼습니다."),

    // Verification
    VERIFICATION_DEADLINE_EXCEEDED(400, "V001", "인증 마감 시간이 지났습니다."),

    // Moderation
    REPORT_NOT_FOUND(404, "M001", "신고를 찾을 수 없습니다.");

    private final int status;
    private final String code;
    private final String message;
}
