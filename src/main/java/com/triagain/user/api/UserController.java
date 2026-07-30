package com.triagain.user.api;

import com.triagain.common.auth.AuthenticatedUser;
import com.triagain.common.response.ApiResponse;
import com.triagain.user.port.in.CreateProfileUploadSessionUseCase;
import com.triagain.user.port.in.CreateProfileUploadSessionUseCase.CreateProfileUploadSessionCommand;
import com.triagain.user.port.in.CreateProfileUploadSessionUseCase.ProfileUploadSessionResult;
import com.triagain.user.port.in.GetUserUseCase;
import com.triagain.user.port.in.UpdateFcmTokenUseCase;
import com.triagain.user.port.in.UpdateUserProfileUseCase;
import com.triagain.user.port.in.UpdateUserProfileUseCase.UpdateProfileCommand;
import com.triagain.user.port.in.WithdrawUserUseCase;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

	private final GetUserUseCase getUserUseCase;
	private final UpdateUserProfileUseCase updateUserProfileUseCase;
	private final CreateProfileUploadSessionUseCase createProfileUploadSessionUseCase;
	private final UpdateFcmTokenUseCase updateFcmTokenUseCase;
	private final WithdrawUserUseCase withdrawUserUseCase;

	/** 내 프로필 조회 — 마이페이지에서 사용 */
	@GetMapping("/me")
	public ResponseEntity<ApiResponse<MyProfileResponse>> getMyProfile(
			@AuthenticatedUser String userId) {
		GetUserUseCase.UserResult result = getUserUseCase.getUser(userId);
		return ResponseEntity.ok(ApiResponse.ok(MyProfileResponse.from(result)));
	}

	/** 닉네임 변경 — 변경 후 전체 프로필 반환 */
	@PatchMapping("/me/nickname")
	public ResponseEntity<ApiResponse<MyProfileResponse>> updateNickname(
			@AuthenticatedUser String userId,
			@Valid @RequestBody UpdateNicknameRequest request) {
		UpdateProfileCommand command = new UpdateProfileCommand(userId, request.nickname(), null);
		UpdateUserProfileUseCase.UpdateProfileResult result = updateUserProfileUseCase.updateProfile(command);
		return ResponseEntity.ok(ApiResponse.ok(MyProfileResponse.from(result)));
	}

	/** 회원탈퇴 — 개인정보 초기화 + 크루 멤버십 정리 + 토큰 무효화 */
	@DeleteMapping("/me")
	public ResponseEntity<ApiResponse<Void>> withdraw(@AuthenticatedUser String userId) {
		withdrawUserUseCase.withdraw(userId);
		return ResponseEntity.ok(ApiResponse.ok());
	}

	/** 프로필 이미지 업로드 세션 생성 — presigned URL 발급 */
	@PostMapping("/me/profile-image/upload-session")
	public ResponseEntity<ApiResponse<ProfileUploadSessionResponse>> createProfileUploadSession(
			@AuthenticatedUser String userId,
			@Valid @RequestBody CreateProfileUploadSessionRequest request) {
		ProfileUploadSessionResult result = createProfileUploadSessionUseCase.createSession(
				new CreateProfileUploadSessionCommand(
						userId, request.fileName(), request.fileType(), request.fileSize()));

		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.ok(ProfileUploadSessionResponse.from(result)));
	}

	/** 프로필 이미지 변경 확정 — S3 업로드 후 DB 반영 */
	@PatchMapping("/me/profile-image")
	public ResponseEntity<ApiResponse<MyProfileResponse>> updateProfileImage(
			@AuthenticatedUser String userId,
			@Valid @RequestBody UpdateProfileImageRequest request) {
		UpdateUserProfileUseCase.UpdateProfileResult result =
				updateUserProfileUseCase.updateProfileImage(userId, request.imageUrl());
		return ResponseEntity.ok(ApiResponse.ok(MyProfileResponse.from(result)));
	}

	/** FCM 토큰 등록/갱신 — 앱 실행 시 클라이언트가 호출 */
	@PatchMapping("/me/fcm-token")
	public ResponseEntity<ApiResponse<Void>> updateFcmToken(
			@AuthenticatedUser String userId,
			@Valid @RequestBody UpdateFcmTokenRequest request) {
		updateFcmTokenUseCase.updateFcmToken(userId, request.fcmToken());
		return ResponseEntity.ok(ApiResponse.ok());
	}

	record CreateProfileUploadSessionRequest(
			@NotBlank String fileName,
			@NotBlank String fileType,
			@NotNull Long fileSize
	) {
	}

	record UpdateProfileImageRequest(
			String imageUrl
	) {
	}

	record ProfileUploadSessionResponse(
			String presignedUrl,
			String imageUrl,
			LocalDateTime expiresAt
	) {
		static ProfileUploadSessionResponse from(ProfileUploadSessionResult r) {
			return new ProfileUploadSessionResponse(r.presignedUrl(), r.imageUrl(), r.expiresAt());
		}
	}

	record UpdateFcmTokenRequest(
			@NotBlank
			@Size(max = 500, message = "FCM 토큰이 너무 깁니다")
			String fcmToken
	) {
	}

	record UpdateNicknameRequest(
			@NotBlank
			@Pattern(regexp = "^[가-힣a-zA-Z0-9_]{2,12}$", message = "INVALID_NICKNAME")
			String nickname
	) {
	}

	record MyProfileResponse(String id, String nickname, String profileImageUrl, String email) {
		static MyProfileResponse from(GetUserUseCase.UserResult r) {
			return new MyProfileResponse(r.id(), r.nickname(), r.profileImageUrl(), r.email());
		}

		static MyProfileResponse from(UpdateUserProfileUseCase.UpdateProfileResult r) {
			return new MyProfileResponse(r.id(), r.nickname(), r.profileImageUrl(), r.email());
		}
	}
}
