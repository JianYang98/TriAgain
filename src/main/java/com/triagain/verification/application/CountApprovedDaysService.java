package com.triagain.verification.application;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.triagain.verification.port.in.CountApprovedDaysUseCase;
import com.triagain.verification.port.out.VerificationRepositoryPort;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CountApprovedDaysService implements CountApprovedDaysUseCase {

	private final VerificationRepositoryPort verificationRepositoryPort;

	/** 유저·크루 묶음별 APPROVED 인증 일수 배치 조회 — 홈 완료 탭 verifiedDayCount 집계에 사용 */
	@Override
	@Transactional(readOnly = true)
	public Map<String, Integer> countApprovedDaysByCrewIds(String userId, List<String> crewIds) {
		return verificationRepositoryPort.findApprovedDayCountsByCrewIds(userId, crewIds);
	}
}
