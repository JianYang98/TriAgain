package com.triagain.user.api;

import com.triagain.common.auth.AuthenticatedUser;
import com.triagain.common.response.ApiResponse;
import com.triagain.user.port.in.GetUserUseCase;
import com.triagain.user.port.in.UpdateUserProfileUseCase;
import com.triagain.user.port.in.UpdateUserProfileUseCase.UpdateProfileCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final GetUserUseCase getUserUseCase;
    private final UpdateUserProfileUseCase updateUserProfileUseCase;

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
