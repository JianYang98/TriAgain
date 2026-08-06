package com.triagain.support.infra;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Repository;

import com.triagain.support.port.out.ReactionRepositoryPort;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class ReactionJpaAdapter implements ReactionRepositoryPort {

	private final ReactionJpaRepository reactionJpaRepository;

	@Override
	public int upsertIfVerificationActive(
			String id, String verificationId, String userId, String emoji, LocalDateTime now) {
		return reactionJpaRepository.upsertIfVerificationActive(id, verificationId, userId, emoji, now);
	}

	@Override
	public long deleteByVerificationIdAndUserId(String verificationId, String userId) {
		return reactionJpaRepository.deleteByVerificationIdAndUserId(verificationId, userId);
	}

	@Override
	public List<ReactionRow> findRowsByVerificationIdIn(List<String> verificationIds) {
		return reactionJpaRepository.findRowsByVerificationIdIn(verificationIds);
	}
}
