package com.triagain.verification.port.in;

import java.time.LocalDate;
import java.util.Optional;

/** Moderation Context가 인증 상태를 제어할 때 사용하는 Input Port */
public interface VerificationModerationUseCase {

    /** 인증 숨김 처리 — 신고 임계치 도달 시 자동 숨김에 사용 */
    void hideVerification(String verificationId);

    /** 인증 거부 처리 — 관리자 검토 후 부적절 판정 시 사용 */
    void rejectVerification(String verificationId);

    /** 인증 승인 처리 — 관리자 검토 후 적절 판정 시 사용 */
    void approveVerification(String verificationId);

    /** 신고 횟수 증가 — 신고 접수 시 사용, 증가 후 현재 횟수 반환 */
    int incrementReportCount(String verificationId);

    /** 인증 정보 조회 — 신고/검토 시 인증 메타데이터 참조에 사용 */
    Optional<VerificationInfoDto> findById(String verificationId);

    record VerificationInfoDto(
            String id,
            String challengeId,
            String userId,
            String crewId,
            int reportCount,
            LocalDate targetDate
    ) {}
}
