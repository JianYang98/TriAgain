# API 명세 (API Specification)

> **이 문서는 도메인별로 분할되었습니다.** 상세 명세는 `api-spec/` 하위 파일을 참조하세요.
> 엔드포인트를 추가·수정·삭제할 때는 아래 인덱스에서 해당 도메인 파일을 찾아 그 파일을 정본으로 수정합니다.
> 맞는 도메인이 없으면(새 컨텍스트) 임의로 파일을 만들지 말고 **먼저 사용자에게 확인** — 승인 후 새 `api-spec/{컨텍스트}.md`를 만들고 이 인덱스에 행을 추가합니다.

## 개요

인증 사용자 플로우 (사진 필수 크루인 경우)
→ 텍스트만 인증 가능한 크루라면 바로 POST /verifications

```
1. POST /upload-sessions → presignedUrl + sessionId 수신
2. GET /upload-sessions/{id}/events (SSE 구독)
3. S3에 직접 업로드 (PUT {presignedUrl})
4. Lambda → 자동 완료 감지 → SSE "COMPLETED" 수신
5. POST /verifications → 인증 완료
```

---

## 엔드포인트 인덱스

| 도메인 | 파일 | 주요 엔드포인트 |
|--------|------|-----------------|
| 인증/유저 | [`api-spec/auth-user.md`](api-spec/auth-user.md) | POST /auth/kakao · signup · apple · apple-signup · refresh · logout, GET /users/me, PATCH /users/me/nickname, 프로필 이미지 업로드·확정, DELETE /users/me |
| 크루 | [`api-spec/crew.md`](api-spec/crew.md) | GET /crews/{id}/feed · my-verifications, GET /crews/invite/{code} · {id}/preview, POST /crews/join, GET /crews/{id}, POST /crews, GET /crews, PATCH·DELETE /crews/{id}, DELETE /crews/{id}/members/me, GET /crews/search, POST /crews/{id}/join, GET /invite/{code} |
| 인증 업로드 | [`api-spec/verification.md`](api-spec/verification.md) | POST /upload-sessions, POST /verifications, DELETE /verifications/{id}, PATCH /verifications/{id}, GET /upload-sessions/{id}/events (SSE) |
| 알림 | [`api-spec/notification.md`](api-spec/notification.md) | PATCH /users/me/fcm-token, GET /notifications · unread-count, PATCH /notifications/{id}/read · read-all, DELETE /notifications |
| 습관·솔로 | [`api-spec/habit.md`](api-spec/habit.md) | POST /habits, GET /habits · archived, PATCH /habits/{id}, POST /habits/{id}/end · pause · resume · cycles · verifications, DELETE cycles/current |
| 내부 API | [`api-spec/internal.md`](api-spec/internal.md) | PUT /internal/upload-sessions/complete, POST /internal/fcm-test |

## TODO (구현 시 추가 예정)

### Moderation Context
- POST /verifications/{id}/reports — 신고

### Support Context
- POST /verifications/{id}/reactions — 반응 (이모지)
