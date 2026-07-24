package com.triagain.crew.infra;

import com.triagain.common.port.out.DeadLetterRepositoryPort;
import com.triagain.common.scheduler.ChunkProcessor;
import com.triagain.common.util.IdGenerator;
import com.triagain.crew.application.scheduler.FailExpiredChallengesScheduler;
import com.triagain.crew.domain.model.Challenge;
import com.triagain.crew.domain.model.Crew;
import com.triagain.crew.domain.vo.ChallengeStatus;
import com.triagain.crew.port.out.ChallengeRepositoryPort;
import com.triagain.crew.port.out.NotificationPort;
import com.triagain.e2e.E2eTestBase;
import com.triagain.verification.domain.model.Verification;
import com.triagain.verification.port.out.VerificationRepositoryPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.AdditionalAnswers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * D14-b: 만료 스케줄러 lost update 방지 회귀 테스트 — 실제 PostgreSQL(Testcontainers) 사용.
 * <p>
 * {@link FailExpiredChallengesScheduler}가 트랜잭션 밖에서 읽은 스냅샷으로 전체 엔티티를 save()하면,
 * 스냅샷 조회~UPDATE 사이에 유저가 인증을 커밋해도 낡은 completed_days + FAILED로 덮어써
 * 유저의 성공이 조용히 실패로 뒤집힌다(T1). 조건부 UPDATE(compare-and-set)인
 * {@link ChallengeJpaRepository#failIfUnchanged}로 이를 방지한다.
 */
@Tag("e2e")
class ChallengeFailIfUnchangedIntegrationTest extends E2eTestBase {

    @Autowired
    private VerificationRepositoryPort verificationRepositoryPort;

    @Autowired
    private NotificationPort notificationPort;

    @Autowired
    private ChunkProcessor chunkProcessor;

    @Autowired
    private DeadLetterRepositoryPort deadLetterRepositoryPort;

    @Autowired
    private FailExpiredChallengesScheduler scheduler;

    @Test
    @DisplayName("T1: 스냅샷 조회 후 유저가 인증을 커밋하면 스케줄러가 그 성공을 덮어쓰지 않는다")
    void schedulerSnapshotRace_doesNotOverwriteUserSuccessCommittedAfterSnapshot() {
        // Given — 슬롯(오늘-8일)이 마감을 훌쩍 넘긴 IN_PROGRESS 챌린지 (completed_days=2, targetDays=3)
        String userId = "e2e-cas-race-user";
        createUser(userId);
        Crew crew = createActiveCrew(userId);

        LocalDate startDate = LocalDate.now().minusDays(10);
        int snapshotCompletedDays = 2;
        LocalDate slot = startDate.plusDays(snapshotCompletedDays);

        Challenge challenge = createExpiredChallenge(
                userId, crew.getId(), snapshotCompletedDays, ChallengeStatus.IN_PROGRESS, startDate);

        // 스냅샷 조회를 가로채 "실 조회 → 유저 인증 커밋 → 낡은 스냅샷 반환" 순서를 결정적으로 만든다.
        // 실 빈이 @Repository 프록시일 수 있어 spy+callRealMethod 대신 delegatesTo로 감싼다.
        ChallengeRepositoryPort interleavingPort = Mockito.mock(
                ChallengeRepositoryPort.class,
                AdditionalAnswers.delegatesTo(challengeRepositoryPort));

        Mockito.doAnswer(invocation -> {
            List<Challenge> snapshot = challengeRepositoryPort.findExpiredWithoutVerification();

            // 인터리브 — 스냅샷 확보 직후, 유저가 슬롯 인증을 커밋해 completed_days=3, SUCCESS로 전이
            verificationRepositoryPort.save(Verification.createText(
                    challenge.getId(), userId, crew.getId(), "완료", slot, 1, 1));
            Challenge committing = challengeRepositoryPort.findById(challenge.getId()).orElseThrow();
            committing.recordCompletion();
            challengeRepositoryPort.save(committing);

            return snapshot;
        }).when(interleavingPort).findExpiredWithoutVerification();

        FailExpiredChallengesScheduler raceScheduler = new FailExpiredChallengesScheduler(
                interleavingPort, crewRepositoryPort, notificationPort, chunkProcessor, deadLetterRepositoryPort);

        // When — 스케줄러 실행 (낡은 스냅샷 기준으로 조건부 UPDATE 시도)
        raceScheduler.failExpiredChallenges();

        // Then — DB 행은 유저가 커밋한 성공 상태 그대로여야 한다 (실 DB 조회로 검증, mock verify 아님)
        Challenge actual = challengeRepositoryPort.findById(challenge.getId()).orElseThrow();
        assertThat(actual.getCompletedDays())
                .as("스케줄러가 낡은 스냅샷(completed_days=2)으로 유저의 성공 커밋(completed_days=3)을 덮어쓰면 안 된다")
                .isEqualTo(3);
        assertThat(actual.getStatus())
                .as("유저가 3일차 인증을 커밋해 SUCCESS로 전이한 상태가 유지되어야 한다")
                .isEqualTo(ChallengeStatus.SUCCESS);
    }

    @Test
    @DisplayName("T2: 간섭 없이 실행하면 만료 챌린지는 FAILED로 전환되고 completed_days는 불변이다")
    void noInterleave_expiredChallenge_becomesFailedWithUnchangedCompletedDays() {
        // Given
        String userId = "e2e-cas-plain-user";
        createUser(userId);
        Crew crew = createActiveCrew(userId);

        int completedDays = 2;

        Challenge challenge = createExpiredChallenge(
                userId, crew.getId(), completedDays, ChallengeStatus.IN_PROGRESS, LocalDate.now().minusDays(10));

        // When
        scheduler.failExpiredChallenges();

        // Then
        Challenge actual = challengeRepositoryPort.findById(challenge.getId()).orElseThrow();
        assertThat(actual.getStatus()).isEqualTo(ChallengeStatus.FAILED);
        assertThat(actual.getCompletedDays()).isEqualTo(completedDays);

        // 상태 전이가 도메인(fail())과 네이티브 SQL에 이중 표현돼 있다.
        // 도메인이 만드는 결과와 실 DB 행이 전 컬럼 동일해야 두 표현이 갈라지지 않는다.
        challenge.fail();   // 시드에 쓴 도메인 객체(IN_PROGRESS 스냅샷)에 도메인 전이를 적용
        assertThat(actual)
                .usingRecursiveComparison()
                .ignoringFields("createdAt")   // DB 왕복 시 타임스탬프 정밀도 차이
                .isEqualTo(challenge);
    }

    @Test
    @DisplayName("T3: completed_days가 기대치와 다르면 실패 전환하지 않고 0을 반환한다")
    @Transactional
    void failIfUnchanged_completedDaysMismatch_returnsZeroAndKeepsInProgress() {
        // Given
        String userId = "e2e-cas-mismatch-user";
        createUser(userId);
        Crew crew = createActiveCrew(userId);

        Challenge challenge = createChallenge(userId, crew.getId(), 2);

        // When — 기대치(1)가 실제 completed_days(2)와 불일치
        int affected = challengeRepositoryPort.failIfUnchanged(challenge.getId(), 1);

        // Then
        assertThat(affected).isEqualTo(0);
        Challenge actual = challengeRepositoryPort.findById(challenge.getId()).orElseThrow();
        assertThat(actual.getStatus()).isEqualTo(ChallengeStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("T4: 이미 FAILED인 챌린지에 재시도해도 0을 반환한다 (멱등)")
    @Transactional
    void failIfUnchanged_alreadyFailed_returnsZeroIdempotently() {
        // Given
        String userId = "e2e-cas-idempotent-user";
        createUser(userId);
        Crew crew = createActiveCrew(userId);

        Challenge challenge = Challenge.of(
                IdGenerator.generate("CHAL"), userId, crew.getId(), 1,
                3, 2, ChallengeStatus.FAILED,
                LocalDate.now().minusDays(10), LocalDateTime.now().plusDays(3), LocalDateTime.now());
        challengeRepositoryPort.save(challenge);

        // When
        int affected = challengeRepositoryPort.failIfUnchanged(challenge.getId(), 2);

        // Then
        assertThat(affected).isEqualTo(0);
    }

    // ─────────────────────────────────────────────────────────────
    // 테스트 헬퍼
    // ─────────────────────────────────────────────────────────────

    /** 마감(startDate+3일 23:59:59)이 이미 지난 챌린지 생성 — 스케줄러 픽업 대상 픽스처에 사용 */
    private Challenge createExpiredChallenge(
            String userId, String crewId, int completedDays, ChallengeStatus status, LocalDate startDate) {
        LocalDateTime deadline = startDate.plusDays(3).atTime(23, 59, 59);
        Challenge challenge = Challenge.of(
                IdGenerator.generate("CHAL"), userId, crewId, 1,
                3, completedDays, status, startDate, deadline, LocalDateTime.now());
        challengeRepositoryPort.save(challenge);
        return challenge;
    }
}
