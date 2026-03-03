package com.triagain.user.domain.model;

import com.triagain.common.exception.BusinessException;
import com.triagain.common.exception.ErrorCode;

import java.time.LocalDateTime;
import java.util.regex.Pattern;

public class User {

    private static final Pattern NICKNAME_PATTERN = Pattern.compile("^[가-힣a-zA-Z0-9_]{2,12}$");

    private final String id;
    private final String provider;
    private String email;
    private String nickname;
    private String profileImageUrl;
    private final LocalDateTime createdAt;
    private final LocalDateTime termsAgreedAt;

    private User(String id, String provider, String email, String nickname,
                 String profileImageUrl, LocalDateTime createdAt, LocalDateTime termsAgreedAt) {
        this.id = id;
        this.provider = provider;
        this.email = email;
        this.nickname = nickname;
        this.profileImageUrl = profileImageUrl;
        this.createdAt = createdAt;
        this.termsAgreedAt = termsAgreedAt;
    }

    /** 카카오 회원가입으로 신규 유저 생성 — kakaoId를 PK로 사용, 약관 동의 필수 */
    public static User createFromKakao(String kakaoId, String nickname, String email, String profileImageUrl) {
        validateNickname(nickname);
        return new User(
                kakaoId,
                "KAKAO",
                email,
                nickname,
                profileImageUrl,
                LocalDateTime.now(),
                LocalDateTime.now()
        );
    }

    /** DB 조회 결과 → 도메인 객체 복원 */
    public static User of(String id, String provider, String email, String nickname,
                          String profileImageUrl, LocalDateTime createdAt, LocalDateTime termsAgreedAt) {
        return new User(id, provider, email, nickname, profileImageUrl, createdAt, termsAgreedAt);
    }

    /** 닉네임 검증 — 2~12자, 한글/영문/숫자/언더스코어만 허용 */
    public static void validateNickname(String nickname) {
        if (nickname == null || nickname.isBlank()) {
            throw new BusinessException(ErrorCode.NICKNAME_REQUIRED);
        }
        String trimmed = nickname.trim();
        if (!NICKNAME_PATTERN.matcher(trimmed).matches()) {
            throw new BusinessException(ErrorCode.INVALID_NICKNAME);
        }
    }

    /** 프로필 수정 — 닉네임/프로필 이미지 변경 */
    public void updateProfile(String nickname, String profileImageUrl) {
        if (nickname != null && !nickname.isBlank()) {
            validateNickname(nickname);
            this.nickname = nickname.trim();
        }
        if (profileImageUrl != null) {
            this.profileImageUrl = profileImageUrl;
        }
    }

    /** 카카오 재로그인 시 프로필 갱신 */
    public void updateKakaoProfile(String nickname, String email, String profileImageUrl) {
        if (nickname != null && !nickname.isBlank()) {
            this.nickname = nickname;
        }
        this.email = email;
        if (profileImageUrl != null) {
            this.profileImageUrl = profileImageUrl;
        }
    }

    public String getId() {
        return id;
    }

    public String getProvider() {
        return provider;
    }

    public String getEmail() {
        return email;
    }

    public String getNickname() {
        return nickname;
    }

    public String getProfileImageUrl() {
        return profileImageUrl;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getTermsAgreedAt() {
        return termsAgreedAt;
    }
}
