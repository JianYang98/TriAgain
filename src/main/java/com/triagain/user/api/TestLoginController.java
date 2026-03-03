package com.triagain.user.api;

import com.triagain.common.auth.JwtProvider;
import com.triagain.common.exception.BusinessException;
import com.triagain.common.exception.ErrorCode;
import com.triagain.common.response.ApiResponse;
import com.triagain.user.domain.model.User;
import com.triagain.user.port.in.KakaoLoginUseCase.KakaoLoginResult;
import com.triagain.user.port.in.KakaoLoginUseCase.LoginUserInfo;
import com.triagain.user.port.out.UserRepositoryPort;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@Profile("!prod")
@RequiredArgsConstructor
public class TestLoginController {

    private final UserRepositoryPort userRepositoryPort;
    private final JwtProvider jwtProvider;

    /** 테스트 로그인 — userId로 JWT 발급 (dev/test 전용, prod에서는 빈 자체가 로드되지 않음) */
    @PostMapping("/test-login")
    public ResponseEntity<ApiResponse<KakaoLoginResult>> testLogin(
            @Valid @RequestBody TestLoginRequest request) {

        User user = userRepositoryPort.findById(request.userId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        String accessToken = jwtProvider.createAccessToken(user.getId(), user.getProvider());
        String refreshToken = jwtProvider.createRefreshToken(user.getId());

        return ResponseEntity.ok(ApiResponse.ok(
                KakaoLoginResult.existingUser(
                        accessToken, refreshToken,
                        jwtProvider.getAccessTokenExpirationSeconds(),
                        new LoginUserInfo(user.getId(), user.getNickname(), user.getProfileImageUrl())
                )
        ));
    }

    record TestLoginRequest(@NotBlank String userId) {}
}
