package com.triagain.verification.infra;

import com.triagain.common.exception.BusinessException;
import com.triagain.common.exception.ErrorCode;
import com.triagain.crew.domain.model.Challenge;
import com.triagain.crew.domain.vo.ChallengeStatus;
import com.triagain.crew.port.out.ChallengeRepositoryPort;
import com.triagain.verification.port.out.ChallengePort.ActiveChallengeInfo;
import com.triagain.verification.port.out.ChallengePort.ChallengeInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ChallengeClientAdapterTest {

    @Mock
    private ChallengeRepositoryPort challengeRepositoryPort;

    @InjectMocks
    private ChallengeClientAdapter challengeClientAdapter;

    private static Challenge inProgressChallenge(String id) {
        return Challenge.of(id, "user-1", "crew-1", 1, 3, 1,
                ChallengeStatus.IN_PROGRESS, LocalDate.now(),
                LocalDateTime.now().plusDays(3), LocalDateTime.now());
    }

    @Test
    @DisplayName("recordCompletion 정상 — completedDays 증가 + save 호출")
    void recordCompletion_success() {
        // Given
        String challengeId = "CHAL-001";
        Challenge challenge = inProgressChallenge(challengeId);
        given(challengeRepositoryPort.findById(challengeId)).willReturn(Optional.of(challenge));

        // When
        challengeClientAdapter.recordCompletion(challengeId);

        // Then
        assertThat(challenge.getCompletedDays()).isEqualTo(2);
        verify(challengeRepositoryPort).save(challenge);
    }

    @Test
    @DisplayName("recordCompletion 존재하지 않는 challengeId → CHALLENGE_NOT_FOUND 예외")
    void recordCompletion_challengeNotFound() {
        // Given
        String challengeId = "CHAL-999";
        given(challengeRepositoryPort.findById(challengeId)).willReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> challengeClientAdapter.recordCompletion(challengeId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CHALLENGE_NOT_FOUND);
    }

    @Test
    @DisplayName("findChallengeById 정상 — ChallengeInfo DTO 반환")
    void findChallengeById_success() {
        // Given
        String challengeId = "CHAL-001";
        Challenge challenge = inProgressChallenge(challengeId);
        given(challengeRepositoryPort.findById(challengeId)).willReturn(Optional.of(challenge));

        // When
        Optional<ChallengeInfo> result = challengeClientAdapter.findChallengeById(challengeId);

        // Then
        assertThat(result).isPresent();
        ChallengeInfo info = result.get();
        assertThat(info.id()).isEqualTo(challengeId);
        assertThat(info.userId()).isEqualTo("user-1");
        assertThat(info.crewId()).isEqualTo("crew-1");
        assertThat(info.completedDays()).isEqualTo(1);
        assertThat(info.targetDays()).isEqualTo(3);
    }

    @Test
    @DisplayName("findActiveByUserIdAndCrewId 활성 챌린지 존재 → ActiveChallengeInfo 반환")
    void findActiveByUserIdAndCrewId_found() {
        // Given
        String userId = "user-1";
        String crewId = "crew-1";
        Challenge challenge = inProgressChallenge("CHAL-001");
        given(challengeRepositoryPort.findByUserIdAndCrewIdAndStatus(userId, crewId, ChallengeStatus.IN_PROGRESS))
                .willReturn(Optional.of(challenge));

        // When
        Optional<ActiveChallengeInfo> result = challengeClientAdapter.findActiveByUserIdAndCrewId(userId, crewId);

        // Then
        assertThat(result).isPresent();
        ActiveChallengeInfo info = result.get();
        assertThat(info.id()).isEqualTo("CHAL-001");
        assertThat(info.status()).isEqualTo("IN_PROGRESS");
        assertThat(info.completedDays()).isEqualTo(1);
        assertThat(info.targetDays()).isEqualTo(3);
    }

    @Test
    @DisplayName("findActiveByUserIdAndCrewId 활성 챌린지 없음 → empty 반환")
    void findActiveByUserIdAndCrewId_notFound() {
        // Given
        String userId = "user-1";
        String crewId = "crew-1";
        given(challengeRepositoryPort.findByUserIdAndCrewIdAndStatus(userId, crewId, ChallengeStatus.IN_PROGRESS))
                .willReturn(Optional.empty());

        // When
        Optional<ActiveChallengeInfo> result = challengeClientAdapter.findActiveByUserIdAndCrewId(userId, crewId);

        // Then
        assertThat(result).isEmpty();
    }
}
