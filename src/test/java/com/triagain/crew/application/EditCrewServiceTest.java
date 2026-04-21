package com.triagain.crew.application;

import com.triagain.common.exception.BusinessException;
import com.triagain.common.exception.ErrorCode;
import com.triagain.crew.domain.model.Crew;
import com.triagain.crew.domain.model.CrewMember;
import com.triagain.crew.domain.vo.CrewRole;
import com.triagain.crew.domain.vo.CrewStatus;
import com.triagain.crew.domain.vo.VerificationType;
import com.triagain.crew.port.in.EditCrewUseCase.EditCrewCommand;
import com.triagain.crew.port.in.EditCrewUseCase.EditCrewResult;
import com.triagain.crew.port.out.CrewRepositoryPort;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class EditCrewServiceTest {

    @Mock
    private CrewRepositoryPort crewRepositoryPort;

    @InjectMocks
    private EditCrewService editCrewService;

    @Test
    @DisplayName("LEADER가 크루 이름을 수정하면 변경된 이름이 반환된다")
    void editName_success() {
        // Given
        Crew crew = recruitingCrewWithLeader("leader-1");
        given(crewRepositoryPort.findById("CREW-1")).willReturn(Optional.of(crew));
        given(crewRepositoryPort.save(crew)).willReturn(crew);

        EditCrewCommand command = new EditCrewCommand("leader-1", "CREW-1", "새 이름", null, null, null, null);

        // When
        EditCrewResult result = editCrewService.editCrew(command);

        // Then
        assertThat(result.name()).isEqualTo("새 이름");
        assertThat(result.goal()).isEqualTo("목표");
    }

    @Test
    @DisplayName("여러 필드를 동시에 수정하면 모두 반영된다")
    void editMultipleFields_success() {
        // Given
        Crew crew = recruitingCrewWithLeader("leader-1");
        given(crewRepositoryPort.findById("CREW-1")).willReturn(Optional.of(crew));
        given(crewRepositoryPort.save(crew)).willReturn(crew);

        EditCrewCommand command = new EditCrewCommand("leader-1", "CREW-1", "새 이름", "새 목표", "새 인증", null, null);

        // When
        EditCrewResult result = editCrewService.editCrew(command);

        // Then
        assertThat(result.name()).isEqualTo("새 이름");
        assertThat(result.goal()).isEqualTo("새 목표");
        assertThat(result.verificationContent()).isEqualTo("새 인증");
    }

    @Test
    @DisplayName("모든 필드가 null이면 CREW_NO_FIELDS_TO_UPDATE 예외가 발생한다")
    void allFieldsNull_throws() {
        // Given
        EditCrewCommand command = new EditCrewCommand("leader-1", "CREW-1", null, null, null, null, null);

        // When & Then
        assertThatThrownBy(() -> editCrewService.editCrew(command))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CREW_NO_FIELDS_TO_UPDATE);
    }

    @Test
    @DisplayName("빈 문자열로 수정하면 CREW_INVALID_FIELD_VALUE 예외가 발생한다")
    void blankName_throws() {
        // Given
        EditCrewCommand command = new EditCrewCommand("leader-1", "CREW-1", "", null, null, null, null);

        // When & Then
        assertThatThrownBy(() -> editCrewService.editCrew(command))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CREW_INVALID_FIELD_VALUE);
    }

    @Test
    @DisplayName("공백만 있는 문자열로 수정하면 CREW_INVALID_FIELD_VALUE 예외가 발생한다")
    void whitespaceOnly_throws() {
        // Given
        EditCrewCommand command = new EditCrewCommand("leader-1", "CREW-1", null, "   ", null, null, null);

        // When & Then
        assertThatThrownBy(() -> editCrewService.editCrew(command))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CREW_INVALID_FIELD_VALUE);
    }

    @Test
    @DisplayName("존재하지 않는 크루를 수정하면 CREW_NOT_FOUND 예외가 발생한다")
    void crewNotFound_throws() {
        // Given
        given(crewRepositoryPort.findById("CREW-999")).willReturn(Optional.empty());
        EditCrewCommand command = new EditCrewCommand("leader-1", "CREW-999", "새 이름", null, null, null, null);

        // When & Then
        assertThatThrownBy(() -> editCrewService.editCrew(command))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CREW_NOT_FOUND);
    }

    @Test
    @DisplayName("MEMBER가 수정하면 CREW_ACCESS_DENIED 예외가 발생한다")
    void memberCannotEdit_throws() {
        // Given
        Crew crew = recruitingCrewWithLeaderAndMember("leader-1", "member-1");
        given(crewRepositoryPort.findById("CREW-1")).willReturn(Optional.of(crew));

        EditCrewCommand command = new EditCrewCommand("member-1", "CREW-1", "새 이름", null, null, null, null);

        // When & Then
        assertThatThrownBy(() -> editCrewService.editCrew(command))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CREW_ACCESS_DENIED);
    }

    @Test
    @DisplayName("비멤버가 수정하면 CREW_MEMBER_NOT_FOUND 예외가 발생한다")
    void nonMemberCannotEdit_throws() {
        // Given
        Crew crew = recruitingCrewWithLeader("leader-1");
        given(crewRepositoryPort.findById("CREW-1")).willReturn(Optional.of(crew));

        EditCrewCommand command = new EditCrewCommand("stranger", "CREW-1", "새 이름", null, null, null, null);

        // When & Then
        assertThatThrownBy(() -> editCrewService.editCrew(command))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CREW_MEMBER_NOT_FOUND);
    }

    // --- 헬퍼 ---

    private Crew recruitingCrewWithLeader(String leaderId) {
        CrewMember leader = CrewMember.of("CRMB-1", leaderId, "CREW-1", CrewRole.LEADER, LocalDateTime.now());
        return Crew.of("CREW-1", leaderId, "테스트 크루", "목표", "인증 내용",
                VerificationType.TEXT, 10, 1, CrewStatus.RECRUITING,
                LocalDate.now().plusDays(1), LocalDate.now().plusDays(14), true,
                "ABC123", LocalDateTime.now(), LocalTime.of(23, 59, 59), null, null, 0L, List.of(leader));
    }

    private Crew recruitingCrewWithLeaderAndMember(String leaderId, String memberId) {
        CrewMember leader = CrewMember.of("CRMB-1", leaderId, "CREW-1", CrewRole.LEADER, LocalDateTime.now());
        CrewMember member = CrewMember.of("CRMB-2", memberId, "CREW-1", CrewRole.MEMBER, LocalDateTime.now());
        return Crew.of("CREW-1", leaderId, "테스트 크루", "목표", "인증 내용",
                VerificationType.TEXT, 10, 2, CrewStatus.RECRUITING,
                LocalDate.now().plusDays(1), LocalDate.now().plusDays(14), true,
                "ABC123", LocalDateTime.now(), LocalTime.of(23, 59, 59), null, null, 0L, List.of(leader, member));
    }
}
