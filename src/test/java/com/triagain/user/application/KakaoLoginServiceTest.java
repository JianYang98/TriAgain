package com.triagain.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.triagain.common.auth.JwtProvider;
import com.triagain.common.exception.BusinessException;
import com.triagain.common.exception.ErrorCode;
import com.triagain.user.domain.model.User;
import com.triagain.user.port.in.KakaoLoginUseCase.KakaoLoginCommand;
import com.triagain.user.port.in.KakaoLoginUseCase.KakaoLoginResult;
import com.triagain.user.port.out.KakaoApiPort;
import com.triagain.user.port.out.KakaoApiPort.KakaoUserInfo;
import com.triagain.user.port.out.UserRepositoryPort;

@ExtendWith(MockitoExtension.class)
class KakaoLoginServiceTest {

	@InjectMocks
	private KakaoLoginService kakaoLoginService;

	@Mock
	private KakaoApiPort kakaoApiPort;

	@Mock
	private UserRepositoryPort userRepositoryPort;

	@Mock
	private JwtProvider jwtProvider;

	private KakaoUserInfo kakaoUserInfo;

	@BeforeEach
	void setUp() {
		kakaoUserInfo = new KakaoUserInfo("12345", "카카오유저", "kakao@test.com", "https://img.kakao.com/profile.jpg");
	}

	@Test
	@DisplayName("신규 유저 — isNewUser=true + 카카오 프로필 반환, JWT 미발급")
	void login_newUser_returnsKakaoProfileWithoutJwt() {
		// Given
		given(kakaoApiPort.getUserInfo("valid-token")).willReturn(kakaoUserInfo);
		given(userRepositoryPort.findById("12345")).willReturn(Optional.empty());

		// When
		KakaoLoginResult result = kakaoLoginService.login(new KakaoLoginCommand("valid-token"));

		// Then
		assertThat(result.isNewUser()).isTrue();
		assertThat(result.kakaoId()).isEqualTo("12345");
		assertThat(result.kakaoProfile().nickname()).isEqualTo("카카오유저");
		assertThat(result.kakaoProfile().email()).isEqualTo("kakao@test.com");
		assertThat(result.kakaoProfile().profileImageUrl()).isEqualTo("https://img.kakao.com/profile.jpg");
		assertThat(result.accessToken()).isNull();
		assertThat(result.refreshToken()).isNull();
		assertThat(result.user()).isNull();
		verify(userRepositoryPort, never()).save(any(User.class));
	}

	@Test
	@DisplayName("기존 유저 — 재로그인 시 email/프로필 동기화하지 않고 JWT만 발급")
	void login_existingUser_returnsJwtWithoutSync() {
		// Given — 카카오 email과 다른 email을 가진 기존 유저
		User existingUser = User.of("12345", "KAKAO", "old@test.com", "기존유저",
				"https://my-custom.com/photo.jpg",
				null, null, LocalDateTime.now(), LocalDateTime.now(), null, 0);
		given(kakaoApiPort.getUserInfo("valid-token")).willReturn(kakaoUserInfo);
		given(userRepositoryPort.findById("12345")).willReturn(Optional.of(existingUser));
		given(jwtProvider.createAccessToken(anyString(), anyString(), anyInt())).willReturn("access-token");
		given(jwtProvider.createRefreshToken(anyString(), anyInt())).willReturn("refresh-token");
		given(jwtProvider.getAccessTokenExpirationSeconds()).willReturn(1800L);

		// When
		KakaoLoginResult result = kakaoLoginService.login(new KakaoLoginCommand("valid-token"));

		// Then
		assertThat(result.isNewUser()).isFalse();
		assertThat(result.accessToken()).isEqualTo("access-token");
		assertThat(result.user().nickname()).isEqualTo("기존유저");
		assertThat(result.user().profileImageUrl()).isEqualTo("https://my-custom.com/photo.jpg");
		verify(userRepositoryPort, never()).save(any(User.class)); // 재로그인 시 save 호출 안 함
	}

	@Test
	@DisplayName("유효하지 않은 카카오 토큰 — INVALID_KAKAO_TOKEN 예외")
	void login_invalidToken_throwsException() {
		// Given
		given(kakaoApiPort.getUserInfo("invalid-token"))
				.willThrow(new BusinessException(ErrorCode.INVALID_KAKAO_TOKEN));

		// When & Then
		assertThatThrownBy(() -> kakaoLoginService.login(new KakaoLoginCommand("invalid-token")))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.INVALID_KAKAO_TOKEN);
	}

	@Test
	@DisplayName("카카오 API 장애 — KAKAO_API_ERROR 예외")
	void login_kakaoApiError_throwsException() {
		// Given
		given(kakaoApiPort.getUserInfo("any-token"))
				.willThrow(new BusinessException(ErrorCode.KAKAO_API_ERROR));

		// When & Then
		assertThatThrownBy(() -> kakaoLoginService.login(new KakaoLoginCommand("any-token")))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.KAKAO_API_ERROR);
	}

	@Test
	@DisplayName("카카오 email이 null인 신규 유저 — 정상적으로 isNewUser 반환")
	void login_nullEmail_newUser_succeeds() {
		// Given
		KakaoUserInfo noEmailUser = new KakaoUserInfo("12345", "카카오유저", null, null);
		given(kakaoApiPort.getUserInfo("valid-token")).willReturn(noEmailUser);
		given(userRepositoryPort.findById("12345")).willReturn(Optional.empty());

		// When
		KakaoLoginResult result = kakaoLoginService.login(new KakaoLoginCommand("valid-token"));

		// Then
		assertThat(result.isNewUser()).isTrue();
		assertThat(result.kakaoProfile().email()).isNull();
	}
}
