package com.triagain.crew.application;

import com.triagain.common.exception.BusinessException;
import com.triagain.common.exception.ErrorCode;
import com.triagain.crew.domain.model.Crew;
import com.triagain.crew.domain.model.CrewMember;
import com.triagain.crew.domain.vo.CrewRole;
import com.triagain.crew.domain.vo.CrewStatus;
import com.triagain.crew.domain.vo.CrewVisibility;
import com.triagain.crew.domain.vo.VerificationType;
import com.triagain.crew.port.out.CrewRepositoryPort;
import com.triagain.crew.port.out.UserPort;
import com.triagain.crew.port.out.UserPort.UserProfile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class GetCrewPreviewServiceTest {

    @Mock
    private CrewRepositoryPort crewRepositoryPort;

    @Mock
    private UserPort userPort;

    @InjectMocks
    private GetCrewPreviewService getCrewPreviewService;

    @Test
    @DisplayName("PUBLIC RECRUITING 크루를 미리보기하면 크루 정보와 joinable=true를 반환한다")
    void 공개_모집중_크루_미리보기_성공() {
        // Given
        Crew crew = publicRecruitingCrew(
                LocalDate.now().plusDays(1),
                LocalDate.now().plusDays(14)
        );
        given(crewRepositoryPort.findById("CREW-001")).willReturn(Optional.of(crew));
        given(userPort.findProfilesByIds(List.of("creator-001")))
                .willReturn(Map.of("creator-001", new UserProfile("크루장", null)));

        // When
        var result = getCrewPreviewService.getCrewPreview("CREW-001", "other-user");

        // Then
        assertThat(result.id()).isEqualTo("CREW-001");
        assertThat(result.name()).isEqualTo("테스트 크루");
        assertThat(result.visibility()).isEqualTo(CrewVisibility.PUBLIC);
        assertThat(result.joinable()).isTrue();
        assertThat(result.joinBlockedReason()).isNull();
        assertThat(result.members()).hasSize(1);
        assertThat(result.members().get(0).nickname()).isEqualTo("크루장");
    }

    @Test
    @DisplayName("PRIVATE 크루를 미리보기하면 CREW_NOT_PUBLIC 예외가 발생한다")
    void PRIVATE_크루_미리보기_예외() {
        // Given
        Crew crew = privateRecruitingCrew(
                LocalDate.now().plusDays(1),
                LocalDate.now().plusDays(14)
        );
        given(crewRepositoryPort.findById("CREW-002")).willReturn(Optional.of(crew));

        // When & Then
        assertThatThrownBy(() -> getCrewPreviewService.getCrewPreview("CREW-002", "user-001"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CREW_NOT_PUBLIC);
    }

    @Test
    @DisplayName("존재하지 않는 크루 ID로 미리보기하면 CREW_NOT_FOUND 예외가 발생한다")
    void 존재하지_않는_크루_미리보기_예외() {
        // Given
        given(crewRepositoryPort.findById("NONEXIST")).willReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> getCrewPreviewService.getCrewPreview("NONEXIST", "user-001"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CREW_NOT_FOUND);
    }

    @Test
    @DisplayName("이미 멤버인 유저가 미리보기하면 joinBlockedReason=ALREADY_MEMBER를 반환한다")
    void 이미_멤버인_유저_미리보기() {
        // Given
        Crew crew = publicRecruitingCrew(
                LocalDate.now().plusDays(1),
                LocalDate.now().plusDays(14)
        );
        given(crewRepositoryPort.findById("CREW-001")).willReturn(Optional.of(crew));
        given(userPort.findProfilesByIds(List.of("creator-001")))
                .willReturn(Map.of("creator-001", new UserProfile("크루장", null)));

        // When — creator-001은 이미 멤버
        var result = getCrewPreviewService.getCrewPreview("CREW-001", "creator-001");

        // Then
        assertThat(result.joinable()).isFalse();
        assertThat(result.joinBlockedReason()).isEqualTo("ALREADY_MEMBER");
    }

    @Test
    @DisplayName("정원이 가득 찬 크루를 미리보기하면 joinBlockedReason=CREW_FULL을 반환한다")
    void 정원_초과_크루_미리보기() {
        // Given
        Crew crew = fullPublicRecruitingCrew(
                LocalDate.now().plusDays(1),
                LocalDate.now().plusDays(14)
        );
        List<String> memberIds = crew.getMembers().stream().map(CrewMember::getUserId).toList();
        Map<String, UserProfile> profiles = memberIds.stream()
                .collect(Collectors.toMap(
                        id -> id,
                        id -> new UserProfile("닉네임_" + id, null)
                ));
        given(crewRepositoryPort.findById("CREW-FULL")).willReturn(Optional.of(crew));
        given(userPort.findProfilesByIds(memberIds)).willReturn(profiles);

        // When
        var result = getCrewPreviewService.getCrewPreview("CREW-FULL", "new-user");

        // Then
        assertThat(result.joinable()).isFalse();
        assertThat(result.joinBlockedReason()).isEqualTo("CREW_FULL");
    }

    // --- 헬퍼 메서드 ---

    private static Crew publicRecruitingCrew(LocalDate startDate, LocalDate endDate) {
        CrewMember leader = CrewMember.of("CRMB-1", "creator-001", "CREW-001", CrewRole.LEADER, LocalDateTime.now());
        return Crew.of(
                "CREW-001", "creator-001", "테스트 크루", "목표",
                "인증 내용", VerificationType.TEXT, 10, 1,
                CrewStatus.RECRUITING, startDate, endDate,
                true, "ABC123", LocalDateTime.now(),
                LocalTime.of(23, 59, 59), null, CrewVisibility.PUBLIC, 0L, List.of(leader)
        );
    }

    private static Crew privateRecruitingCrew(LocalDate startDate, LocalDate endDate) {
        CrewMember leader = CrewMember.of("CRMB-1", "creator-001", "CREW-002", CrewRole.LEADER, LocalDateTime.now());
        return Crew.of(
                "CREW-002", "creator-001", "비공개 크루", "목표",
                "인증 내용", VerificationType.TEXT, 10, 1,
                CrewStatus.RECRUITING, startDate, endDate,
                true, "DEF456", LocalDateTime.now(),
                LocalTime.of(23, 59, 59), null, CrewVisibility.PRIVATE, 0L, List.of(leader)
        );
    }

    private static Crew fullPublicRecruitingCrew(LocalDate startDate, LocalDate endDate) {
        CrewMember leader = CrewMember.of("CRMB-1", "leader-001", "CREW-FULL", CrewRole.LEADER, LocalDateTime.now());
        CrewMember member = CrewMember.of("CRMB-2", "member-001", "CREW-FULL", CrewRole.MEMBER, LocalDateTime.now());
        return Crew.of(
                "CREW-FULL", "leader-001", "만원 크루", "목표",
                "인증 내용", VerificationType.TEXT, 2, 2,
                CrewStatus.RECRUITING, startDate, endDate,
                true, "GHI789", LocalDateTime.now(),
                LocalTime.of(23, 59, 59), null, CrewVisibility.PUBLIC, 0L, List.of(leader, member)
        );
    }
}
