package com.triagain.crew.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.triagain.common.exception.BusinessException;
import com.triagain.common.exception.ErrorCode;
import com.triagain.crew.domain.model.Crew;
import com.triagain.crew.domain.model.CrewMember;
import com.triagain.crew.domain.vo.CrewRole;
import com.triagain.crew.domain.vo.CrewStatus;
import com.triagain.crew.domain.vo.VerificationType;
import com.triagain.crew.port.out.ChallengeRepositoryPort;
import com.triagain.crew.port.out.CrewRepositoryPort;

@ExtendWith(MockitoExtension.class)
class DeleteCrewServiceTest {

	@Mock
	private CrewRepositoryPort crewRepositoryPort;

	@Mock
	private ChallengeRepositoryPort challengeRepositoryPort;

	@InjectMocks
	private DeleteCrewService deleteCrewService;

	@Test
	@DisplayName("LEADER가 RECRUITING + 혼자인 크루를 삭제하면 성공한다")
	void recruiting_alone_success() {
		// Given
		Crew crew = recruitingCrewWithLeader("leader-1");
		given(crewRepositoryPort.findByIdWithLock("CREW-1"))
			.willReturn(Optional.of(crew));
		given(challengeRepositoryPort.existsByCrewId("CREW-1"))
			.willReturn(false);

		// When & Then
		assertThatCode(() ->
			deleteCrewService.deleteCrew("CREW-1", "leader-1"))
			.doesNotThrowAnyException();
		verify(crewRepositoryPort).deleteCrewWithAssociations("CREW-1");
	}

	@Test
	@DisplayName("ACTIVE + 혼자 + 인증 전이면 삭제 성공한다")
	void active_alone_no_challenge_success() {
		// Given
		Crew crew = crewWithStatus("leader-1", CrewStatus.ACTIVE, 1);
		given(crewRepositoryPort.findByIdWithLock("CREW-1"))
			.willReturn(Optional.of(crew));
		given(challengeRepositoryPort.existsByCrewId("CREW-1"))
			.willReturn(false);

		// When & Then
		assertThatCode(() ->
			deleteCrewService.deleteCrew("CREW-1", "leader-1"))
			.doesNotThrowAnyException();
		verify(crewRepositoryPort).deleteCrewWithAssociations("CREW-1");
	}

