package com.triagain.crew.application;

import java.time.LocalDateTime;

import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.triagain.crew.domain.model.CrewMember;
import com.triagain.crew.port.out.CrewRepositoryPort;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class CrewJoinPersistenceService {

	private final CrewRepositoryPort crewRepositoryPort;
	private final TransactionTemplate transactionTemplate;

	/** 멤버·카운터를 함께 commit — 반환 시 commit 완료, 대상 UNIQUE 중복만 정상 replay */
	public int persist(String crewId, String userId, LocalDateTime joinedAt) {
		if (TransactionSynchronizationManager.isActualTransactionActive()) {
			throw new IllegalStateException("Worker persistence must own its transaction");
		}
		CrewMember member = CrewMember.createMember(userId, crewId, joinedAt);
		return transactionTemplate.execute(status -> insertAndIncrement(member));
	}

	/** 멤버 저장과 인원 증가가 한 트랜잭션이며, 중복 재처리 때는 둘 다 다시 하지 않는다 */
	private int insertAndIncrement(CrewMember member) {
		int inserted = crewRepositoryPort.insertMemberIfAbsent(member);
		if (inserted == 0) {
			return 0;
		}
		if (inserted != 1) {
			throw new IllegalStateException("Unexpected membership INSERT count: " + inserted);
		}
		if (crewRepositoryPort.incrementMembersIfNotFull(member.getCrewId()) != 1) {
			throw new IllegalStateException("Redis/DB membership invariant mismatch");
		}
		return 1;
	}
}
