package com.triagain.support.api;

import com.triagain.common.auth.AuthenticatedUser;
import com.triagain.common.response.ApiResponse;
import com.triagain.support.port.in.DeleteAllNotificationsUseCase;
import com.triagain.support.port.in.GetNotificationsUseCase;
import com.triagain.support.port.in.GetNotificationsUseCase.NotificationListResult;
import com.triagain.support.port.in.ReadAllNotificationsUseCase;
import com.triagain.support.port.in.ReadNotificationUseCase;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final GetNotificationsUseCase getNotificationsUseCase;
    private final ReadNotificationUseCase readNotificationUseCase;
    private final DeleteAllNotificationsUseCase deleteAllNotificationsUseCase;
    private final ReadAllNotificationsUseCase readAllNotificationsUseCase;

    /** 내 알림 목록 조회 — GET /notifications */
    @GetMapping
    public ResponseEntity<ApiResponse<NotificationListResult>> getNotifications(
            @AuthenticatedUser String userId,
            @RequestParam(required = false) Boolean isRead,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(
                getNotificationsUseCase.getNotifications(userId, isRead, page, size)));
    }

    /** 안 읽은 알림 수 조회 — GET /notifications/unread-count */
    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<UnreadCountResponse>> getUnreadCount(
            @AuthenticatedUser String userId) {
        long count = getNotificationsUseCase.getUnreadCount(userId);
        return ResponseEntity.ok(ApiResponse.ok(new UnreadCountResponse(count)));
    }

    /** 알림 읽음 처리 — PATCH /notifications/{id}/read */
    @PatchMapping("/{id}/read")
    public ResponseEntity<ApiResponse<Void>> markAsRead(
            @PathVariable String id,
            @AuthenticatedUser String userId) {
        readNotificationUseCase.markAsRead(id, userId);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    /** 알림 전체 삭제 — DELETE /notifications */
    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> deleteAll(
            @AuthenticatedUser String userId) {
        deleteAllNotificationsUseCase.deleteAllByUserId(userId);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    /** 알림 전체 읽음 — PATCH /notifications/read-all */
    @PatchMapping("/read-all")
    public ResponseEntity<ApiResponse<Void>> readAll(
            @AuthenticatedUser String userId) {
        readAllNotificationsUseCase.markAllAsReadByUserId(userId);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    record UnreadCountResponse(long count) {}
}
