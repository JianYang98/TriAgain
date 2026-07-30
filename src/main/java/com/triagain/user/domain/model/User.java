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
	private String fcmToken;
	private String appleRefreshToken;
	private final LocalDateTime createdAt;
	private final LocalDateTime termsAgreedAt;
	private LocalDateTime deletedAt;
	private int tokenVersion;

	private User(String id, String provider, String email, String nickname,
				String profileImageUrl, String fcmToken, String appleRefreshToken,
				LocalDateTime createdAt, LocalDateTime termsAgreedAt,
				LocalDateTime deletedAt, int tokenVersion) {
		this.id = id;
		this.provider = provider;
		this.email = email;
		this.nickname = nickname;
		this.profileImageUrl = profileImageUrl;
		this.fcmToken = fcmToken;
		this.appleRefreshToken = appleRefreshToken;
		this.createdAt = createdAt;
		this.termsAgreedAt = termsAgreedAt;
		this.deletedAt = deletedAt;
		this.tokenVersion = tokenVersion;
	}

	/** 카카오 회원가입으로 신규 유저 생성 — kakaoId를 PK로 사용, 약관 동의 필수 */
	public static User createFromKakao(String kakaoId, String nickname, String email, String profileImageUrl) {
		validateNickname(nickname);
		return new User(
				kakaoId, "KAKAO", email, nickname, profileImageUrl, null, null,
				LocalDateTime.now(), LocalDateTime.now(), null, 0
		);
	}

	/** Apple 회원가입으로 신규 유저 생성 — appleId(sub)를 PK로 사용, 프로필 이미지 없음, refresh_token 저장 */
	public static User createFromApple(String appleId, String nickname, String email, String appleRefreshToken) {
		validateNickname(nickname);
		return new User(
				appleId, "APPLE", email, nickname, null, null, appleRefreshToken,
				LocalDateTime.now(), LocalDateTime.now(), null, 0
		);
	}

	/** DB 조회 결과 → 도메인 객체 복원 */
	public static User of(String id, String provider, String email, String nickname,
						String profileImageUrl, String fcmToken, String appleRefreshToken,
						LocalDateTime createdAt, LocalDateTime termsAgreedAt,
						LocalDateTime deletedAt, int tokenVersion) {
		return new User(id, provider, email, nickname, profileImageUrl, fcmToken, appleRefreshToken,
				createdAt, termsAgreedAt, deletedAt, tokenVersion);
	}

	/** 회원탈퇴 — 개인정보 초기화 + 토큰 무효화 + Apple refresh_token 제거 */
	public void withdraw() {
		this.nickname = "탈퇴한 사용자";
		this.email = null;
		this.profileImageUrl = null;
		this.fcmToken = null;
		this.appleRefreshToken = null;
		this.deletedAt = LocalDateTime.now();
		this.tokenVersion++;
	}

	/** 토큰 버전 증가 — 로그아웃 시 기존 토큰 무효화 */
	public void incrementTokenVersion() {
		this.tokenVersion++;
	}

	/** 탈퇴 여부 확인 */
	public boolean isWithdrawn() {
		return deletedAt != null;
	}

	/** 탈퇴 계정 재활성화 — 동일 소셜 계정으로 재가입 시 사용 (Apple은 appleRefreshToken도 함께 갱신) */
	public void reactivate(String nickname, String email, String profileImageUrl, String appleRefreshToken) {
		validateNickname(nickname);
		this.nickname = nickname.trim();
		this.email = email;
		this.profileImageUrl = profileImageUrl;
		this.appleRefreshToken = appleRefreshToken;
		this.deletedAt = null;
		this.tokenVersion++;
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

	/** 프로필 이미지 변경 — null이면 기본 이미지로 리셋 */
	public void updateProfileImage(String profileImageUrl) {
		this.profileImageUrl = profileImageUrl;
	}

	/** Apple 재로그인 시 email 동기화 — email이 null이면 기존값 유지 (Apple은 최초 1회만 email 제공) */
	public boolean syncAppleProfile(String email) {
		if (email != null && !java.util.Objects.equals(this.email, email)) {
			this.email = email;
			return true;
		}
		return false;
	}

	/** 카카오 email 수동 동기화 — email이 null이면 기존값 유지. 현재 재로그인에서는 미사용 */
	public boolean syncKakaoProfile(String email) {
		if (email != null && !java.util.Objects.equals(this.email, email)) {
			this.email = email;
			return true;
		}
		return false;
	}

	public String getId() { return id; }
	public String getProvider() { return provider; }
	public String getEmail() { return email; }
	public String getNickname() { return nickname; }
	public String getProfileImageUrl() { return profileImageUrl; }
	public LocalDateTime getCreatedAt() { return createdAt; }
	public LocalDateTime getTermsAgreedAt() { return termsAgreedAt; }
	public LocalDateTime getDeletedAt() { return deletedAt; }
	public int getTokenVersion() { return tokenVersion; }

	/** FCM 토큰 갱신 — 앱 실행/로그인 시 클라이언트가 호출 */
	public void updateFcmToken(String fcmToken) {
		this.fcmToken = fcmToken;
	}

	public String getFcmToken() { return fcmToken; }

	/** Apple OAuth refresh_token 갱신 — Apple 로그인 backfill 또는 재가입 시 */
	public void updateAppleRefreshToken(String appleRefreshToken) {
		this.appleRefreshToken = appleRefreshToken;
	}

	public String getAppleRefreshToken() { return appleRefreshToken; }
}
