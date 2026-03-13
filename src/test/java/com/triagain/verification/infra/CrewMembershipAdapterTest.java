package com.triagain.verification.infra;

import com.triagain.crew.port.in.CrewMembershipQueryUseCase;
import com.triagain.crew.port.in.CrewMembershipQueryUseCase.CrewPeriodDto;
import com.triagain.crew.port.in.CrewMembershipQueryUseCase.CrewVerificationWindowDto;
import com.triagain.verification.port.out.CrewPort.CrewPeriod;
import com.triagain.verification.port.out.CrewPort.CrewVerificationWindowInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CrewMembershipAdapterTest {

    @Mock
    private CrewMembershipQueryUseCase crewMembershipQueryUseCase;

    @InjectMocks
    private CrewMembershipAdapter crewMembershipAdapter;

    @Test
    @DisplayName("validateMembership — CrewMembershipQueryUseCase에 위임")
    void validateMembership_delegatesToUseCase() {
        // Given
        String crewId = "crew-1";
        String userId = "user-1";

        // When
        crewMembershipAdapter.validateMembership(crewId, userId);

        // Then
        verify(crewMembershipQueryUseCase).validateMembership(crewId, userId);
    }

    @Test
    @DisplayName("getVerificationType — 크루 인증방식 문자열 반환 확인")
    void getVerificationType_returnsString() {
        // Given
        String crewId = "crew-1";
        given(crewMembershipQueryUseCase.getVerificationType(crewId)).willReturn("PHOTO");

        // When
        String result = crewMembershipAdapter.getVerificationType(crewId);

        // Then
        assertThat(result).isEqualTo("PHOTO");
    }

    @Test
    @DisplayName("getCrewPeriod — CrewPeriodDto → CrewPeriod 변환 확인")
    void getCrewPeriod_convertsDto() {
        // Given
        String crewId = "crew-1";
        LocalDate startDate = LocalDate.of(2026, 3, 10);
        LocalDate endDate = LocalDate.of(2026, 3, 19);
        given(crewMembershipQueryUseCase.getCrewPeriod(crewId))
                .willReturn(new CrewPeriodDto(startDate, endDate));

        // When
        CrewPeriod result = crewMembershipAdapter.getCrewPeriod(crewId);

        // Then
        assertThat(result.startDate()).isEqualTo(startDate);
        assertThat(result.endDate()).isEqualTo(endDate);
    }

    @Test
    @DisplayName("getCrewVerificationWindowInfo — CrewVerificationWindowDto → CrewVerificationWindowInfo 변환 확인")
    void getCrewVerificationWindowInfo_convertsDto() {
        // Given
        String crewId = "crew-1";
        LocalDate startDate = LocalDate.of(2026, 3, 10);
        LocalDate endDate = LocalDate.of(2026, 3, 19);
        LocalTime deadlineTime = LocalTime.of(23, 59);

        given(crewMembershipQueryUseCase.getCrewVerificationWindowInfo(crewId))
                .willReturn(new CrewVerificationWindowDto(
                        "PHOTO", "ACTIVE", startDate, endDate, true, deadlineTime
                ));

        // When
        CrewVerificationWindowInfo result = crewMembershipAdapter.getCrewVerificationWindowInfo(crewId);

        // Then
        assertThat(result.verificationType()).isEqualTo("PHOTO");
        assertThat(result.status()).isEqualTo("ACTIVE");
        assertThat(result.startDate()).isEqualTo(startDate);
        assertThat(result.endDate()).isEqualTo(endDate);
        assertThat(result.allowLateJoin()).isTrue();
        assertThat(result.deadlineTime()).isEqualTo(deadlineTime);
    }
}
