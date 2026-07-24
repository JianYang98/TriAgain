package com.triagain.crew.infra;

import com.triagain.common.util.IdGenerator;
import com.triagain.crew.domain.model.Challenge;
import com.triagain.crew.domain.model.Crew;
import com.triagain.crew.domain.vo.ChallengeStatus;
import com.triagain.crew.port.out.ChallengeRepositoryPort;
import com.triagain.e2e.E2eTestBase;
import com.triagain.verification.domain.model.Verification;
import com.triagain.verification.port.out.VerificationRepositoryPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T-I1: 실패 판정 스케줄러의 슬롯(target_date = start_date + completed_days) NOT EXISTS 정합 회귀 테스트 —
 * 실제 PostgreSQL(Testcontainers) 사용.
 * <p>
 * 목적: {@link ChallengeJpaRepository#findExpiredWithoutVerification()}의 네이티브 SQL이
 * "슬롯(start_date+completed_days)에 해당하는 인증이 있으면 제외, 없으면 포함"을 실제 DB에서 정확히 수행하는지
 * 검증한다. 이 SDD(grace-targetdate)가 targetDate를 슬롯으로 귀속시키도록 고치는 근거 그 자체 —
 * 스케줄러가 이미 슬롯 기준으로 NOT EXISTS를 판정하고 있으므로, CreateVerificationService가 슬롯이 아닌
 * "생성 시각의 날짜"로 targetDate를 저장하면 이 쿼리와 어긋난다.
 * <p>
 * 마감(cr.deadline_time)+5분을 훌쩍 넘긴 과거 슬롯(10일 전)을 가진 두 챌린지를 만들어 대조한다 —
 * 시각 고정 대신 과거 start_date로 조건을 성립시킨다 (쿼리의 NOW()는 DB 시각).
 */
@Tag("e2e")
class ChallengeExpiredWithoutVerificationIntegrationTest extends E2eTestBase {

    @Autowired
    private VerificationRepositoryPort verificationRepositoryPort;

    @Autowired
    private ChallengeRepositoryPort challengeRepositoryPort;

    @Test
    @DisplayName("슬롯에 해당하는 인증이 존재하는 챌린지는 제외되고, 부재한 챌린지만 만료 목록에 포함된다")
    void findExpiredWithoutVerification_matchesBySlotNotByCreatedDate() {
        // Given — 마감을 훌쩍 넘긴 과거 슬롯(10일 전)을 가진 두 챌린지: 인증 존재/부재
        LocalDate slot = LocalDate.now().minusDays(10);
        LocalDateTime cycleDeadline = slot.plusDays(3).atTime(23, 59, 59);

        String verifiedUserId = "integ-exp-user-verified";
        createUser(verifiedUserId);
        Crew verifiedCrew = createActiveCrew(verifiedUserId);
        Challenge verifiedChallenge = Challenge.of(
                IdGenerator.generate("CHAL"), verifiedUserId, verifiedCrew.getId(), 1,
                3, 0, ChallengeStatus.IN_PROGRESS,
                slot, cycleDeadline, LocalDateTime.now());
        challengeRepositoryPort.save(verifiedChallenge);
        verificationRepositoryPort.save(Verification.createText(
                verifiedChallenge.getId(), verifiedUserId, verifiedCrew.getId(),
                "완료", slot, 1, 1));

        String unverifiedUserId = "integ-exp-user-unverified";
        createUser(unverifiedUserId);
        Crew unverifiedCrew = createActiveCrew(unverifiedUserId);
        Challenge unverifiedChallenge = Challenge.of(
                IdGenerator.generate("CHAL"), unverifiedUserId, unverifiedCrew.getId(), 1,
                3, 0, ChallengeStatus.IN_PROGRESS,
                slot, cycleDeadline, LocalDateTime.now());
        challengeRepositoryPort.save(unverifiedChallenge);

        // When
        List<Challenge> expired = challengeRepositoryPort.findExpiredWithoutVerification();
        List<String> expiredIds = expired.stream().map(Challenge::getId).toList();

        // Then — 슬롯 인증이 존재하는 챌린지는 안 잡히고(①), 부재한 챌린지만 잡힌다(②)
        assertThat(expiredIds)
                .as("슬롯(D)에 인증이 존재하면 NOT EXISTS가 거짓이 되어 만료 목록에서 제외되어야 한다")
                .doesNotContain(verifiedChallenge.getId());
        assertThat(expiredIds)
                .as("슬롯(D)에 인증이 없으면 NOT EXISTS가 참이 되어 만료 목록에 포함되어야 한다")
                .contains(unverifiedChallenge.getId());
    }
}
