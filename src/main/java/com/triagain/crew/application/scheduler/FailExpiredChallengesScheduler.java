package com.triagain.crew.application.scheduler;

import com.triagain.common.domain.DeadLetter;
import com.triagain.common.domain.DeadLetterTaskType;
import com.triagain.common.port.out.DeadLetterRepositoryPort;
import com.triagain.common.scheduler.ChunkProcessingResult;
import com.triagain.common.scheduler.ChunkProcessor;
import com.triagain.common.scheduler.FailedItem;
import com.triagain.crew.domain.model.Challenge;
import com.triagain.crew.domain.vo.ChallengeStatus;
import com.triagain.crew.port.out.ChallengeRepositoryPort;
import com.triagain.crew.port.out.CrewRepositoryPort;
import com.triagain.crew.port.out.NotificationPort;
import com.triagain.crew.port.out.NotificationPort.ChallengeFailedInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class FailExpiredChallengesScheduler {

    private static final int CHUNK_SIZE = 50;

    private final ChallengeRepositoryPort challengeRepositoryPort;
    private final CrewRepositoryPort crewRepositoryPort;
    private final NotificationPort notificationPort;
    private final ChunkProcessor chunkProcessor;
    private final DeadLetterRepositoryPort deadLetterRepositoryPort;

    /**
     * 마감 초과 챌린지 실패 처리 — 5분마다 전량 스캔 (Phase 1, 500명 규모 기준 안전).
     * 윈도우+보정 이중 구조는 후속 과제 (future-considerations.md 2026-04-09 참조).
     */
    @Scheduled(fixedDelay = 300_000)
    public void failExpiredChallenges() {
        List<Challenge> expired = challengeRepositoryPort.findExpiredWithoutVerification();
        processExpired(expired);
    }

    private void processExpired(List<Challenge> expired) {
        if (expired.isEmpty()) return;

        // 기대치는 "트랜잭션 밖 원본 스냅샷"에서 미리 고정한다 (재시도 경로에서 무효화되지 않도록)
        Map<String, Integer> snapshotDays = expired.stream()
                .collect(Collectors.toMap(Challenge::getId, Challenge::getCompletedDays));
        Map<String, Boolean> failedNow = new HashMap<>();   // id → 이번 실행에서 실제 FAILED 전환했는지

        ChunkProcessingResult<Challenge> result = chunkProcessor.execute(expired, CHUNK_SIZE,
                challenge -> failIfStillExpired(challenge, snapshotDays, failedNow),
                stale -> challengeRepositoryPort.findById(stale.getId()).orElseThrow());

        for (FailedItem<Challenge> failed : result.failedItems()) {
            deadLetterRepositoryPort.save(DeadLetter.of(
                    DeadLetterTaskType.CHALLENGE_FAIL,
                    failed.item().getId(),
                    failed.errorMessage()
            ));
        }

        List<Challenge> actuallyFailed = result.successItems().stream()
                .filter(c -> Boolean.TRUE.equals(failedNow.get(c.getId())))
                .toList();
        List<String> skippedIds = result.successItems().stream()
                .filter(c -> !Boolean.TRUE.equals(failedNow.get(c.getId())))
                .map(Challenge::getId)
                .toList();

        sendFailedNotifications(actuallyFailed);
        logOutcome(expired.size(), actuallyFailed.size(), skippedIds, result.failedCount());
    }

    /** 처리 결과 로깅 — 스킵은 조용히 지나가면 안 되므로 id까지 남기고, 전건 스킵은 경고로 올린다 */
    private void logOutcome(int total, int failedCount, List<String> skippedIds, int errorCount) {
        log.info("챌린지 실패 처리: 전체 {}건, 성공 {}건, 스킵 {}건, 실패 {}건",
                total, failedCount, skippedIds.size(), errorCount);
        if (skippedIds.isEmpty()) {
            return;
        }
        if (failedCount == 0 && errorCount == 0) {
            log.warn("만료 대상 {}건이 전건 스킵됐다 — 개별 경합이 아니라 조회 조건과 CAS 술어 불일치 의심: {}",
                    skippedIds.size(), skippedIds);
        } else {
            log.info("실패 처리 스킵 {}건 — 조회 이후 인증/취소로 대상에서 벗어남: {}",
                    skippedIds.size(), skippedIds);
        }
    }

    /** 조건부 UPDATE로 실패 전환 — 스냅샷 이후 변경된 건은 예외 없이 스킵으로 기록한다 */
    private void failIfStillExpired(Challenge challenge,
                                    Map<String, Integer> snapshotDays, Map<String, Boolean> failedNow) {
        if (challenge.getStatus() != ChallengeStatus.IN_PROGRESS) {
            failedNow.put(challenge.getId(), false);   // rehydrate 결과가 이미 SUCCESS 등 — DeadLetter 오염 방지
            return;
        }
        challenge.fail();                              // 도메인 가드 유지
        int affected = challengeRepositoryPort.failIfUnchanged(
                challenge.getId(), snapshotDays.get(challenge.getId()));
        failedNow.put(challenge.getId(), affected == 1);   // 재시도 시 마지막 시도 결과로 덮어씀
    }

    /** 챌린지 실패 알림 발송 — 크루명 배치 조회 후 NotificationPort에 위임 */
    private void sendFailedNotifications(List<Challenge> failedChallenges) {
        if (failedChallenges.isEmpty()) return;

        try {
            List<String> crewIds = failedChallenges.stream()
                    .map(Challenge::getCrewId)
                    .distinct()
                    .toList();

            Map<String, String> crewNameMap = crewRepositoryPort.findAllByIds(crewIds).stream()
                    .collect(Collectors.toMap(
                            com.triagain.crew.domain.model.Crew::getId,
                            com.triagain.crew.domain.model.Crew::getName
                    ));

            List<ChallengeFailedInfo> infos = failedChallenges.stream()
                    .map(c -> new ChallengeFailedInfo(
                            c.getUserId(), c.getCrewId(),
                            crewNameMap.getOrDefault(c.getCrewId(), "크루")))
                    .toList();

            // TODO: 챌린지 실패 알림 임시 비활성화 — 유저 알림 on/off 설정 UI 구현 후 복원
            // notificationPort.sendChallengeFailedNotifications(infos);
        } catch (Exception e) {
            log.warn("챌린지 실패 알림 발송 중 오류: {}", e.getMessage());
        }
    }
}
