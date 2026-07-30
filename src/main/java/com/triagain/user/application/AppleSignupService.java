package com.triagain.user.application;

import com.triagain.common.auth.JwtProvider;
import com.triagain.common.exception.BusinessException;
import com.triagain.common.exception.ErrorCode;
import com.triagain.user.domain.model.User;
import com.triagain.user.port.in.AppleSignupUseCase;
import com.triagain.user.port.out.AppleOAuthPort;
import com.triagain.user.port.out.AppleTokenVerifierPort;
import com.triagain.user.port.out.AppleTokenVerifierPort.AppleUserInfo;
import com.triagain.user.port.out.UserRepositoryPort;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppleSignupService implements AppleSignupUseCase {

	private final AppleTokenVerifierPort appleTokenVerifierPort;
	private final AppleOAuthPort appleOAuthPort;
	private final UserRepositoryPort userRepositoryPort;
	private final JwtProvider jwtProvider;
	private final AppleSignupService self;

	public AppleSignupService(
			AppleTokenVerifierPort appleTokenVerifierPort,
			AppleOAuthPort appleOAuthPort,
			UserRepositoryPort userRepositoryPort,
			JwtProvider jwtProvider,
			@Lazy AppleSignupService self
	) {
		this.appleTokenVerifierPort = appleTokenVerifierPort;
		this.appleOAuthPort = appleOAuthPort;
		this.userRepositoryPort = userRepositoryPort;
		this.jwtProvider = jwtProvider;
		this.self = self;
	}

	/**
	 * Apple 회원가입 진입점 — 트랜잭션 밖에서 외부 호출(Apple /auth/token 교환) 후
	 * 트랜잭션 안에서 유저 생성/저장(self.persistSignup). 외부 API와 DB 트랜잭션 분리.
	 */
	@Override
	public AppleSignupResult signup(AppleSignupCommand command) {
		String trimmedNickname = command.nickname() != null ? command.nickname().trim() : null;
		User.validateNickname(trimmedNickname);

		if (!command.termsAgreed()) {
			throw new BusinessException(ErrorCode.TERMS_NOT_AGREED);
		}

		AppleUserInfo appleUser = appleTokenVerifierPort.verify(command.identityToken());

		if (!appleUser.sub().equals(command.appleId())) {
			throw new BusinessException(ErrorCode.APPLE_ID_MISMATCH);
		}

		// 트랜잭션 밖에서 Apple OAuth refresh_token 교환 (회원가입은 차단형 — 실패 시 예외)
		String appleRefreshToken = appleOAuthPort.exchangeAuthorizationCode(command.authorizationCode());

		return self.persistSignup(appleUser, trimmedNickname, appleRefreshToken);
	}

	/** Apple 회원가입 트랜잭션 영역 — 중복 확인 → 유저 생성/재활성화 → JWT 발급 */
	@Transactional
	public AppleSignupResult persistSignup(AppleUserInfo appleUser, String trimmedNickname, String appleRefreshToken) {
		var existingUser = userRepositoryPort.findByIdIncludingWithdrawn(appleUser.sub());
		if (existingUser.isPresent()) {
			User existing = existingUser.get();
			if (!existing.isWithdrawn()) {
				throw new BusinessException(ErrorCode.USER_ALREADY_EXISTS);
			}
			existing.reactivate(trimmedNickname, appleUser.email(), null, appleRefreshToken);
			User saved = userRepositoryPort.save(existing);
			return buildResult(saved);
		}

		User user = User.createFromApple(appleUser.sub(), trimmedNickname, appleUser.email(), appleRefreshToken);
		User saved = userRepositoryPort.save(user);
		return buildResult(saved);
	}

	private AppleSignupResult buildResult(User saved) {
		String accessToken = jwtProvider.createAccessToken(saved.getId(), saved.getProvider(), saved.getTokenVersion());
		String refreshToken = jwtProvider.createRefreshToken(saved.getId(), saved.getTokenVersion());
		return new AppleSignupResult(
				accessToken,
				refreshToken,
				jwtProvider.getAccessTokenExpirationSeconds(),
				new AppleSignupUserInfo(saved.getId(), saved.getNickname(), saved.getProfileImageUrl())
		);
	}
}
