package com.triagain.support.infra;

import com.triagain.support.port.out.FcmTokenCleanupPort;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
@Slf4j
public class FcmTokenCleanupAdapter implements FcmTokenCleanupPort {

    private final EntityManager entityManager;

    /** 무효 FCM 토큰 제거 — users 테이블 직접 UPDATE (cross-BC) */
    @Override
    @Transactional
    public void clearFcmToken(String userId) {
        int updated = entityManager.createNativeQuery(
                        "UPDATE users SET fcm_token = NULL WHERE id = :userId AND fcm_token IS NOT NULL")
                .setParameter("userId", userId)
                .executeUpdate();

        if (updated > 0) {
            log.info("무효 FCM 토큰 제거 완료: userId={}", userId);
        }
    }
}