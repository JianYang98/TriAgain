package com.triagain.verification.application;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.triagain.verification.port.in.CheckTodayVerificationUseCase;
import com.triagain.verification.port.out.VerificationRepositoryPort;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CheckTodayVerificationService implements CheckTodayVerificationUseCase {

	private final VerificationRepositoryPort verificationRepositoryPort;

	/** 오늘 인증 완료한 크루 ID 배치 조회 */
	@Override
	@Transactional(readOnly = true)
	public Set<String> findVerifiedCrewIds(String userId, List<String> crewIds, LocalDate targetDate) {
		return verificationRepositoryPort.findVerifiedCrewIds(userId, crewIds, targetDate);
	}
}
