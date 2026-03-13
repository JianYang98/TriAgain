package com.triagain.verification.infra;

import com.triagain.crew.port.in.ChallengeQueryUseCase;
import com.triagain.crew.port.in.ChallengeQueryUseCase.ChallengeInfoDto;
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
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ChallengeClientAdapterTest {

    @Mock
    private ChallengeQueryUseCase challengeQueryUseCase;

    @InjectMocks
    private ChallengeClientAdapter challengeClientAdapter;

    private static ChallengeInfoDto inProgressDto(String id) {
        return new ChallengeInfoDto(id, "user-1", "crew-1", 1, 3, 1,
                "IN_PROGRESS", LocalDate.now(),
                LocalDateTime.now().plusDays(3), LocalDateTime.now());
    }

    @Test
    @DisplayName("recordCompletion 정상 — ChallengeQueryUseCase에 위임")
    void recordCompletion_success() {
        // Given
        String challengeId = "CHAL-001";

        // When
        challengeClientAdapter.recordCompletion(challengeId);

        // Then
        verify(challengeQueryUseCase).recordCompletion(challengeId);
    }

    @Test
    @DisplayName("findChallengeById 정상 — ChallengeInfo DTO 변환 확인")
    void findChallengeById_success() {
        // Given
        String challengeId = "CHAL-001";
        given(challengeQueryUseCase.findById(challengeId)).willReturn(Optional.of(inProgressDto(challengeId)));

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
        assertThat(info.status()).isEqualTo("IN_PROGRESS");
    }

    @Test
    @DisplayName("findActiveByUserIdAndCrewId 활성 챌린지 존재 → ActiveChallengeInfo 반환")
    void findActiveByUserIdAndCrewId_found() {
        // Given
        String userId = "user-1";
        String crewId = "crew-1";
        given(challengeQueryUseCase.findActiveByUserIdAndCrewId(userId, crewId))
                .willReturn(Optional.of(inProgressDto("CHAL-001")));

        // When
        Optional<ActiveChallengeInfo> result = challengeClientAdapter.findActiveByUserIdAndCrewId(userId, crewId);

        // Then
        assertThat(result).isPresent();
        ActiveChallengeInfo info = result.get();
        assertThat(info.id()).isEqualTo("CHAL-001");
        assertThat(info.status()).isEqualTo("IN_PROGRESS");
        assertThat(info.completedDays()).isEqualTo(1);
        assertThat(info.targetDays()).isEqualTo(3);
        assertThat(info.deadline()).isNotNull();
    }

    @Test
    @DisplayName("findActiveByUserIdAndCrewId 활성 챌린지 없음 → empty 반환")
    void findActiveByUserIdAndCrewId_notFound() {
        // Given
        String userId = "user-1";
        String crewId = "crew-1";
        given(challengeQueryUseCase.findActiveByUserIdAndCrewId(userId, crewId))
                .willReturn(Optional.empty());

        // When
        Optional<ActiveChallengeInfo> result = challengeClientAdapter.findActiveByUserIdAndCrewId(userId, crewId);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findOrCreateActiveChallenge — ChallengeQueryUseCase에 위임")
    void findOrCreateActiveChallenge_delegatesToUseCase() {
        // Given
        String userId = "user-1";
        String crewId = "crew-1";
        given(challengeQueryUseCase.findOrCreateActive(userId, crewId)).willReturn(inProgressDto("CHAL-001"));

        // When
        ChallengeInfo result = challengeClientAdapter.findOrCreateActiveChallenge(userId, crewId);

        // Then
        assertThat(result.id()).isEqualTo("CHAL-001");
        assertThat(result.status()).isEqualTo("IN_PROGRESS");
        verify(challengeQueryUseCase).findOrCreateActive(userId, crewId);
    }

    @Test
    @DisplayName("countCompletedChallenges — ChallengeQueryUseCase에 위임")
    void countCompletedChallenges_delegatesToUseCase() {
        // Given
        String userId = "user-1";
        String crewId = "crew-1";
        given(challengeQueryUseCase.countCompletedChallenges(userId, crewId)).willReturn(5);

        // When
        int result = challengeClientAdapter.countCompletedChallenges(userId, crewId);

        // Then
        assertThat(result).isEqualTo(5);
    }
}
