package com.triagain.user.infra;

import com.triagain.common.security.crypto.AesGcmStringConverter;
import com.triagain.user.domain.model.User;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
public class UserJpaEntity {

	@Id
	@Column(length = 64)
	private String id;

	@Column(nullable = false, length = 20)
	private String provider;

	@Column
	private String email;

	@Column(nullable = false)
	private String nickname;

	@Column(name = "profile_image_url")
	private String profileImageUrl;

	@Column(name = "fcm_token", length = 500)
	private String fcmToken;

	@Convert(converter = AesGcmStringConverter.class)
	@Column(name = "apple_refresh_token", length = 1024)
	private String appleRefreshToken;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(name = "terms_agreed_at")
	private LocalDateTime termsAgreedAt;

	@Column(name = "deleted_at")
	private LocalDateTime deletedAt;

	@Column(name = "token_version", nullable = false)
	private int tokenVersion;

	protected UserJpaEntity() {
	}

	public User toDomain() {
		return User.of(id, provider, email, nickname, profileImageUrl, fcmToken, appleRefreshToken,
				createdAt, termsAgreedAt, deletedAt, tokenVersion);
	}

	public static UserJpaEntity fromDomain(User user) {
		UserJpaEntity entity = new UserJpaEntity();
		entity.id = user.getId();
		entity.provider = user.getProvider();
		entity.email = user.getEmail();
		entity.nickname = user.getNickname();
		entity.profileImageUrl = user.getProfileImageUrl();
		entity.fcmToken = user.getFcmToken();
		entity.appleRefreshToken = user.getAppleRefreshToken();
		entity.createdAt = user.getCreatedAt();
		entity.termsAgreedAt = user.getTermsAgreedAt();
		entity.deletedAt = user.getDeletedAt();
		entity.tokenVersion = user.getTokenVersion();
		return entity;
	}
}