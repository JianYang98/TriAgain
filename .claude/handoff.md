# Handoff: 알림 인프라 + 인앱 알림 API 구현

> 브랜치: `feat/scheduler-fault-isolation`

---

## 이번 세션 완료 작업

### TODO 6: 인앱 알림 API (100% 완료)

기존 알림 인프라(도메인 모델, JPA 어댑터, 리포지토리 포트) 위에 UseCase + Service + Controller 3계층을 추가했다.

**신규 파일 5개:**

| 파일 | 역할 |
|------|------|
| `support/port/in/GetNotificationsUseCase.java` | 알림 조회 + 안 읽은 수 UseCase |
| `support/port/in/ReadNotificationUseCase.java` | 알림 읽음 처리 UseCase |
| `support/application/GetNotificationsService.java` | 조회 서비스 (Slice 페이지네이션) |
| `support/application/ReadNotificationService.java` | 읽음 처리 서비스 (소유권 검증) |
| `support/api/NotificationController.java` | REST 컨트롤러 (3개 엔드포인트) |

**API 엔드포인트:**

| Method | Path | 설명 |
|--------|------|------|
| GET | `/notifications` | 내 알림 목록 (page/size) |
| GET | `/notifications/unread-count` | 안 읽은 알림 수 |
| PATCH | `/notifications/{id}/read` | 알림 읽음 처리 |

**설계 결정:**
- `unread-count`를 별도 UseCase로 분리하지 않고 `GetNotificationsUseCase`에 포함 (오버엔지니어링 방지)
- 읽음 처리 시 소유권 불일치 → `NOTIFICATION_NOT_FOUND` 반환 (정보 노출 방지)
- enum을 String으로 변환하여 클라이언트 호환성 확보

### 이전 TODO (1~5) — 이번 브랜치에 이미 포함

| 파일 | 역할 |
|------|------|
| `support/application/ReminderScheduler.java` | 리마인더 스케줄러 (TransactionTemplate 장애 격리) |
| `support/application/CrewStartNotificationScheduler.java` | 크루 시작 알림 스케줄러 |
| `support/domain/vo/NotificationMessageTemplate.java` | 알림 메시지 템플릿 |
| `support/infra/NotificationTargetQueryAdapter.java` | 스케줄러용 조회 어댑터 |
| `support/port/out/NotificationTargetQueryPort.java` | 스케줄러용 조회 포트 |

---

## 미커밋 변경 파일

| 파일 | 상태 |
|------|------|
| `CLAUDE.md` | Modified |
| `TriAgainApplication.java` | Modified |
| `.claude/skills/` | New (디렉토리) |
| `support/api/NotificationController.java` | New |
| `support/application/*.java` | New (4개: Get/Read Service, 2 Schedulers) |
| `support/domain/vo/NotificationMessageTemplate.java` | New |
| `support/infra/NotificationTargetQueryAdapter.java` | New |
| `support/port/in/*.java` | New (2개: Get/Read UseCase) |
| `support/port/out/NotificationTargetQueryPort.java` | New |

**빌드 상태:** `compileJava` ✅ / `test` ✅

---

## 다음 단계

1. **SecurityConfig 확인** — `/notifications/**` 엔드포인트가 인증 필요한지 확인하고 필요시 설정 추가
2. **api-spec.md 업데이트** — 알림 API 3개 명세를 정본 문서에 추가
3. **커밋 & PR** — 이 브랜치의 모든 변경사항 커밋

---

## 기존 미해결 항목

### 크루 최소 기간 미검증 버그

- **파일**: `crew/domain/model/Crew.java:171-178`
- **현재**: `endDate > startDate`만 체크
- **정본(biz-logic.md)**: "최소 시작일+6일 (작심삼일 2회 보장)"
- **수정**: `endDate >= startDate + 6` 검증 추가