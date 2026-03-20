# 알림 기능 구현 TODO

> **프로젝트**: TriAgain (작심삼일 크루)  
> **보장 수준**: At Least Once (@Retryable)  
> **채널**: FCM 푸시 + 인앱 알림함

---

## 구현 순서

에이전트한테 **하나씩** 순서대로 준다. 각 TODO 완료 후 `./gradlew test` 통과 확인.

---

### TODO 1: Flyway 마이그레이션 (notifications 테이블)

```sql
CREATE TABLE notifications (
    id          VARCHAR(36)  NOT NULL PRIMARY KEY,
    user_id     VARCHAR(36)  NOT NULL,
    type        VARCHAR(50)  NOT NULL,
    title       VARCHAR(255) NOT NULL,
    content     VARCHAR(500) NOT NULL,
    is_read     BOOLEAN      NOT NULL DEFAULT FALSE,
    target_type VARCHAR(50),
    target_id   VARCHAR(36),
    created_at  TIMESTAMP    NOT NULL
);

CREATE INDEX idx_notification_user_created 
    ON notifications (user_id, created_at DESC);

CREATE INDEX idx_notification_created 
    ON notifications (created_at);
```

**완료 기준:** 마이그레이션 실행 성공, `./gradlew bootRun`으로 테이블 생성 확인

---

### TODO 2: Notification 도메인 모델 + JPA 엔티티 + Repository

**신규 파일:**
- `support/domain/model/Notification.java` — 도메인 모델
- `support/domain/vo/NotificationType.java` — enum (REMINDER, CREW_STARTED)
- `support/domain/vo/TargetType.java` — enum (CREW, VERIFICATION)
- `support/infra/NotificationJpaEntity.java` — JPA 엔티티
- `support/infra/NotificationJpaRepository.java` — Spring Data JPA
- `support/infra/NotificationJpaAdapter.java` — 어댑터
- `support/port/out/NotificationRepositoryPort.java` — 아웃바운드 포트

**주요 메서드:**
- `save(Notification)` — 알림 저장
- `findByUserId(userId, pageable)` — 유저별 알림 목록 (최신순)
- `markAsRead(notificationId)` — 읽음 처리
- `deleteOlderThan(date)` — 30일 지난 알림 삭제

**완료 기준:** 도메인 모델 + JPA 어댑터 생성, 컴파일 통과

---

### TODO 3: FCM 연동 (Firebase Admin SDK + NotificationSendPort)

**신규 파일:**
- `support/port/out/NotificationSendPort.java` — 아웃바운드 포트 (인터페이스)
- `support/infra/FcmAdapter.java` — FCM 구현체

**설정:**
- `build.gradle`에 Firebase Admin SDK 의존성 추가
- `application.yml`에 Firebase 서비스 계정 경로 설정
- Firebase 서비스 계정 JSON 파일 프로젝트에 추가 (gitignore!)

**NotificationSendPort 인터페이스:**
```java
public interface NotificationSendPort {
    void send(String fcmToken, String title, String body, Map<String, String> data);
}
```

**완료 기준:** FCM 연동 코드 작성, 컴파일 통과 (실제 발송 테스트는 TODO 6에서)

---

### TODO 4: @Retryable 설정

**수정 파일:**
- `build.gradle`에 `spring-retry` + `spring-boot-starter-aop` 의존성 추가
- 메인 Application 클래스에 `@EnableRetry` 추가
- `FcmAdapter.send()`에 `@Retryable` 적용

**@Retryable 설정:**
```java
@Retryable(
    retryFor = {FirebaseMessagingException.class},
    maxAttempts = 3,
    backoff = @Backoff(delay = 1000, multiplier = 2)
)
```

**완료 기준:** @Retryable 설정 완료, 컴파일 통과

---

### TODO 5: 스케줄러 (인증 리마인더 + 크루 시작 알림)

**신규 파일:**
- `support/api/ReminderScheduler.java` — 인증 리마인더 (15분마다)
- `support/api/CrewStartNotificationScheduler.java` — 크루 시작 알림 (아침 9시)
- `support/port/in/SendReminderUseCase.java` — 유스케이스 인터페이스
- `support/port/in/SendCrewStartNotificationUseCase.java` — 유스케이스 인터페이스
- `support/application/SendReminderService.java` — 리마인더 서비스
- `support/application/SendCrewStartNotificationService.java` — 크루 시작 알림 서비스

**스케줄러는 api/ 패키지에!** (서비스에 @Scheduled 넣지 않는다)

**인증 리마인더 로직:**
```
매 15분마다 (00, 15, 30, 45분)
→ 마감이 now+15분 ~ now+30분인 ACTIVE 크루 조회
→ 해당 크루의 미인증자 조회
→ 각 미인증자에게: Notification 저장 + FCM 발송
```

**크루 시작 알림 로직:**
```
매일 아침 9시
→ startDate = today인 ACTIVE 크루 조회
→ 해당 크루 전체 멤버에게: Notification 저장 + FCM 발송
```

**이벤트 기반 분리:**
- 스케줄러 → 이벤트 발행 (ReminderEvent / CrewStartedEvent)
- @EventListener → NotificationSendPort로 FCM 발송

**완료 기준:** 스케줄러 + 서비스 구현, `./gradlew test` 통과

---

### TODO 6: 인앱 알림 API

**신규 파일:**
- `support/port/in/GetNotificationsUseCase.java` — 알림 목록 조회
- `support/port/in/ReadNotificationUseCase.java` — 알림 읽음 처리
- `support/application/GetNotificationsService.java`
- `support/application/ReadNotificationService.java`
- `support/api/NotificationController.java`

**API 엔드포인트:**
```
GET  /notifications          — 내 알림 목록 (페이지네이션, 최신순)
PATCH /notifications/{id}/read — 알림 읽음 처리
GET  /notifications/unread-count — 안 읽은 알림 수 (뱃지용)
```

**완료 기준:** API 구현 + 단위테스트 통과

---

### TODO 7: 30일 삭제 스케줄러

**신규 파일:**
- `support/api/NotificationCleanupScheduler.java`

**로직:**
```
매일 새벽 3시
→ DELETE FROM notifications WHERE created_at < now() - 30일
```

**완료 기준:** 스케줄러 구현, `./gradlew test` 통과

---

### TODO 8: 문서 업데이트

- `CLAUDE.md` — API 엔드포인트 테이블에 알림 API 추가
- `docs/spec/api-spec.md` — 알림 API 스펙 추가
- `docs/spec/schema.md` — notifications 테이블 추가
- `docs/log/debugging-log.md` — 알림 설계 판단 기록

---

## FCM 토큰 관리 (프론트 연동 필요)

프론트에서 해야 할 것:
- 앱 시작 시 FCM 토큰 발급
- 토큰을 백엔드에 전달: `POST /users/me/fcm-token`
- 토큰 갱신 시 재전달

백엔드에서 해야 할 것:
- User 테이블에 `fcm_token` 컬럼 추가 (또는 별도 테이블)
- FCM 토큰 저장/갱신 API

→ 이건 TODO 3 이후에 프론트와 협의하여 진행

---

## 체크리스트

- [ ] TODO 1: Flyway 마이그레이션
- [ ] TODO 2: 도메인 모델 + Repository
- [ ] TODO 3: FCM 연동
- [ ] TODO 4: @Retryable 설정
- [ ] TODO 5: 스케줄러 (리마인더 + 크루 시작)
- [ ] TODO 6: 인앱 알림 API
- [ ] TODO 7: 30일 삭제 스케줄러
- [ ] TODO 8: 문서 업데이트
