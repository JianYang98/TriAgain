package com.triagain.verification.application;

import com.triagain.common.exception.BusinessException;
import com.triagain.common.exception.ErrorCode;
import com.triagain.common.domain.DeadlinePolicy;
import com.triagain.verification.domain.model.UploadSession;
import com.triagain.verification.domain.model.Verification;
import com.triagain.verification.port.in.CreateVerificationUseCase;
import com.triagain.verification.port.out.ChallengePort;
import com.triagain.verification.port.out.ChallengePort.ChallengeInfo;
import com.triagain.verification.port.out.CrewPort;
import com.triagain.verification.port.out.CrewPort.CrewVerificationWindowInfo;
import com.triagain.common.port.out.StoragePort;
import com.triagain.verification.application.event.ChallengeSuccessEvent;
import com.triagain.verification.application.event.CrewFirstVerificationEvent;
import com.triagain.verification.port.out.UploadSessionRepositoryPort;
import com.triagain.verification.port.out.VerificationRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Service
@RequiredArgsConstructor
public class CreateVerificationService implements CreateVerificationUseCase {

    private final VerificationRepositoryPort verificationRepositoryPort;
    private final UploadSessionRepositoryPort uploadSessionRepositoryPort;
    private final ChallengePort challengePort;
    private final CrewPort crewPort;
    private final StoragePort storagePort;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    @Override
    @Transactional
    public VerificationResult createVerification(CreateVerificationCommand command) {
        // crewId가 있으면 먼저 멤버십 검증 — 비회원의 크루 상태 노출 + 챌린지 생성 방지
        if (command.crewId() != null) {
            crewPort.validateMembership(command.crewId(), command.userId());
        }

        // photo 인증이고 uploadSessionId가 있을 때, challenge resolve 전에 session cross-crew 선검증
        UploadSession preloadedSession = null;
        if (command.uploadSessionId() != null) {
            preloadedSession = uploadSessionRepositoryPort
                    .findByIdAndUserId(command.uploadSessionId(), command.userId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.UPLOAD_SESSION_NOT_FOUND));

            String targetCrewId = command.crewId();
            if (targetCrewId != null && preloadedSession.getCrewId() != null
                    && !preloadedSession.getCrewId().equals(targetCrewId)) {
                throw new BusinessException(ErrorCode.UPLOAD_SESSION_CREW_MISMATCH);
            }
        }

        ChallengeInfo challenge = resolveChallenge(command);

        if (!"IN_PROGRESS".equals(challenge.status())) {
            throw new BusinessException(ErrorCode.CHALLENGE_NOT_IN_PROGRESS);
        }

        // challengeId-only: challenge에서 crewId를 알아낸 후 검증
        if (command.crewId() == null) {
            crewPort.validateMembership(challenge.crewId(), command.userId());
        }

        // [신규] verificationType·deadlineTime을 1회 조회로 통합 (구 getVerificationType 대체, 위치 상향)
        CrewVerificationWindowInfo windowInfo = crewPort.getCrewVerificationWindowInfo(challenge.crewId());

        // [신규] 앵커 — 사진은 세션의 requestedAt(서버 기록, 조작 불가), 텍스트는 요청 처리 시각
        LocalDateTime anchor = (preloadedSession != null)
                ? preloadedSession.getRequestedAt()
                : LocalDateTime.now(clock);

        // [신규] 슬롯 산출 + 하한 검증(V003) — targetDate는 생성 시각이 아닌 챌린지의 미인증 당일(슬롯)
        LocalDate targetDate = resolveSlot(challenge, anchor);

        if (verificationRepositoryPort.existsByUserIdAndCrewIdAndTargetDate(
                command.userId(), challenge.crewId(), targetDate)) {
            throw new BusinessException(ErrorCode.VERIFICATION_ALREADY_EXISTS);
        }

        if ("PHOTO".equals(windowInfo.verificationType()) && command.uploadSessionId() == null) {
            throw new BusinessException(ErrorCode.PHOTO_REQUIRED);
        }

        // [신규] 상한 검증(V002) — min(슬롯 일일마감, 사이클 마감) + grace. PHOTO_REQUIRED 다음, 생성 분기 이전
        validateDeadline(challenge, windowInfo.deadlineTime(), targetDate, anchor);

        Verification verification;

        if (command.uploadSessionId() != null) {
            verification = createPhotoVerification(preloadedSession, command, challenge, targetDate);
        } else {
            verification = createTextVerification(command, challenge, targetDate);
        }

        Verification saved = verificationRepositoryPort.save(verification);

