package com.triagain.user.api;

import com.triagain.common.response.ApiResponse;
import com.triagain.user.port.in.KakaoLoginUseCase;
import com.triagain.user.port.in.KakaoLoginUseCase.KakaoLoginCommand;
import com.triagain.user.port.in.KakaoLoginUseCase.KakaoLoginResult;
import com.triagain.user.port.in.RefreshTokenUseCase;
import com.triagain.user.port.in.RefreshTokenUseCase.RefreshCommand;
import com.triagain.user.port.in.RefreshTokenUseCase.RefreshResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final KakaoLoginUseCase kakaoLoginUseCase;
    private final RefreshTokenUseCase refreshTokenUseCase;

    /** 카카오 로그인 — 카카오 Access Token으로 자체 JWT 발급 */
    @PostMapping("/kakao")
    public ResponseEntity<ApiResponse<KakaoLoginResult>> kakaoLogin(
            @Valid @RequestBody KakaoLoginRequest request
    ) {
        KakaoLoginResult result = kakaoLoginUseCase.login(new KakaoLoginCommand(request.kakaoAccessToken()));
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    /** Access Token 갱신 — Refresh Token으로 새 Access Token 발급 */
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<RefreshResult>> refresh(
            @Valid @RequestBody RefreshTokenRequest request
    ) {
        RefreshResult result = refreshTokenUseCase.refresh(new RefreshCommand(request.refreshToken()));
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    record KakaoLoginRequest(@NotBlank String kakaoAccessToken) {
    }

    record RefreshTokenRequest(@NotBlank String refreshToken) {
    }
}
