package com.triagain.crew.domain.model;

import com.triagain.common.exception.BusinessException;
import com.triagain.common.exception.ErrorCode;
import com.triagain.crew.domain.vo.CrewStatus;
import com.triagain.crew.domain.vo.VerificationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CrewTest {

    private static final LocalDate TOMORROW = LocalDate.now().plusDays(1);
    private static final LocalDate NEXT_WEEK = LocalDate.now().plusDays(7);
    private static final LocalDate FAR_FUTURE = LocalDate.now().plusDays(30);

    @Nested
    @DisplayName("create — 크루 생성")
    class Create {

        @Test
        @DisplayName("유효한 값으로 크루를 생성한다")
        void success() {
            // Given & When
            Crew crew = Crew.create("user1", "독서 크루", "매일 30분 읽기",
                    VerificationType.TEXT, 5, TOMORROW, NEXT_WEEK, false, null);

            // Then
            assertThat(crew.getId()).startsWith("CREW");
            assertThat(crew.getCreatorId()).isEqualTo("user1");
            assertThat(crew.getName()).isEqualTo("독서 크루");
            assertThat(crew.getStatus()).isEqualTo(CrewStatus.RECRUITING);
            assertThat(crew.getCurrentMembers()).isEqualTo(1);
            assertThat(crew.getMembers()).hasSize(1);
            assertThat(crew.getMembers().get(0).isLeader()).isTrue();
            assertThat(crew.getInviteCode()).hasSize(6);
        }

        @Test
        @DisplayName("maxMembers가 1이면 크루장 혼자 크루를 운영한다")
        void minMembers() {
            Crew crew = Crew.create("user1", "1인 크루", "목표",
                    VerificationType.TEXT, 1, TOMORROW, NEXT_WEEK, false, null);

            assertThat(crew.getMaxMembers()).isEqualTo(1);
        }

        @Test
        @DisplayName("maxMembers가 10이면 최대 정원으로 생성된다")
        void maxMembers() {
            Crew crew = Crew.create("user1", "대규모 크루", "목표",
                    VerificationType.TEXT, 10, TOMORROW, NEXT_WEEK, false, null);

            assertThat(crew.getMaxMembers()).isEqualTo(10);
        }

        @Test
        @DisplayName("maxMembers가 0이면 예외가 발생한다")
        void maxMembersZero() {
            assertThatThrownBy(() -> Crew.create("user1", "크루", "목표",
                    VerificationType.TEXT, 0, TOMORROW, NEXT_WEEK, false, null))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.INVALID_MAX_MEMBERS);
        }

        @Test
        @DisplayName("maxMembers가 11이면 예외가 발생한다")
        void maxMembersExceedsLimit() {
            assertThatThrownBy(() -> Crew.create("user1", "크루", "목표",
                    VerificationType.TEXT, 11, TOMORROW, NEXT_WEEK, false, null))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.INVALID_MAX_MEMBERS);
        }

        @Test
        @DisplayName("시작일이 오늘이면 예외가 발생한다")
        void startDateToday() {
            assertThatThrownBy(() -> Crew.create("user1", "크루", "목표",
                    VerificationType.TEXT, 5, LocalDate.now(), NEXT_WEEK, false, null))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.INVALID_START_DATE);
        }

        @Test
        @DisplayName("시작일이 과거면 예외가 발생한다")
        void startDatePast() {
            assertThatThrownBy(() -> Crew.create("user1", "크루", "목표",
                    VerificationType.TEXT, 5, LocalDate.now().minusDays(1), NEXT_WEEK, false, null))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.INVALID_START_DATE);
        }

        @Test
        @DisplayName("종료일이 시작일과 같으면 예외가 발생한다")
        void endDateEqualsStartDate() {
            assertThatThrownBy(() -> Crew.create("user1", "크루", "목표",
                    VerificationType.TEXT, 5, TOMORROW, TOMORROW, false, null))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.INVALID_END_DATE);
        }

        @Test
        @DisplayName("종료일이 시작일보다 이전이면 예외가 발생한다")
        void endDateBeforeStartDate() {
            assertThatThrownBy(() -> Crew.create("user1", "크루", "목표",
                    VerificationType.TEXT, 5, NEXT_WEEK, TOMORROW, false, null))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.INVALID_END_DATE);
        }

        @Test
        @DisplayName("종료일이 시작일+5일이면 최소 기간 미달로 예외가 발생한다")
        void endDateTooClose() {
            // Given — 시작일+5일은 작심삼일 2회(6일) 미달
            LocalDate startDate = TOMORROW;
            LocalDate endDate = TOMORROW.plusDays(5);

            // When & Then
            assertThatThrownBy(() -> Crew.create("user1", "크루", "목표",
                    VerificationType.TEXT, 5, startDate, endDate, false, null))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.INVALID_END_DATE);
        }

        @Test
        @DisplayName("종료일이 정확히 시작일+6일이면 최소 기간을 충족하여 정상 생성된다")
        void endDateExactlyMinimumDuration() {
            // Given — 시작일+6일 = 작심삼일 2회 보장 경계값
            LocalDate startDate = TOMORROW;
            LocalDate endDate = TOMORROW.plusDays(6);

            // When
            Crew crew = Crew.create("user1", "크루", "목표",
                    VerificationType.TEXT, 5, startDate, endDate, false, null);

            // Then
            assertThat(crew.getStartDate()).isEqualTo(startDate);
            assertThat(crew.getEndDate()).isEqualTo(endDate);
        }
    }

    @Nested
    @DisplayName("addMember — 멤버 추가")
    class AddMember {

        @Test
        @DisplayName("모집 중인 크루에 멤버를 추가한다")
        void success() {
            // Given
            Crew crew = recruitingCrew(5, 1);

            // When
            CrewMember member = crew.addMember("user2");

            // Then
            assertThat(member.getUserId()).isEqualTo("user2");
            assertThat(crew.getCurrentMembers()).isEqualTo(2);
            assertThat(crew.getMembers()).hasSize(2);
        }

        @Test
        @DisplayName("정원이 가득 차면 CREW_FULL 예외가 발생한다")
        void crewFull() {
            // Given
            Crew crew = recruitingCrew(2, 2);

            // When & Then
            assertThatThrownBy(() -> crew.addMember("newUser"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.CREW_FULL);
        }

        @Test
        @DisplayName("이미 참여한 유저가 다시 참여하면 CREW_ALREADY_JOINED 예외가 발생한다")
        void alreadyJoined() {
            // Given — leader의 userId = "leader"
            Crew crew = recruitingCrew(5, 1);

            // When & Then
            assertThatThrownBy(() -> crew.addMember("leader"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.CREW_ALREADY_JOINED);
        }

        @Test
        @DisplayName("COMPLETED 상태의 크루에는 참여할 수 없다")
        void completedCrew() {
            // Given
            Crew crew = crewWithStatus(CrewStatus.COMPLETED, 5, 1, false);

            // When & Then
            assertThatThrownBy(() -> crew.addMember("user2"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.CREW_NOT_RECRUITING);
        }

        @Test
        @DisplayName("ACTIVE 상태에서 allowLateJoin=false이면 참여할 수 없다")
        void activeNoLateJoin() {
            // Given
            Crew crew = crewWithStatus(CrewStatus.ACTIVE, 5, 1, false);

            // When & Then
            assertThatThrownBy(() -> crew.addMember("user2"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.CREW_NOT_RECRUITING);
        }

        @Test
        @DisplayName("ACTIVE 상태에서 allowLateJoin=true이면 참여할 수 있다")
        void activeLateJoinAllowed() {
            // Given
            Crew crew = crewWithStatus(CrewStatus.ACTIVE, 5, 1, true);

            // When
            CrewMember member = crew.addMember("user2");

            // Then
            assertThat(member.getUserId()).isEqualTo("user2");
            assertThat(crew.getCurrentMembers()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("canJoin — 참여 가능 여부")
    class CanJoin {

        @Test
        @DisplayName("RECRUITING 상태이고 정원이 남아있으면 true")
        void recruitingWithCapacity() {
            Crew crew = recruitingCrew(5, 1);
            assertThat(crew.canJoin()).isTrue();
        }

        @Test
        @DisplayName("ACTIVE + allowLateJoin=true이면 true")
        void activeLateJoin() {
            Crew crew = crewWithStatus(CrewStatus.ACTIVE, 5, 1, true);
            assertThat(crew.canJoin()).isTrue();
        }

        @Test
        @DisplayName("ACTIVE + allowLateJoin=false이면 false")
        void activeNoLateJoin() {
            Crew crew = crewWithStatus(CrewStatus.ACTIVE, 5, 1, false);
            assertThat(crew.canJoin()).isFalse();
        }

        @Test
        @DisplayName("COMPLETED 상태면 false")
        void completed() {
            Crew crew = crewWithStatus(CrewStatus.COMPLETED, 5, 1, false);
            assertThat(crew.canJoin()).isFalse();
        }

        @Test
        @DisplayName("정원이 가득 차면 false")
        void full() {
            Crew crew = recruitingCrew(2, 2);
            assertThat(crew.canJoin()).isFalse();
        }
    }

    @Nested
    @DisplayName("isFull — 정원 초과 여부")
    class IsFull {

        @Test
        @DisplayName("현재 멤버 수가 최대 정원 미만이면 false")
        void notFull() {
            Crew crew = recruitingCrew(5, 3);
            assertThat(crew.isFull()).isFalse();
        }

        @Test
        @DisplayName("현재 멤버 수가 최대 정원과 같으면 true")
        void full() {
            Crew crew = recruitingCrew(5, 5);
            assertThat(crew.isFull()).isTrue();
        }
    }

    @Nested
    @DisplayName("isJoinDeadlinePassed — 참여 마감 여부")
    class IsJoinDeadlinePassed {

        @Test
        @DisplayName("종료일이 충분히 미래면 false")
        void notPassed() {
            Crew crew = crewWithEndDate(FAR_FUTURE);
            assertThat(crew.isJoinDeadlinePassed()).isFalse();
        }

        @Test
        @DisplayName("종료일이 3일 이내이면 true")
        void passed() {
            // endDate - 3 < now → now > endDate - 3
            Crew crew = crewWithEndDate(LocalDate.now().plusDays(2));
            assertThat(crew.isJoinDeadlinePassed()).isTrue();
        }
    }

    @Nested
    @DisplayName("activate — 크루 활성화")
    class Activate {

        @Test
        @DisplayName("RECRUITING → ACTIVE 상태 전환에 성공한다")
        void success() {
            // Given
            Crew crew = recruitingCrew(5, 1);

            // When
            crew.activate();

            // Then
            assertThat(crew.getStatus()).isEqualTo(CrewStatus.ACTIVE);
        }

        @Test
        @DisplayName("ACTIVE 상태에서 activate하면 예외가 발생한다")
        void alreadyActive() {
            Crew crew = crewWithStatus(CrewStatus.ACTIVE, 5, 1, false);

            assertThatThrownBy(crew::activate)
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.CREW_NOT_RECRUITING);
        }

        @Test
        @DisplayName("COMPLETED 상태에서 activate하면 예외가 발생한다")
        void completed() {
            Crew crew = crewWithStatus(CrewStatus.COMPLETED, 5, 1, false);

            assertThatThrownBy(crew::activate)
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.CREW_NOT_RECRUITING);
        }
    }

    @Nested
    @DisplayName("complete — 크루 종료")
    class Complete {

        @Test
        @DisplayName("ACTIVE → COMPLETED 상태 전환에 성공한다")
        void success() {
            // Given
            Crew crew = crewWithStatus(CrewStatus.ACTIVE, 5, 1, false);

            // When
            crew.complete();

            // Then
            assertThat(crew.getStatus()).isEqualTo(CrewStatus.COMPLETED);
        }

        @Test
        @DisplayName("RECRUITING 상태에서 complete하면 예외가 발생한다")
        void recruiting() {
            Crew crew = recruitingCrew(5, 1);

            assertThatThrownBy(crew::complete)
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.CREW_NOT_ACTIVE);
        }
    }

    // --- 헬퍼 메서드 ---

    private Crew recruitingCrew(int maxMembers, int currentMembers) {
        return crewWithStatus(CrewStatus.RECRUITING, maxMembers, currentMembers, false);
    }

    private Crew crewWithStatus(CrewStatus status, int maxMembers, int currentMembers, boolean allowLateJoin) {
        List<CrewMember> members = new ArrayList<>();
        members.add(CrewMember.of("CRMB-1", "leader", "CREW-1", com.triagain.crew.domain.vo.CrewRole.LEADER, LocalDateTime.now()));
        for (int i = 1; i < currentMembers; i++) {
            members.add(CrewMember.of("CRMB-" + (i + 1), "user" + (i + 1), "CREW-1",
                    com.triagain.crew.domain.vo.CrewRole.MEMBER, LocalDateTime.now()));
        }

        return Crew.of("CREW-1", "leader", "테스트 크루", "목표",
                VerificationType.TEXT, maxMembers, currentMembers,
                status, TOMORROW, FAR_FUTURE, allowLateJoin,
                "ABC123", LocalDateTime.now(), Crew.DEFAULT_DEADLINE_TIME, members);
    }

    private Crew crewWithEndDate(LocalDate endDate) {
        List<CrewMember> members = List.of(
                CrewMember.of("CRMB-1", "leader", "CREW-1",
                        com.triagain.crew.domain.vo.CrewRole.LEADER, LocalDateTime.now()));

        return Crew.of("CREW-1", "leader", "테스트 크루", "목표",
                VerificationType.TEXT, 5, 1,
                CrewStatus.RECRUITING, TOMORROW, endDate, false,
                "ABC123", LocalDateTime.now(), Crew.DEFAULT_DEADLINE_TIME, members);
    }
}
