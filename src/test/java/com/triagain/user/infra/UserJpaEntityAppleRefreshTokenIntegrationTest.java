package com.triagain.user.infra;

import com.triagain.user.domain.model.User;
import com.triagain.user.port.out.UserRepositoryPort;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AesGcmStringConverter ↔ Hibernate ↔ Spring DI 통합 검증.
 * 단위테스트로는 검증 못 하는 부분: Hibernate가 SpringBeanContainer로부터 Spring 빈 컨버터를 받아
 * 실제 INSERT/SELECT 시 자동 호출하는지 확인.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DirtiesContext
class UserJpaEntityAppleRefreshTokenIntegrationTest {

    @Autowired
    private UserRepositoryPort userRepositoryPort;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("Apple refresh_token이 DB에는 v1: prefix 암호문으로 저장되고 도메인에는 평문으로 복원된다")
    void appleRefreshToken_isEncryptedInDb_andDecryptedOnLoad() {
        // Given
        String userId = "apple-test-user-1";
        String plainRefreshToken = "rt_apple_test_plaintext_xyz";
        User user = User.of(userId, "APPLE", "apple@test.com", "애플유저", null, null,
                plainRefreshToken, LocalDateTime.now(), LocalDateTime.now(), null, 0);

        // When — JPA save (Converter가 자동 암호화)
        userRepositoryPort.save(user);
        entityManager.flush();
        entityManager.clear();

        // Then 1 — 도메인 객체로 다시 읽으면 평문이 복원된다
        User loaded = userRepositoryPort.findById(userId).orElseThrow();
        assertThat(loaded.getAppleRefreshToken()).isEqualTo(plainRefreshToken);

        // Then 2 — DB 컬럼은 v1: prefix 암호문이고 평문이 노출되지 않는다
        Object rawValue = entityManager.createNativeQuery(
                        "SELECT apple_refresh_token FROM users WHERE id = :id")
                .setParameter("id", userId)
                .getSingleResult();
        String dbValue = (String) rawValue;
        assertThat(dbValue).startsWith("v1:");
        assertThat(dbValue).doesNotContain(plainRefreshToken);
    }

    @Test
    @DisplayName("apple_refresh_token이 null이면 DB에도 null로 저장된다 (Apple 외 사용자 호환)")
    void nullAppleRefreshToken_isPersistedAsNull() {
        // Given
        String userId = "kakao-test-user-1";
        User user = User.of(userId, "KAKAO", "k@test.com", "카카오", null, null, null,
                LocalDateTime.now(), LocalDateTime.now(), null, 0);

        // When
        userRepositoryPort.save(user);
        entityManager.flush();
        entityManager.clear();

        // Then
        User loaded = userRepositoryPort.findById(userId).orElseThrow();
        assertThat(loaded.getAppleRefreshToken()).isNull();
    }
}