	@Test
	@DisplayName("ACTIVE + 인증 시작이면 CANNOT_DELETE_STARTED_CREW 예외가 발생한다")
	void active_started_throws() {
		// Given
		Crew crew = crewWithStatus("leader-1", CrewStatus.ACTIVE, 1);
		given(crewRepositoryPort.findByIdWithLock("CREW-1"))
			.willReturn(Optional.of(crew));
		given(challengeRepositoryPort.existsByCrewId("CREW-1"))
			.willReturn(true);

		// When & Then
		assertThatThrownBy(() ->
			deleteCrewService.deleteCrew("CREW-1", "leader-1"))
			.isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException) e).getErrorCode())
			.isEqualTo(ErrorCode.CANNOT_DELETE_STARTED_CREW);
	}

	@Test
	@DisplayName("ACTIVE + 멤버 2명 이상 + 인증 전이면 CREW_HAS_MEMBERS 예외가 발생한다")
	void active_two_members_no_challenge_throws() {
		// Given — 인증 전(false)이어야 상태 게이트 통과 후 멤버 체크 도달
		Crew crew = crewWithStatus("leader-1", CrewStatus.ACTIVE, 2);
		given(crewRepositoryPort.findByIdWithLock("CREW-1"))
			.willReturn(Optional.of(crew));
		given(challengeRepositoryPort.existsByCrewId("CREW-1"))
			.willReturn(false);

		// When & Then
		assertThatThrownBy(() ->
			deleteCrewService.deleteCrew("CREW-1", "leader-1"))
			.isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException) e).getErrorCode())
			.isEqualTo(ErrorCode.CREW_HAS_MEMBERS);
	}

	@Test
	@DisplayName("COMPLETED 상태이면 CANNOT_DELETE_STARTED_CREW 예외가 발생한다")
	void completed_throws() {
		// Given
		Crew crew = crewWithStatus("leader-1", CrewStatus.COMPLETED, 1);
		given(crewRepositoryPort.findByIdWithLock("CREW-1"))
			.willReturn(Optional.of(crew));
		given(challengeRepositoryPort.existsByCrewId("CREW-1"))
			.willReturn(false);

		// When & Then
		assertThatThrownBy(() ->
			deleteCrewService.deleteCrew("CREW-1", "leader-1"))
			.isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException) e).getErrorCode())
			.isEqualTo(ErrorCode.CANNOT_DELETE_STARTED_CREW);
	}

	@Test
	@DisplayName("리더는 인증 전이지만 떠난 멤버의 ENDED 챌린지(crew_id)가 있으면 CANNOT_DELETE_STARTED_CREW 예외가 발생한다")
	void active_alone_ended_challenge_from_ex_member_throws() {
		// Given — 리더만 남았지만 existsByCrewId가 true (떠난 멤버 챌린지 잔존)
		Crew crew = crewWithStatus("leader-1", CrewStatus.ACTIVE, 1);
		given(crewRepositoryPort.findByIdWithLock("CREW-1"))
			.willReturn(Optional.of(crew));
		given(challengeRepositoryPort.existsByCrewId("CREW-1"))
			.willReturn(true);

		// When & Then
		assertThatThrownBy(() ->
			deleteCrewService.deleteCrew("CREW-1", "leader-1"))
			.isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException) e).getErrorCode())
			.isEqualTo(ErrorCode.CANNOT_DELETE_STARTED_CREW);
	}

	@Test
	@DisplayName("존재하지 않는 크루를 삭제하면 CREW_NOT_FOUND 예외가 발생한다")
	void crewNotFound_throws() {
		// Given
		given(crewRepositoryPort.findByIdWithLock("CREW-999"))
			.willReturn(Optional.empty());

		// When & Then
		assertThatThrownBy(() ->
			deleteCrewService.deleteCrew("CREW-999", "leader-1"))
			.isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException) e).getErrorCode())
			.isEqualTo(ErrorCode.CREW_NOT_FOUND);
	}

	@Test
	@DisplayName("MEMBER가 삭제하면 CREW_ACCESS_DENIED 예외가 발생한다")
	void memberCannotDelete_throws() {
		// Given
		Crew crew = crewWithLeaderAndMember("leader-1", "member-1",
			CrewStatus.RECRUITING);
		given(crewRepositoryPort.findByIdWithLock("CREW-1"))
			.willReturn(Optional.of(crew));

		// When & Then
		assertThatThrownBy(() ->
			deleteCrewService.deleteCrew("CREW-1", "member-1"))
			.isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException) e).getErrorCode())
			.isEqualTo(ErrorCode.CREW_ACCESS_DENIED);
	}

	// --- 헬퍼 ---

	private Crew recruitingCrewWithLeader(String leaderId) {
		return crewWithStatus(leaderId, CrewStatus.RECRUITING, 1);
	}

	private Crew crewWithStatus(String leaderId, CrewStatus status,
			int currentMembers) {
		CrewMember leader = CrewMember.of(
			"CRMB-1", leaderId, "CREW-1",
			CrewRole.LEADER, LocalDateTime.now());
		List<CrewMember> members = new java.util.ArrayList<>();
		members.add(leader);
		for (int i = 1; i < currentMembers; i++) {
			members.add(CrewMember.of(
				"CRMB-" + (i + 1), "user" + (i + 1), "CREW-1",
				CrewRole.MEMBER, LocalDateTime.now()));
		}
		return Crew.of("CREW-1", leaderId, "테스트 크루", "목표",
			"인증 내용", VerificationType.TEXT, 10, currentMembers,
			status, LocalDate.now().plusDays(1),
			LocalDate.now().plusDays(14), true, "ABC123",
			LocalDateTime.now(), LocalTime.of(23, 59, 59),
			null, null, 0L, members);
	}

	private Crew crewWithLeaderAndMember(String leaderId, String memberId,
			CrewStatus status) {
		CrewMember leader = CrewMember.of(
			"CRMB-1", leaderId, "CREW-1",
			CrewRole.LEADER, LocalDateTime.now());
		CrewMember member = CrewMember.of(
			"CRMB-2", memberId, "CREW-1",
			CrewRole.MEMBER, LocalDateTime.now());
		return Crew.of("CREW-1", leaderId, "테스트 크루", "목표",
			"인증 내용", VerificationType.TEXT, 10, 2,
			status, LocalDate.now().plusDays(1),
			LocalDate.now().plusDays(14), true, "ABC123",
			LocalDateTime.now(), LocalTime.of(23, 59, 59),
			null, null, 0L, List.of(leader, member));
	}
}
