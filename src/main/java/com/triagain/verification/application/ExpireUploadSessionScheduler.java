package com.triagain.verification.application;

import com.triagain.verification.domain.model.UploadSession;
import com.triagain.verification.port.out.UploadSessionRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExpireUploadSessionScheduler {

    private static final int EXPIRY_MINUTES = 15;

    private final UploadSessionRepositoryPort uploadSessionRepositoryPort;
    private final TransactionTemplate transactionTemplate;

    /** PENDING 상태 세션 만료 처리 — 15분 경과 시 EXPIRED로 전환 */
    @Scheduled(fixedRate = 300_000)
    public void expirePendingSessions() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(EXPIRY_MINUTES);
        List<UploadSession> expiredSessions = uploadSessionRepositoryPort.findPendingSessionsBefore(threshold);
        if (expiredSessions.isEmpty()) return;

        int successCount = 0;
        List<Long> failedIds = new ArrayList<>();

        for (UploadSession session : expiredSessions) {
            try {
                transactionTemplate.executeWithoutResult(status -> {
                    session.expire();
                    uploadSessionRepositoryPort.save(session);
                });
                successCount++;
            } catch (Exception e) {
                failedIds.add(session.getId());
                log.error("업로드 세션 만료 처리 실패 [sessionId={}]: {}", session.getId(), e.getMessage(), e);
            }
        }

        log.info("업로드 세션 만료 처리 완료: 전체 {}건, 성공 {}건, 실패 {}건{}",
                expiredSessions.size(), successCount, failedIds.size(),
                failedIds.isEmpty() ? "" : " | 실패 ID: " + failedIds);
    }
}
