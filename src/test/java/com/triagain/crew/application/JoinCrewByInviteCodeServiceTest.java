package com.triagain.crew.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import com.triagain.common.exception.BusinessException;
import com.triagain.common.exception.ErrorCode;
import com.triagain.crew.application.CrewLockProperties.LockStrategy;
import com.triagain.crew.domain.model.Crew;
import com.triagain.crew.domain.model.CrewMember;
import com.triagain.crew.domain.vo.CrewRole;
import com.triagain.crew.domain.vo.CrewStatus;
import com.triagain.crew.domain.vo.CrewVisibility;
import com.triagain.crew.domain.vo.VerificationType;
import com.triagain.crew.port.in.JoinCrewByInviteCodeUseCase.JoinByInviteCodeCommand;
import com.triagain.crew.port.out.CrewRepositoryPort;

@ExtendWith(MockitoExtension.class)
class JoinCrewByInviteCodeServiceTest {

	@Mock
	private CrewRepositoryPort crewRepositoryPort;
	@Mock
	private CrewLockProperties lockProperties;
	@Mock
	private TransactionTemplate txTemplate;
	@InjectMocks
	private JoinCrewByInviteCodeService service;

	@ParameterizedTest
	@EnumSource(value = LockStrategy.class, names = {"PESSIMISTIC", "OPTIMISTIC", "CONDITIONAL"})
	@DisplayName("세 DB 전략 모두 초대코드로 비공개 크루에 가입한다")
	void joinPrivateCrew_usesSelectedStrategy(LockStrategy strategy) {
		// Given
		prepareTransaction(strategy);
		Crew crew = privateCrew();
		given(crewRepositoryPort.findByInviteCode("ABC123")).willReturn(Optional.of(crew));
		prepareSuccessfulWrite(strategy, crew);

		// When
		var result = service.joinByInviteCode(command());

		// Then
		assertThat(result.userId()).isEqualTo("member");
		assertThat(result.role()).isEqualTo(CrewRole.MEMBER);
		assertThat(result.currentMembers()).isEqualTo(2);
		verifySelectedWrite(strategy);
	}

	@ParameterizedTest
	@EnumSource(value = LockStrategy.class, names = {"PESSIMISTIC", "OPTIMISTIC", "CONDITIONAL"})
	@DisplayName("세 DB 전략 모두 존재하지 않는 초대코드를 거부한다")
	void invalidInviteCode_preservesError(LockStrategy strategy) {
		// Given
		prepareTransaction(strategy);
		given(crewRepositoryPort.findByInviteCode("ABC123")).willReturn(Optional.empty());

		// When & Then
		assertError(ErrorCode.INVALID_INVITE_CODE);
		verify(crewRepositoryPort, never()).saveMember(any());
		verify(crewRepositoryPort, never()).saveMemberAndFlush(any());
	}

	@Test
	@DisplayName("낙관 충돌 뒤 재시도에서 성공하면 멤버는 한 번만 저장한다")
	void optimisticRetry_savesOnce() {
		// Given
		prepareTransaction(LockStrategy.OPTIMISTIC);
		given(crewRepositoryPort.findByInviteCode("ABC123")).willAnswer(inv -> Optional.of(privateCrew()));
		given(crewRepositoryPort.updateCurrentMembersWithVersion("CREW-1", 2, 0L)).willReturn(0, 1);

		// When
		var result = service.joinByInviteCode(command());

		// Then
		assertThat(result.currentMembers()).isEqualTo(2);
		verify(txTemplate, times(2)).execute(any());
		verify(crewRepositoryPort).saveMember(any());
	}

	@Test
	@DisplayName("낙관 재시도가 소진되면 CR023으로 거부한다")
	void optimisticRetry_exhausted() {
		// Given
		prepareTransaction(LockStrategy.OPTIMISTIC);
		given(crewRepositoryPort.findByInviteCode("ABC123")).willAnswer(inv -> Optional.of(privateCrew()));
		given(crewRepositoryPort.updateCurrentMembersWithVersion("CREW-1", 2, 0L)).willReturn(0);

		// When & Then
		assertError(ErrorCode.CREW_JOIN_CONFLICT);
		verify(txTemplate, times(3)).execute(any());
		verify(crewRepositoryPort, never()).saveMember(any());
	}

	@Test
	@DisplayName("조건부 UPDATE가 0행이면 정원 초과로 거부한다")
	void conditionalFull_doesNotSaveMember() {
		// Given
		prepareTransaction(LockStrategy.CONDITIONAL);
		given(crewRepositoryPort.findByInviteCode("ABC123")).willReturn(Optional.of(privateCrew()));
		given(crewRepositoryPort.incrementMembersIfNotFull("CREW-1")).willReturn(0);

		// When & Then
		assertError(ErrorCode.CREW_FULL);
		verify(crewRepositoryPort, never()).saveMemberAndFlush(any());
	}

