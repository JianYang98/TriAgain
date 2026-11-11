package com.triagain.crew.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.triagain.crew.domain.model.Crew;
import com.triagain.crew.domain.model.CrewMember;
import com.triagain.crew.domain.vo.CrewCategory;
import com.triagain.crew.domain.vo.CrewStatus;
import com.triagain.crew.domain.vo.CrewVisibility;
import com.triagain.crew.domain.vo.VerificationType;

@ExtendWith(MockitoExtension.class)
class CrewJpaAdapterTest {

	private static final String CREW_ID = "CREW-001";

	@Mock
	private CrewJpaRepository crewJpaRepository;

	@Mock
	private CrewMemberJpaRepository crewMemberJpaRepository;

	@Mock
	private EntityManager entityManager;

	@InjectMocks
	private CrewJpaAdapter adapter;

	@Test
	@DisplayName("findByIdWithoutMembers는 crew_members를 조회하지 않는다")
	void findByIdWithoutMembers_doesNotQueryCrewMembers() {
		// Given — 옛 동작(findById 위임)으로 되돌아가도 예외 없이 흘러가 아래 단언에서 잡히도록 lenient로 둔다
		given(crewJpaRepository.findById(CREW_ID)).willReturn(Optional.of(crewEntity()));
		lenient().when(crewMemberJpaRepository.findByCrewId(CREW_ID)).thenReturn(List.of(leaderEntity()));

		// When
		adapter.findByIdWithoutMembers(CREW_ID);

		// Then
		verify(crewMemberJpaRepository, never()).findByCrewId(any());
	}

	@Test
	@DisplayName("findByIdWithoutMembers가 반환한 크루의 멤버 목록은 비어 있다")
	void findByIdWithoutMembers_returnsCrewWithEmptyMembers() {
		// Given
		given(crewJpaRepository.findById(CREW_ID)).willReturn(Optional.of(crewEntity()));
		lenient().when(crewMemberJpaRepository.findByCrewId(CREW_ID)).thenReturn(List.of(leaderEntity()));

		// When
		Optional<Crew> result = adapter.findByIdWithoutMembers(CREW_ID);

		// Then
		assertThat(result).isPresent();
		assertThat(result.get().getMembers()).isEmpty();
	}

	@Test
	@DisplayName("findByIdWithoutMembers는 같은 엔티티로 findById가 만든 크루와 가입 사전 검증 필드가 같다")
	void findByIdWithoutMembers_matchesFindByIdOnJoinPreCheckFields() {
		// Given
		given(crewJpaRepository.findById(CREW_ID)).willReturn(Optional.of(crewEntity()));
		given(crewMemberJpaRepository.findByCrewId(CREW_ID)).willReturn(List.of(leaderEntity()));

		// When
		Crew withMembers = adapter.findById(CREW_ID).orElseThrow();
		Crew withoutMembers = adapter.findByIdWithoutMembers(CREW_ID).orElseThrow();

		// Then — 멤버 목록만 다르고 status·visibility·endDate·maxMembers·currentMembers·allowLateJoin은 같다
		assertThat(withMembers.getMembers()).hasSize(1);
		assertThat(withoutMembers).usingRecursiveComparison()
			.comparingOnlyFields("status", "visibility", "endDate", "maxMembers", "currentMembers", "allowLateJoin")
			.isEqualTo(withMembers);
		assertThat(withoutMembers.getStatus()).isEqualTo(CrewStatus.ACTIVE);
		assertThat(withoutMembers.getVisibility()).isEqualTo(CrewVisibility.PUBLIC);
		assertThat(withoutMembers.isAllowLateJoin()).isTrue();
	}

	@Test
	@DisplayName("findByIdWithoutMembers는 크루가 없으면 빈 Optional을 반환하고 crew_members를 조회하지 않는다")
	void findByIdWithoutMembers_returnsEmptyWhenCrewMissing() {
		// Given
		given(crewJpaRepository.findById(CREW_ID)).willReturn(Optional.empty());

		// When
		Optional<Crew> result = adapter.findByIdWithoutMembers(CREW_ID);

		// Then
		assertThat(result).isEmpty();
		verifyNoInteractions(crewMemberJpaRepository);
	}

	private static CrewJpaEntity crewEntity() {
		return CrewJpaEntity.fromDomain(Crew.of(CREW_ID, "creator-1", "테스트 크루", "목표", "인증 내용",
			VerificationType.TEXT, 8, 3, CrewStatus.ACTIVE,
			LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 30),
			true, "ABC123", LocalDateTime.of(2026, 9, 20, 10, 0),
			LocalTime.of(22, 0), CrewCategory.STUDY, CrewVisibility.PUBLIC, 5L, List.of()));
	}

	private static CrewMemberJpaEntity leaderEntity() {
		return CrewMemberJpaEntity.fromDomain(CrewMember.createLeader("creator-1", CREW_ID));
	}
}
