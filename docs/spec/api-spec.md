# API 명세 (API Specification)

> **이 문서는 도메인별로 분할되었습니다.** 상세 명세는 `api-spec/` 하위 파일을 참조하세요.
> 엔드포인트를 추가·수정·삭제할 때는 아래 인덱스에서 해당 도메인 파일을 찾아 그 파일을 정본으로 수정합니다.
> 맞는 도메인이 없으면(새 컨텍스트) 임의로 파일을 만들지 말고 **먼저 사용자에게 확인** — 승인 후 새 `api-spec/{컨텍스트}.md`를 만들고 이 인덱스에 행을 추가합니다.

## 개요

인증 사용자 플로우 (사진 필수 크루인 경우)
→ 텍스트만 인증 가능한 크루라면 바로 POST /verifications

```
1. POST /upload-sessions → presignedUrl + uploadSessionId 수신
2. GET /upload-sessions/{id}/events (SSE) 구독 시작          ← S3 PUT 전
3. S3에 직접 업로드 (PUT {presignedUrl})
4. 업로드 완료 후 GET /upload-sessions/{id} 2초 폴링 시작     ← S3 PUT 후
5. SSE·폴링 중 먼저 확정된 결과를 채택하고 나머지 대기 종료
   ├─ "COMPLETED" → 6으로 진행
   └─ "EXPIRED"   → 인증 생성 불가. 1부터 새 세션 발급 또는 실패 종료
6. POST /verifications → 인증 완료  ("COMPLETED"인 경우에만)
```

> **순서 주의** — SSE 구독은 반드시 **S3 PUT 전**에 시작한다. 서버는 미구독 세션의 완료 이벤트를
> 보관하지 않고 버리므로, 구독 전에 Lambda 콜백이 도착하면 이벤트가 영구 유실된다.
> 폴링은 업로드 전에는 완료될 수 없으므로 **S3 PUT 후**에 시작한다.

---

## 엔드포인트 인덱스

| 도메인 | 파일 | 주요 엔드포인트 |
|--------|------|-----------------|
| 인증/유저 | [`api-spec/auth-user.md`](api-spec/auth-user.md) | POST /auth/kakao · signup · apple · apple-signup · refresh · logout, GET /users/me, PATCH /users/me/nickname · fcm-token, 프로필 이미지 업로드·확정, DELETE /users/me |
| 크루 | [`api-spec/crew.md`](api-spec/crew.md) | GET /crews/{id}/feed · my-verifications, GET /crews/invite/{code} · {id}/preview, POST /crews/join, GET /crews/{id}, POST /crews, GET /crews, PATCH·DELETE /crews/{id}, DELETE /crews/{id}/members/me, GET /crews/search, POST /crews/{id}/join, GET /invite/{code} |
| 인증 업로드 | [`api-spec/verification.md`](api-spec/verification.md) | POST /upload-sessions, GET /upload-sessions/{id} · {id}/events (SSE), POST /verifications, DELETE /verifications/{id}, PATCH /verifications/{id}, PUT·DELETE /verifications/{id}/reactions (Support Context) |
| 알림 | [`api-spec/notification.md`](api-spec/notification.md) | GET /notifications · unread-count, PATCH /notifications/{id}/read · read-all, DELETE /notifications |
| 습관·솔로 | [`api-spec/habit.md`](api-spec/habit.md) | POST /habits, GET /habits · archived, PATCH /habits/{id}, POST /habits/{id}/end · pause · resume · cycles · verifications, DELETE cycles/current |
| 내부 API | [`api-spec/internal.md`](api-spec/internal.md) | PUT /internal/upload-sessions/complete, POST /internal/fcm-test |

## TODO (구현 시 추가 예정)

### Moderation Context
- POST /verifications/{id}/reports — 신고
