package com.triagain.support.infra;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.EntityManager;

import org.springframework.stereotype.Repository;

import com.triagain.support.port.out.VerificationLookupPort;

import lombok.RequiredArgsConstructor;

/** 인증 교차 컨텍스트 조회 — support의 네이티브 읽기(선례: NotificationTargetQueryAdapter) */
@Repository
@RequiredArgsConstructor
public class VerificationLookupAdapter implements VerificationLookupPort {

	private final EntityManager entityManager;

	/** 인증의 크루 id 조회 — status 무관(취소된 인증도 조회 가능, E1-d가 이 특성에 기댄다) */
	@Override
	@SuppressWarnings("unchecked")
	public Optional<String> findCrewIdById(String verificationId) {
		List<String> rows = entityManager
				.createNativeQuery("SELECT crew_id FROM verifications WHERE id = :id")
				.setParameter("id", verificationId)
				.getResultList();
		return rows.stream().findFirst();
	}
}
