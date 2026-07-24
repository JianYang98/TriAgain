# API 명세 — 내부 API (Internal)

> 전체 인덱스: [`../api-spec.md`](../api-spec.md) · 정본은 실제 코드다. 코드보다 이 문서를 먼저 수정한다.

---

### PUT /internal/upload-sessions/complete (Lambda 콜백 — Internal API)

S3 업로드 완료 시 Lambda가 호출하여 업로드 세션을 COMPLETED 상태로 전환하고 SSE 이벤트를 발행한다.

**요청 (Request)**
```
PUT /internal/upload-sessions/complete?imageKey={key} HTTP/1.1
X-Internal-Api-Key: {api-key}
```

**쿼리 파라미터:**
- `imageKey`: (필수) S3 오브젝트 키 (예: `upload-sessions/{userId}/{uuid}.{ext}`)

**성공 응답 (200 OK)**
```json
{
  "success": true,
  "data": null,
  "error": null
}
```

**에러 응답:**
- `404 Not Found` — 해당 imageKey의 업로드 세션이 없음
- `403 Forbidden` — API Key 누락 또는 불일치

**보안:**
- `/internal/**` 경로는 `X-Internal-Api-Key` 헤더로 인증 (InternalApiKeyFilter)
- API Key 불일치 시 403 Forbidden 반환
- prod 환경: `internal.api-key` 속성으로 설정

---

### POST /internal/fcm-test (FCM 키 스모크 테스트 — Internal API)

Firebase 서비스계정 키 로테이션·배포 직후, 단건 FCM 발송으로 키 유효성을 즉시 확인한다. (cron/이벤트 기반 발송 경로는 키 무효 시 다음 실행까지 장애를 탐지하지 못하는 공백을 메움)

**요청 (Request)**
```
POST /internal/fcm-test?fcmToken={token} HTTP/1.1
X-Internal-Api-Key: {api-key}
```

**쿼리 파라미터:**
- `fcmToken`: (필수) 테스트 발송 대상 FCM 토큰 (DB의 실제 토큰 권장)

**성공 응답 (200 OK)**
```json
{
  "success": true,
  "data": {
    "sent": true,
    "status": "SUCCESS",
    "detail": "발송 성공"
  },
  "error": null
}
```

`status` 값:
- `SUCCESS` — 발송 성공, 키 유효 (`sent: true`)
- `TOKEN_INVALID` — 토큰이 영구 무효 (UNREGISTERED/INVALID_ARGUMENT, `sent: false`). 키 자체는 정상
- `ERROR` — 발송 실패 (`sent: false`, `detail`에 사유). 키 무효 시 3회 재시도(~3초) 후 `FCM_SEND_FAILED`

**에러 응답:**
- `403 Forbidden` — API Key 누락 또는 불일치
- `404 Not Found` — `firebase.enabled=false` 환경(dev/test 및 `FIREBASE_ENABLED` 미설정 prod)에서는 엔드포인트 미존재

**보안:**
- `/internal/**` 경로는 `X-Internal-Api-Key` 헤더로 인증 (InternalApiKeyFilter)
- `firebase.enabled=true` (= FcmAdapter 활성) 환경에서만 빈 등록 — 그 외 404 (dev/test의 NoOp 어댑터로 인한 거짓 성공 차단)
- prod 환경: `internal.api-key` 속성으로 설정

---

