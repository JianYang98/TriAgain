package com.triagain.verification.application;

import com.triagain.verification.domain.model.UploadSession;
import com.triagain.verification.port.out.UploadSessionRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExpireUploadSessionScheduler {

    private static final int EXPIRY_MINUTES = 15;

    private final UploadSessionRepositoryPort uploadSessionRepositoryPort;

    /** PENDING 상태 세션 만료 처리 — 15분 경과 시 EXPIRED로 전환 */
    @Scheduled(fixedRate = 300_000)
    @Transactional
    public void expirePendingSessions() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(EXPIRY_MINUTES);
        List<UploadSession> expiredSessions = uploadSessionRepositoryPort.findPendingSessionsBefore(threshold);

        for (UploadSession session : expiredSessions) {
            session.expire();
            uploadSessionRepositoryPort.save(session);
        }

        if (!expiredSessions.isEmpty()) {
            log.info("만료 처리된 업로드 세션: {}건", expiredSessions.size());
        }
    }
}
