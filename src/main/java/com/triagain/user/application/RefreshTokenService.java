package com.triagain.user.application;

import com.triagain.common.auth.JwtProvider;
import com.triagain.common.exception.BusinessException;
import com.triagain.common.exception.ErrorCode;
import com.triagain.user.domain.model.User;
import com.triagain.user.port.in.RefreshTokenUseCase;
import com.triagain.user.port.out.UserRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RefreshTokenService implements RefreshTokenUseCase {

	private final JwtProvider jwtProvider;
	private final UserRepositoryPort userRepositoryPort;

	/** Refresh Token 검증 → 새 Access Token 발급 */
	@Override
	@Transactional(readOnly = true)
	public RefreshResult refresh(RefreshCommand command) {
		if (!jwtProvider.validateToken(command.refreshToken())) {
			throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
		}

		if (!"refresh".equals(jwtProvider.getTokenType(command.refreshToken()))) {
			throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
		}

		String userId = jwtProvider.getUserId(command.refreshToken());
		User user = userRepositoryPort.findById(userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

		// tokenVersion 검증 — 탈퇴/로그아웃 후 refresh 시도 차단
		int tokenVersion = jwtProvider.getTokenVersion(command.refreshToken());
		if (tokenVersion != user.getTokenVersion()) {
			throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
		}

		String accessToken = jwtProvider.createAccessToken(user.getId(), user.getProvider(), user.getTokenVersion());

		return new RefreshResult(accessToken, jwtProvider.getAccessTokenExpirationSeconds());
	}
}