	@Test
	@DisplayName("조건부 멤버 flush의 중복 오류는 기존 CR004를 유지한다")
	void conditionalDuplicate_preservesError() {
		// Given
		prepareTransaction(LockStrategy.CONDITIONAL);
		given(crewRepositoryPort.findByInviteCode("ABC123")).willReturn(Optional.of(privateCrew()));
		given(crewRepositoryPort.incrementMembersIfNotFull("CREW-1")).willReturn(1);
		given(crewRepositoryPort.saveMemberAndFlush(any())).willThrow(new DataIntegrityViolationException("duplicate"));

		// When & Then
		assertError(ErrorCode.CREW_ALREADY_JOINED);
	}

	@Test
	@DisplayName("REDIS_ASYNC 초대 가입은 DB 작업 없이 즉시 거부한다")
	void redisAsync_rejectsWithoutDatabaseAccess() {
		// Given
		given(lockProperties.getLockStrategy()).willReturn(LockStrategy.REDIS_ASYNC);

		// When & Then
		assertThatThrownBy(() -> service.joinByInviteCode(command()))
			.isInstanceOf(IllegalStateException.class).hasMessageContaining("REDIS_ASYNC");
		verifyNoInteractions(crewRepositoryPort, txTemplate);
	}

	@Test
	@DisplayName("가입 전략이 null이면 초대 가입도 DB 전략으로 대체하지 않는다")
	void missingStrategy_doesNotFallBack() {
		// Given
		given(lockProperties.getLockStrategy()).willReturn(null);

		// When & Then
		assertThatThrownBy(() -> service.joinByInviteCode(command()))
			.isInstanceOf(NullPointerException.class).hasMessageContaining("lock-strategy");
		verifyNoInteractions(crewRepositoryPort, txTemplate);
	}

	private void prepareTransaction(LockStrategy strategy) {
		given(lockProperties.getLockStrategy()).willReturn(strategy);
		given(txTemplate.execute(any())).willAnswer(invocation -> {
			TransactionCallback<?> callback = invocation.getArgument(0);
			return callback.doInTransaction(null);
		});
		if (strategy == LockStrategy.OPTIMISTIC) {
			given(lockProperties.getMaxRetry()).willReturn(3);
		}
	}

	private void prepareSuccessfulWrite(LockStrategy strategy, Crew crew) {
		switch (strategy) {
			case PESSIMISTIC -> given(crewRepositoryPort.findByIdWithLock("CREW-1")).willReturn(Optional.of(crew));
			case OPTIMISTIC -> given(crewRepositoryPort.updateCurrentMembersWithVersion("CREW-1", 2, 0L)).willReturn(1);
			case CONDITIONAL -> given(crewRepositoryPort.incrementMembersIfNotFull("CREW-1")).willReturn(1);
			case REDIS_ASYNC -> throw new AssertionError("DB 전략 테스트에 Redis를 사용할 수 없습니다.");
		}
	}

	private void verifySelectedWrite(LockStrategy strategy) {
		if (strategy == LockStrategy.CONDITIONAL) {
			verify(crewRepositoryPort).saveMemberAndFlush(any());
			verify(crewRepositoryPort, never()).saveMember(any());
		} else {
			verify(crewRepositoryPort).saveMember(any());
			verify(crewRepositoryPort, never()).saveMemberAndFlush(any());
		}
		if (strategy == LockStrategy.PESSIMISTIC) {
			verify(crewRepositoryPort).findByIdWithLock("CREW-1");
			verify(crewRepositoryPort).save(any());
		} else {
			verify(crewRepositoryPort, never()).findByIdWithLock(any());
			verify(crewRepositoryPort, never()).save(any());
		}
	}

	private void assertError(ErrorCode expected) {
		assertThatThrownBy(() -> service.joinByInviteCode(command()))
			.isInstanceOf(BusinessException.class).extracting("errorCode").isEqualTo(expected);
	}

	private JoinByInviteCodeCommand command() {
		return new JoinByInviteCodeCommand("member", "ABC123");
	}

	private Crew privateCrew() {
		return Crew.of("CREW-1", "leader", "크루", "목표", "인증", VerificationType.TEXT, 5, 1,
			CrewStatus.RECRUITING, LocalDate.now().plusDays(1), LocalDate.now().plusDays(30), false,
			"ABC123", LocalDateTime.now(), Crew.DEFAULT_DEADLINE_TIME, null, CrewVisibility.PRIVATE, 0L,
			List.of(CrewMember.createLeader("leader", "CREW-1")));
	}
}
