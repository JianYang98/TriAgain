package com.triagain.user.application;

import com.triagain.common.exception.BusinessException;
import com.triagain.common.exception.ErrorCode;
import com.triagain.user.domain.model.User;
import com.triagain.user.port.out.AppleOAuthPort;
import com.triagain.user.port.out.CrewMembershipPort;
import com.triagain.user.port.out.CrewMembershipPort.MembershipInfo;
import com.triagain.user.port.out.UserRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class WithdrawUserServiceTest {

    @Mock
    private UserRepositoryPort userRepositoryPort;

    @Mock
    private CrewMembershipPort crewMembershipPort;

    @Mock
    private AppleOAuthPort appleOAuthPort;

    private WithdrawUserService withdrawUserService;

    @BeforeEach
    void setUp() {
        // self-injection을 단위 테스트에서 만들기 위해 자기 자신을 self로 주입
        withdrawUserService = new WithdrawUserService(userRepositoryPort, crewMembershipPort, appleOAuthPort, null);
        ReflectionTestUtils.setField(withdrawUserService, "self", withdrawUserService);
    }

    private User createActiveKakaoUser(String userId) {
        return User.of(userId, "KAKAO", "test@test.com", "테스트", null, null, null,
                LocalDateTime.now(), LocalDateTime.now(), null, 0);
    }

    private User createActiveAppleUser(String userId, String appleRefreshToken) {
        return User.of(userId, "APPLE", "apple@test.com", "애플유저", null, null, appleRefreshToken,
                LocalDateTime.now(), LocalDateTime.now(), null, 0);
    }

    private User createWithdrawnUser(String userId) {
        return User.of(userId, "KAKAO", null, "탈퇴한 사용자", null, null, null,
                LocalDateTime.now(), LocalDateTime.now(), LocalDateTime.now(), 1);
    }

    @DisplayName("정상 탈퇴 — MEMBER인 크루에서 제거되고 user.withdraw() 호출된다")
    @Test
    void withdraw_memberUser_removesFromCrewAndWithdraws() {
        // Given
        String userId = "user-1";
        User user = createActiveKakaoUser(userId);
        MembershipInfo membership = new MembershipInfo("crew-1", "MEMBER", "ACTIVE", 5);

        given(userRepositoryPort.findById(userId)).willReturn(Optional.of(user));
        given(crewMembershipPort.findAllByUserId(userId)).willReturn(List.of(membership));
        given(userRepositoryPort.save(any(User.class))).willAnswer(inv -> inv.getArgument(0));

        // When
        withdrawUserService.withdraw(userId);

        // Then
        assertThat(user.isWithdrawn()).isTrue();
        assertThat(user.getNickname()).isEqualTo("탈퇴한 사용자");
        verify(crewMembershipPort).endActiveChallenges(userId, "crew-1");
        verify(crewMembershipPort).removeMember("crew-1", userId);
        verify(crewMembershipPort, never()).deleteCrewWithAllData(any());
        verify(userRepositoryPort).save(user);
    }

    @DisplayName("LEADER + 혼자인 크루 → deleteCrewWithAllData() 호출된다")
    @Test
    void withdraw_leaderAlone_deletesCrewWithAllData() {
        // Given
        String userId = "user-1";
        User user = createActiveKakaoUser(userId);
        MembershipInfo membership = new MembershipInfo("crew-1", "LEADER", "ACTIVE", 1);

        given(userRepositoryPort.findById(userId)).willReturn(Optional.of(user));
        given(crewMembershipPort.findAllByUserId(userId)).willReturn(List.of(membership));
        given(userRepositoryPort.save(any(User.class))).willAnswer(inv -> inv.getArgument(0));

        // When
        withdrawUserService.withdraw(userId);

        // Then
        assertThat(user.isWithdrawn()).isTrue();
        verify(crewMembershipPort).endActiveChallenges(userId, "crew-1");
        verify(crewMembershipPort).deleteCrewWithAllData("crew-1");
        verify(crewMembershipPort, never()).removeMember(any(), any());
        verify(userRepositoryPort).save(user);
    }

    @DisplayName("LEADER + 멤버 있는 크루 → LEADER_CANNOT_WITHDRAW 예외")
    @Test
    void withdraw_leaderWithMembers_throwsLeaderCannotWithdraw() {
        // Given
        String userId = "user-1";
        User user = createActiveKakaoUser(userId);
        MembershipInfo membership = new MembershipInfo("crew-1", "LEADER", "ACTIVE", 3);

        given(userRepositoryPort.findById(userId)).willReturn(Optional.of(user));
        given(crewMembershipPort.findAllByUserId(userId)).willReturn(List.of(membership));

        // When & Then
        assertThatThrownBy(() -> withdrawUserService.withdraw(userId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.LEADER_CANNOT_WITHDRAW);

        verify(userRepositoryPort, never()).save(any());
        verify(crewMembershipPort, never()).removeMember(any(), any());
        verify(crewMembershipPort, never()).deleteCrewWithAllData(any());
        verify(appleOAuthPort, never()).revokeRefreshToken(any());
    }

    @DisplayName("이미 탈퇴한 유저 → USER_WITHDRAWN 예외")
    @Test
    void withdraw_alreadyWithdrawn_throwsUserWithdrawn() {
        // Given
        String userId = "user-1";
        User user = createWithdrawnUser(userId);

        given(userRepositoryPort.findById(userId)).willReturn(Optional.of(user));

        // When & Then
        assertThatThrownBy(() -> withdrawUserService.withdraw(userId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.USER_WITHDRAWN);

        verify(crewMembershipPort, never()).findAllByUserId(any());
        verify(userRepositoryPort, never()).save(any());
        verify(appleOAuthPort, never()).revokeRefreshToken(any());
    }

    @DisplayName("Apple 사용자 + refresh_token 있으면 → Apple revoke 호출되고 정상 탈퇴된다")
    @Test
    void withdraw_appleUserWithRefreshToken_callsRevokeAndWithdraws() {
        // Given
        String userId = "001234.abcdef.5678";
        User user = createActiveAppleUser(userId, "apple-refresh-token-xyz");

        given(userRepositoryPort.findById(userId)).willReturn(Optional.of(user));
        given(crewMembershipPort.findAllByUserId(userId)).willReturn(List.of());
        given(userRepositoryPort.save(any(User.class))).willAnswer(inv -> inv.getArgument(0));

        // When
        withdrawUserService.withdraw(userId);

        // Then
        verify(appleOAuthPort).revokeRefreshToken("apple-refresh-token-xyz");
        assertThat(user.isWithdrawn()).isTrue();
        assertThat(user.getAppleRefreshToken()).isNull();
        verify(userRepositoryPort).save(user);
    }

    @DisplayName("Apple revoke가 예외를 던져도 → 서비스가 graceful로 잡고 탈퇴는 정상 완료된다")
    @Test
    void withdraw_appleRevokeThrows_stillCompletesWithdraw() {
        // Given
        String userId = "001234.abcdef.5678";
        User user = createActiveAppleUser(userId, "apple-refresh-token-xyz");

        given(userRepositoryPort.findById(userId)).willReturn(Optional.of(user));
        given(crewMembershipPort.findAllByUserId(userId)).willReturn(List.of());
        given(userRepositoryPort.save(any(User.class))).willAnswer(inv -> inv.getArgument(0));
        willThrow(new RuntimeException("Apple API 일시 장애"))
                .given(appleOAuthPort).revokeRefreshToken("apple-refresh-token-xyz");

        // When
        withdrawUserService.withdraw(userId);

        // Then
        verify(appleOAuthPort).revokeRefreshToken("apple-refresh-token-xyz");
        assertThat(user.isWithdrawn()).isTrue();
        verify(userRepositoryPort).save(user);
    }

    @DisplayName("Apple 사용자 + refresh_token이 null → Apple revoke 미호출")
    @Test
    void withdraw_appleUserNoRefreshToken_skipsRevoke() {
        // Given
        String userId = "001234.abcdef.5678";
        User user = createActiveAppleUser(userId, null);

        given(userRepositoryPort.findById(userId)).willReturn(Optional.of(user));
        given(crewMembershipPort.findAllByUserId(userId)).willReturn(List.of());
        given(userRepositoryPort.save(any(User.class))).willAnswer(inv -> inv.getArgument(0));

        // When
        withdrawUserService.withdraw(userId);

        // Then
        verify(appleOAuthPort, never()).revokeRefreshToken(any());
        assertThat(user.isWithdrawn()).isTrue();
    }

    @DisplayName("KAKAO 사용자 → Apple revoke 미호출")
    @Test
    void withdraw_kakaoUser_skipsAppleRevoke() {
        // Given
        String userId = "kakao-1";
        User user = createActiveKakaoUser(userId);

        given(userRepositoryPort.findById(userId)).willReturn(Optional.of(user));
        given(crewMembershipPort.findAllByUserId(userId)).willReturn(List.of());
        given(userRepositoryPort.save(any(User.class))).willAnswer(inv -> inv.getArgument(0));

        // When
        withdrawUserService.withdraw(userId);

        // Then
        verify(appleOAuthPort, never()).revokeRefreshToken(any());
        assertThat(user.isWithdrawn()).isTrue();
    }
}