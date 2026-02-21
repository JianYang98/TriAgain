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
    CREW_NOT_RECRUITING(400, "CR003", "모집 중인 크루가 아닙니다."),
    CREW_ALREADY_JOINED(400, "CR004", "이미 참여 중인 크루입니다."),
    CHALLENGE_NOT_FOUND(404, "CR005", "챌린지를 찾을 수 없습니다."),
    INVALID_INVITE_CODE(400, "CR006", "유효하지 않은 초대 코드입니다."),

    // Verification
    VERIFICATION_NOT_FOUND(404, "V001", "인증을 찾을 수 없습니다."),
    VERIFICATION_DEADLINE_EXCEEDED(400, "V002", "인증 마감 시간이 지났습니다."),
    VERIFICATION_ALREADY_EXISTS(400, "V003", "이미 해당 날짜에 인증이 존재합니다."),
    UPLOAD_SESSION_NOT_FOUND(404, "V004", "업로드 세션을 찾을 수 없습니다."),
    UPLOAD_SESSION_NOT_COMPLETED(400, "V005", "업로드 세션이 완료되지 않았습니다."),
    UPLOAD_SESSION_EXPIRED(400, "V006", "업로드 세션이 만료되었습니다."),

    // Moderation
    REPORT_NOT_FOUND(404, "M001", "신고를 찾을 수 없습니다."),
    REPORT_ALREADY_EXISTS(400, "M002", "이미 해당 인증에 신고를 접수했습니다."),
    REVIEW_NOT_FOUND(404, "M003", "검토를 찾을 수 없습니다."),
    REPORT_ALREADY_PROCESSED(400, "M004", "이미 처리된 신고입니다."),

    // Support
    NOTIFICATION_NOT_FOUND(404, "S001", "알림을 찾을 수 없습니다."),
    REACTION_NOT_FOUND(404, "S002", "반응을 찾을 수 없습니다.");

    private final int status;
    private final String code;
    private final String message;
}
