package com.triagain.crew.domain.model;

import com.triagain.common.exception.BusinessException;
import com.triagain.common.exception.ErrorCode;
import com.triagain.crew.domain.vo.CrewCategory;
import com.triagain.crew.domain.vo.CrewStatus;
import com.triagain.crew.domain.vo.CrewVisibility;
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
                    "인증 내용", VerificationType.TEXT, 5, TOMORROW, NEXT_WEEK, false, null, CrewCategory.EXERCISE, null);

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
                    "인증 내용", VerificationType.TEXT, 1, TOMORROW, NEXT_WEEK, false, null, CrewCategory.EXERCISE, null);

            assertThat(crew.getMaxMembers()).isEqualTo(1);
        }

        @Test
        @DisplayName("maxMembers가 10이면 최대 정원으로 생성된다")
        void maxMembers() {
            Crew crew = Crew.create("user1", "대규모 크루", "목표",
                    "인증 내용", VerificationType.TEXT, 10, TOMORROW, NEXT_WEEK, false, null, CrewCategory.EXERCISE, null);

            assertThat(crew.getMaxMembers()).isEqualTo(10);
        }

        @Test
        @DisplayName("maxMembers가 0이면 예외가 발생한다")
        void maxMembersZero() {
            assertThatThrownBy(() -> Crew.create("user1", "크루", "목표",
                    "인증 내용", VerificationType.TEXT, 0, TOMORROW, NEXT_WEEK, false, null, CrewCategory.EXERCISE, null))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.INVALID_MAX_MEMBERS);
        }

        @Test
        @DisplayName("maxMembers가 11이면 예외가 발생한다")
        void maxMembersExceedsLimit() {
            assertThatThrownBy(() -> Crew.create("user1", "크루", "목표",
                    "인증 내용", VerificationType.TEXT, 11, TOMORROW, NEXT_WEEK, false, null, CrewCategory.EXERCISE, null))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.INVALID_MAX_MEMBERS);
        }

        @Test
        @DisplayName("시작일이 오늘이면 예외가 발생한다")
        void startDateToday() {
            assertThatThrownBy(() -> Crew.create("user1", "크루", "목표",
                    "인증 내용", VerificationType.TEXT, 5, LocalDate.now(), NEXT_WEEK, false, null, CrewCategory.EXERCISE, null))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.INVALID_START_DATE);
        }

        @Test
        @DisplayName("시작일이 과거면 예외가 발생한다")
        void startDatePast() {
            assertThatThrownBy(() -> Crew.create("user1", "크루", "목표",
                    "인증 내용", VerificationType.TEXT, 5, LocalDate.now().minusDays(1), NEXT_WEEK, false, null, CrewCategory.EXERCISE, null))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.INVALID_START_DATE);
        }

        @Test
        @DisplayName("종료일이 시작일과 같으면 예외가 발생한다")
        void endDateEqualsStartDate() {
            assertThatThrownBy(() -> Crew.create("user1", "크루", "목표",
                    "인증 내용", VerificationType.TEXT, 5, TOMORROW, TOMORROW, false, null, CrewCategory.EXERCISE, null))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.INVALID_END_DATE);
        }

        @Test
        @DisplayName("종료일이 시작일보다 이전이면 예외가 발생한다")
        void endDateBeforeStartDate() {
            assertThatThrownBy(() -> Crew.create("user1", "크루", "목표",
                    "인증 내용", VerificationType.TEXT, 5, NEXT_WEEK, TOMORROW, false, null, CrewCategory.EXERCISE, null))
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
                    "인증 내용", VerificationType.TEXT, 5, startDate, endDate, false, null, CrewCategory.EXERCISE, null))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.CREW_DURATION_TOO_SHORT);
        }

        @Test
        @DisplayName("종료일이 정확히 시작일+6일이면 최소 기간을 충족하여 정상 생성된다")
        void endDateExactlyMinimumDuration() {
            // Given — 시작일+6일 = 작심삼일 2회 보장 경계값
            LocalDate startDate = TOMORROW;
            LocalDate endDate = TOMORROW.plusDays(6);

            // When
            Crew crew = Crew.create("user1", "크루", "목표",
                    "인증 내용", VerificationType.TEXT, 5, startDate, endDate, false, null, CrewCategory.EXERCISE, null);

            // Then
            assertThat(crew.getStartDate()).isEqualTo(startDate);
            assertThat(crew.getEndDate()).isEqualTo(endDate);
        }
    }

    @Nested
    @DisplayName("create — visibility 기본값")
    class CreateVisibility {

        @Test
        @DisplayName("visibility가 null이면 PRIVATE으로 생성된다")
        void visibilityNullDefaultsToPrivate() {
            Crew crew = Crew.create("user1", "크루", "목표",
                    "인증 내용", VerificationType.TEXT, 5, TOMORROW, NEXT_WEEK, false, null, CrewCategory.EXERCISE, null);

            assertThat(crew.getVisibility()).isEqualTo(CrewVisibility.PRIVATE);
            assertThat(crew.isPublic()).isFalse();
        }

        @Test
        @DisplayName("visibility를 PUBLIC으로 지정하면 PUBLIC으로 생성된다")
        void visibilityPublic() {
            Crew crew = Crew.create("user1", "크루", "목표",
                    "인증 내용", VerificationType.TEXT, 5, TOMORROW, NEXT_WEEK, false, null, CrewCategory.STUDY, CrewVisibility.PUBLIC);

            assertThat(crew.getVisibility()).isEqualTo(CrewVisibility.PUBLIC);
            assertThat(crew.isPublic()).isTrue();
        }

        @Test
        @DisplayName("category가 설정되면 그대로 저장된다")
        void categoryPreserved() {
            Crew crew = Crew.create("user1", "크루", "목표",
                    "인증 내용", VerificationType.TEXT, 5, TOMORROW, NEXT_WEEK, false, null, CrewCategory.SELF_DEV, null);

            assertThat(crew.getCategory()).isEqualTo(CrewCategory.SELF_DEV);
        }
    }

    @Nested
    @DisplayName("update — category/visibility 수정")
    class UpdateCategoryVisibility {

        @Test
        @DisplayName("category를 수정하면 변경된다")
        void updateCategory() {
            Crew crew = recruitingCrew(5, 1);
            crew.update(null, null, null, CrewCategory.EXERCISE, null);

            assertThat(crew.getCategory()).isEqualTo(CrewCategory.EXERCISE);
        }

        @Test
        @DisplayName("visibility를 PUBLIC으로 수정하면 isPublic이 true이다")
        void updateVisibilityToPublic() {
            Crew crew = recruitingCrew(5, 1);
            crew.update(null, null, null, null, CrewVisibility.PUBLIC);

            assertThat(crew.isPublic()).isTrue();
        }

        @Test
        @DisplayName("null로 전달하면 기존 category/visibility가 유지된다")
        void nullPreservesExisting() {
            Crew crew = Crew.of("CREW-1", "leader", "테스트 크루", "목표",
                    "인증 내용", VerificationType.TEXT, 5, 1,
                    CrewStatus.RECRUITING, TOMORROW, FAR_FUTURE, false,
                    "ABC123", LocalDateTime.now(), Crew.DEFAULT_DEADLINE_TIME,
                    CrewCategory.STUDY, CrewVisibility.PUBLIC, 0L,
                    List.of(CrewMember.of("CRMB-1", "leader", "CREW-1",
                            com.triagain.crew.domain.vo.CrewRole.LEADER, LocalDateTime.now())));

            crew.update(null, null, null, null, null);

            assertThat(crew.getCategory()).isEqualTo(CrewCategory.STUDY);
            assertThat(crew.getVisibility()).isEqualTo(CrewVisibility.PUBLIC);
        }
    }

    @Nested
    @DisplayName("isPublic — 공개 크루 여부")
    class IsPublic {

        @Test
        @DisplayName("visibility가 PUBLIC이면 true")
        void publicCrew() {
            Crew crew = Crew.of("CREW-1", "leader", "크루", "목표",
                    "인증", VerificationType.TEXT, 5, 1,
                    CrewStatus.RECRUITING, TOMORROW, FAR_FUTURE, false,
                    "ABC123", LocalDateTime.now(), Crew.DEFAULT_DEADLINE_TIME,
                    null, CrewVisibility.PUBLIC, 0L, List.of());

            assertThat(crew.isPublic()).isTrue();
        }

        @Test
        @DisplayName("visibility가 PRIVATE이면 false")
        void privateCrew() {
            Crew crew = Crew.of("CREW-1", "leader", "크루", "목표",
                    "인증", VerificationType.TEXT, 5, 1,
                    CrewStatus.RECRUITING, TOMORROW, FAR_FUTURE, false,
                    "ABC123", LocalDateTime.now(), Crew.DEFAULT_DEADLINE_TIME,
                    null, CrewVisibility.PRIVATE, 0L, List.of());

            assertThat(crew.isPublic()).isFalse();
        }

        @Test
        @DisplayName("visibility가 null이면 false")
        void nullVisibility() {
            Crew crew = Crew.of("CREW-1", "leader", "크루", "목표",
                    "인증", VerificationType.TEXT, 5, 1,
                    CrewStatus.RECRUITING, TOMORROW, FAR_FUTURE, false,
                    "ABC123", LocalDateTime.now(), Crew.DEFAULT_DEADLINE_TIME,
                    null, null, 0L, List.of());

            assertThat(crew.isPublic()).isFalse();
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
        @DisplayName("종료일이 3일 이내인 크루에 참여하면 CREW_JOIN_DEADLINE_PASSED 예외가 발생한다")
        void joinDeadlinePassed() {
            // Given — endDate가 2일 후이면 isJoinDeadlinePassed() = true
            Crew crew = crewWithEndDate(LocalDate.now().plusDays(2));

            // When & Then
            assertThatThrownBy(() -> crew.addMember("user2"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.CREW_JOIN_DEADLINE_PASSED);
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

    @Nested
    @DisplayName("update — 크루 수정")
    class Update {

        @Test
        @DisplayName("RECRUITING 상태에서 이름을 수정한다")
        void updateName() {
            // Given
            Crew crew = recruitingCrew(5, 1);

            // When
            crew.update("새 이름", null, null, null, null);

            // Then
            assertThat(crew.getName()).isEqualTo("새 이름");
            assertThat(crew.getGoal()).isEqualTo("목표");
            assertThat(crew.getVerificationContent()).isEqualTo("인증 내용");
        }

        @Test
        @DisplayName("여러 필드를 동시에 수정한다")
        void updateMultipleFields() {
            // Given
            Crew crew = recruitingCrew(5, 1);

            // When
            crew.update("새 이름", "새 목표", "새 인증 내용", null, null);

            // Then
            assertThat(crew.getName()).isEqualTo("새 이름");
            assertThat(crew.getGoal()).isEqualTo("새 목표");
            assertThat(crew.getVerificationContent()).isEqualTo("새 인증 내용");
        }

        @Test
        @DisplayName("null 필드는 기존 값을 유지한다")
        void nullFieldsPreserved() {
            // Given
            Crew crew = recruitingCrew(5, 1);

            // When
            crew.update(null, null, null, null, null);

            // Then
            assertThat(crew.getName()).isEqualTo("테스트 크루");
            assertThat(crew.getGoal()).isEqualTo("목표");
            assertThat(crew.getVerificationContent()).isEqualTo("인증 내용");
        }

        @Test
        @DisplayName("ACTIVE 상태에서 수정하면 예외가 발생한다")
        void activeCrewCannotUpdate() {
            // Given
            Crew crew = crewWithStatus(CrewStatus.ACTIVE, 5, 1, false);

            // When & Then
            assertThatThrownBy(() -> crew.update("새 이름", null, null, null, null))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.CREW_NOT_RECRUITING);
        }

        @Test
        @DisplayName("COMPLETED 상태에서 수정하면 예외가 발생한다")
        void completedCrewCannotUpdate() {
            // Given
            Crew crew = crewWithStatus(CrewStatus.COMPLETED, 5, 1, false);

            // When & Then
            assertThatThrownBy(() -> crew.update("새 이름", null, null, null, null))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.CREW_NOT_RECRUITING);
        }
    }

    @Nested
    @DisplayName("findMemberByUserId — 유저 ID로 멤버 조회")
    class FindMemberByUserId {

        @Test
        @DisplayName("존재하는 멤버를 조회한다")
        void success() {
            // Given
            Crew crew = recruitingCrew(5, 2);

            // When
            CrewMember member = crew.findMemberByUserId("leader");

            // Then
            assertThat(member.getUserId()).isEqualTo("leader");
            assertThat(member.isLeader()).isTrue();
        }

        @Test
        @DisplayName("존재하지 않는 유저 ID로 조회하면 예외가 발생한다")
        void notFound() {
            // Given
            Crew crew = recruitingCrew(5, 1);

            // When & Then
            assertThatThrownBy(() -> crew.findMemberByUserId("unknown"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.CREW_MEMBER_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("removeMember — 멤버 제거")
    class RemoveMember {

        @Test
        @DisplayName("RECRUITING 크루에서 MEMBER를 제거하면 currentMembers가 감소한다")
        void success() {
            // Given
            Crew crew = recruitingCrew(5, 3);

            // When
            crew.removeMember("user2", false);

            // Then
            assertThat(crew.getCurrentMembers()).isEqualTo(2);
            assertThat(crew.getMembers()).hasSize(2);
        }

        @Test
        @DisplayName("존재하지 않는 멤버를 제거하면 예외가 발생한다")
        void notFound() {
            // Given
            Crew crew = recruitingCrew(5, 1);

            // When & Then
            assertThatThrownBy(() -> crew.removeMember("unknown", false))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.CREW_MEMBER_NOT_FOUND);
        }

        @Test
        @DisplayName("ACTIVE 크루에서 챌린지 미시작 멤버는 탈퇴할 수 있다")
        void activeWithoutChallenge_success() {
            // Given
            Crew crew = crewWithStatus(CrewStatus.ACTIVE, 5, 2, false);

            // When
            crew.removeMember("user2", false);

            // Then
            assertThat(crew.getCurrentMembers()).isEqualTo(1);
        }

        @Test
        @DisplayName("ACTIVE 크루에서 챌린지를 시작한 멤버는 CANNOT_LEAVE_ACTIVE_CREW 예외가 발생한다")
        void activeWithChallenge_throws() {
            // Given
            Crew crew = crewWithStatus(CrewStatus.ACTIVE, 5, 2, false);

            // When & Then
            assertThatThrownBy(() -> crew.removeMember("user2", true))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.CANNOT_LEAVE_ACTIVE_CREW);
        }

        @Test
        @DisplayName("COMPLETED 크루에서 removeMember하면 CANNOT_LEAVE_ACTIVE_CREW 예외가 발생한다")
        void completedCannotRemove() {
            // Given
            Crew crew = crewWithStatus(CrewStatus.COMPLETED, 5, 2, false);

            // When & Then
            assertThatThrownBy(() -> crew.removeMember("user2", false))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.CANNOT_LEAVE_ACTIVE_CREW);
        }

        @Test
        @DisplayName("LEADER가 removeMember하면 LEADER_CANNOT_LEAVE 예외가 발생한다")
        void leaderCannotRemove() {
            // Given
            Crew crew = recruitingCrew(5, 2);

            // When & Then
            assertThatThrownBy(() -> crew.removeMember("leader", false))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.LEADER_CANNOT_LEAVE);
        }
    }

    @Nested
    @DisplayName("validateDeletable — 삭제 가능 여부 검증")
    class ValidateDeletable {

        @Test
        @DisplayName("RECRUITING + 멤버 1명이면 삭제 가능하다")
        void success() {
            // Given
            Crew crew = recruitingCrew(5, 1);

            // When & Then — 예외 없이 통과
            crew.validateDeletable();
        }

        @Test
        @DisplayName("ACTIVE 상태면 삭제 불가하다")
        void activeCannotDelete() {
            // Given
            Crew crew = crewWithStatus(CrewStatus.ACTIVE, 5, 1, false);

            // When & Then
            assertThatThrownBy(crew::validateDeletable)
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.CREW_NOT_RECRUITING);
        }

        @Test
        @DisplayName("멤버가 2명 이상이면 삭제 불가하다")
        void hasMembers() {
            // Given
            Crew crew = recruitingCrew(5, 2);

            // When & Then
            assertThatThrownBy(crew::validateDeletable)
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.CREW_HAS_MEMBERS);
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
                "인증 내용", VerificationType.TEXT, maxMembers, currentMembers,
                status, TOMORROW, FAR_FUTURE, allowLateJoin,
                "ABC123", LocalDateTime.now(), Crew.DEFAULT_DEADLINE_TIME, null, null, 0L, members);
    }

    private Crew crewWithEndDate(LocalDate endDate) {
        List<CrewMember> members = List.of(
                CrewMember.of("CRMB-1", "leader", "CREW-1",
                        com.triagain.crew.domain.vo.CrewRole.LEADER, LocalDateTime.now()));

        return Crew.of("CREW-1", "leader", "테스트 크루", "목표",
                "인증 내용", VerificationType.TEXT, 5, 1,
                CrewStatus.RECRUITING, TOMORROW, endDate, false,
                "ABC123", LocalDateTime.now(), Crew.DEFAULT_DEADLINE_TIME, null, null, 0L, members);
    }
}
