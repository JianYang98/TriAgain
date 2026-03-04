package com.triagain.user.api;

import com.triagain.common.response.ApiResponse;
import com.triagain.user.port.in.AppleLoginUseCase;
import com.triagain.user.port.in.AppleLoginUseCase.AppleLoginCommand;
import com.triagain.user.port.in.AppleLoginUseCase.AppleLoginResult;
import com.triagain.user.port.in.AppleSignupUseCase;
import com.triagain.user.port.in.AppleSignupUseCase.AppleSignupCommand;
import com.triagain.user.port.in.AppleSignupUseCase.AppleSignupResult;
import com.triagain.user.port.in.KakaoLoginUseCase;
import com.triagain.user.port.in.KakaoLoginUseCase.KakaoLoginCommand;
import com.triagain.user.port.in.KakaoLoginUseCase.KakaoLoginResult;
import com.triagain.user.port.in.RefreshTokenUseCase;
import com.triagain.user.port.in.RefreshTokenUseCase.RefreshCommand;
import com.triagain.user.port.in.RefreshTokenUseCase.RefreshResult;
import com.triagain.user.port.in.SignupUseCase;
import com.triagain.user.port.in.SignupUseCase.SignupCommand;
import com.triagain.user.port.in.SignupUseCase.SignupResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
    private final AppleLoginUseCase appleLoginUseCase;
    private final AppleSignupUseCase appleSignupUseCase;
    private final RefreshTokenUseCase refreshTokenUseCase;
    private final SignupUseCase signupUseCase;

    /** 카카오 로그인 — 기존 유저면 JWT 발급, 신규 유저면 카카오 프로필 반환 */
    @PostMapping("/kakao")
    public ResponseEntity<ApiResponse<KakaoLoginResult>> kakaoLogin(
            @Valid @RequestBody KakaoLoginRequest request
    ) {
        KakaoLoginResult result = kakaoLoginUseCase.login(new KakaoLoginCommand(request.kakaoAccessToken()));
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    /** 회원가입 — 카카오 인증 + 약관 동의 + 닉네임으로 신규 유저 생성 */
    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<SignupResult>> signup(
            @Valid @RequestBody SignupRequest request
    ) {
        SignupResult result = signupUseCase.signup(new SignupCommand(
                request.kakaoAccessToken(), request.kakaoId(),
                request.nickname(), request.termsAgreed()
        ));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(result));
    }

    /** Access Token 갱신 — Refresh Token으로 새 Access Token 발급 */
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<RefreshResult>> refresh(
            @Valid @RequestBody RefreshTokenRequest request
    ) {
        RefreshResult result = refreshTokenUseCase.refresh(new RefreshCommand(request.refreshToken()));
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    /** Apple 로그인 — 기존 유저면 JWT 발급, 신규 유저면 appleId 반환 */
    @PostMapping("/apple")
    public ResponseEntity<ApiResponse<AppleLoginResult>> appleLogin(
            @Valid @RequestBody AppleLoginRequest request
    ) {
        AppleLoginResult result = appleLoginUseCase.login(new AppleLoginCommand(request.identityToken()));
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    /** Apple 회원가입 — Apple 인증 + 약관 동의 + 닉네임으로 신규 유저 생성 */
    @PostMapping("/apple-signup")
    public ResponseEntity<ApiResponse<AppleSignupResult>> appleSignup(
            @Valid @RequestBody AppleSignupRequest request
    ) {
        AppleSignupResult result = appleSignupUseCase.signup(new AppleSignupCommand(
                request.identityToken(), request.appleId(),
                request.nickname(), request.termsAgreed()
        ));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(result));
    }

    /** 로그아웃 — Phase 1: no-op, 클라이언트가 토큰 삭제 */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout() {
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    record KakaoLoginRequest(@NotBlank String kakaoAccessToken) {
    }

    record SignupRequest(
            @NotBlank String kakaoAccessToken,
            @NotBlank String kakaoId,
            @NotBlank String nickname,
            @NotNull Boolean termsAgreed
    ) {
    }

    record RefreshTokenRequest(@NotBlank String refreshToken) {
    }

    record AppleLoginRequest(@NotBlank String identityToken) {
    }

    record AppleSignupRequest(
            @NotBlank String identityToken,
            @NotBlank String appleId,
            @NotBlank String nickname,
            @NotNull Boolean termsAgreed
    ) {
    }
}