        // [신규] 첫 인증 판정 — save() 직후 count: 방금 저장된 row 포함 1건이면 오늘 첫 인증.
        // 주의: count()는 반드시 save() 이후에 와야 한다 (순서 변경 금지).
        // 동시 첫인증(race) 시 양쪽 tx 모두 count==1 → 2회 이벤트 발행 가능 (best-effort).
        // → 리스너 내 existsCrewFirstVerificationOnDate 멱등 가드가 최종 방어선.
        if (verificationRepositoryPort.countByCrewIdAndTargetDate(challenge.crewId(), targetDate) == 1) {
            eventPublisher.publishEvent(
                    new CrewFirstVerificationEvent(command.userId(), challenge.crewId(), targetDate));
        }

        boolean challengeSuccess = challengePort.recordCompletion(challenge.id());
        if (challengeSuccess) {
            // 트랜잭션 커밋 후 알림 발송 — DB 커넥션 점유 시간 단축 + 외부 호출 트랜잭션 분리
            eventPublisher.publishEvent(new ChallengeSuccessEvent(
                    command.userId(), challenge.crewId()));
        }

        return new VerificationResult(
                saved.getId(),
                saved.getChallengeId(),
                saved.getUserId(),
                saved.getCrewId(),
                saved.getImageUrl(),
                saved.getTextContent(),
                saved.getStatus(),
                saved.getReviewStatus(),
                saved.getReportCount(),
                saved.getTargetDate(),
                saved.getCreatedAt()
        );
    }

    /** 챌린지 결정 — challengeId/crewId 조합에 따라 조회 또는 생성 */
    private ChallengeInfo resolveChallenge(CreateVerificationCommand command) {
        if (command.challengeId() != null && command.crewId() != null) {
            ChallengeInfo challenge = challengePort.findChallengeById(command.challengeId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.CHALLENGE_NOT_FOUND));
            if (!challenge.crewId().equals(command.crewId())) {
                throw new BusinessException(ErrorCode.CHALLENGE_CREW_MISMATCH);
            }
            return challenge;
        }

        if (command.challengeId() != null) {
            return challengePort.findChallengeById(command.challengeId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.CHALLENGE_NOT_FOUND));
        }

        return challengePort.findOrCreateActiveChallenge(command.userId(), command.crewId());
    }

    /**
     * 슬롯 산출 + 하한 검증 — targetDate는 인증 생성 시각이 아닌 챌린지의 미인증 당일(슬롯)로 귀속한다.
     * 앵커 날짜가 슬롯보다 이르면(=해당 슬롯을 이미 인증한 뒤 재제출) 하루치를 몰아 채우는 것으로 간주해 거부한다.
     */
    private LocalDate resolveSlot(ChallengeInfo challenge, LocalDateTime anchor) {
        LocalDate slot = DeadlinePolicy.slotFor(challenge.startDate(), challenge.completedDays());
        if (anchor.toLocalDate().isBefore(slot)) {
            throw new BusinessException(ErrorCode.VERIFICATION_ALREADY_EXISTS);
        }
        return slot;
    }

    /** 슬롯 유효 상한 검증 — min(슬롯 일일마감, 사이클 마감) + grace period 초과 시 거부 */
    private void validateDeadline(ChallengeInfo challenge, LocalTime deadlineTime,
                                   LocalDate slot, LocalDateTime anchor) {
        LocalDateTime effective = DeadlinePolicy.effectiveSlotDeadline(slot, deadlineTime, challenge.deadline());
        if (!DeadlinePolicy.isWithinDeadline(anchor, effective)) {
            throw new BusinessException(ErrorCode.VERIFICATION_DEADLINE_EXCEEDED);
        }
    }

    /** 사진 인증 생성 — 선조회된 session을 재사용하여 중복 DB 조회 방지 */
    private Verification createPhotoVerification(UploadSession session,
                                                  CreateVerificationCommand command,
                                                  ChallengeInfo challenge,
                                                  LocalDate targetDate) {
        // session.crewId와 challenge.crewId 일치 검증 — command.crewId() 제공 여부와 무관
        if (session.getCrewId() != null && !session.getCrewId().equals(challenge.crewId())) {
            throw new BusinessException(ErrorCode.UPLOAD_SESSION_CREW_MISMATCH);
        }

        if (!session.isCompleted()) {
            if (session.isPending()) {
                throw new BusinessException(ErrorCode.UPLOAD_SESSION_NOT_COMPLETED);
            }
            throw new BusinessException(ErrorCode.UPLOAD_SESSION_EXPIRED);
        }

        String imageUrl = storagePort.getImageUrl(session.getImageKey());

        return Verification.createPhoto(
                challenge.id(),
                command.userId(),
                challenge.crewId(),
                session.getId(),
                imageUrl,
                command.textContent(),
                targetDate,
                challenge.completedDays() + 1
        );
    }

    private Verification createTextVerification(CreateVerificationCommand command,
                                                 ChallengeInfo challenge,
                                                 LocalDate targetDate) {
        return Verification.createText(
                challenge.id(),
                command.userId(),
                challenge.crewId(),
                command.textContent(),
                targetDate,
                challenge.completedDays() + 1
        );
    }
}
