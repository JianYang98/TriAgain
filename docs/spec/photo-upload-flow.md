# 사진 인증 업로드 플로우

## 정상 플로우

```mermaid
sequenceDiagram
    actor User
    participant App as Flutter App
    participant BE as Backend (Spring)
    participant S3 as AWS S3
    participant Lambda as AWS Lambda

    User->>App: 인증하기 버튼 탭

    App->>BE: POST /upload-sessions
    Note right of App: { fileName, fileSize,<br/>contentType, crewId }
    BE-->>App: 200 { uploadSessionId, presignedUrl, imageUrl }
    Note left of BE: upload_session 생성<br/>상태: PENDING<br/>requested_at 서버 기록

    App->>BE: GET /upload-sessions/{id}/events
    Note right of App: SSE 구독 시작<br/>(presignedUrl 수신 직후)
    BE-->>App: SSE 연결 수립 (timeout: 60초)

    App->>S3: PUT {presignedUrl} (이미지 바이너리)
    Note right of App: S3 직접 업로드<br/>백엔드 경유 없음

    S3-->>Lambda: S3 이벤트 트리거 (업로드 감지)

    Lambda->>BE: PATCH /upload-sessions/{id}
    Note right of Lambda: 상태: COMPLETED
    BE-->>Lambda: 200 OK
    Note left of BE: upload_session → COMPLETED<br/>SSE 이벤트 발행

    BE-->>App: SSE event: "COMPLETED"
    App->>BE: POST /verifications
    Note right of App: { crewId, uploadSessionId,<br/>content }
    BE-->>App: 201 { verificationId, imageUrl, ... }
    Note left of BE: upload_session COMPLETED 확인<br/>중복 방지: UNIQUE(upload_session_id)

    App-->>User: 인증 완료 화면
```

---

## SSE 타임아웃 → 폴링 fallback

```mermaid
sequenceDiagram
    participant App as Flutter App
    participant BE as Backend
    participant S3 as AWS S3

    App->>BE: GET /upload-sessions/{id}/events
    Note right of App: SSE 구독
    App->>S3: PUT {presignedUrl}

    Note over App,BE: 60초 경과 — SSE 타임아웃

    BE-->>App: SSE 연결 종료

    loop 폴링 (3초 간격, 최대 10회)
        App->>BE: GET /upload-sessions/{id}
        BE-->>App: { status: "PENDING" }
    end

    Note over App,BE: Lambda 처리 완료

    App->>BE: GET /upload-sessions/{id}
    BE-->>App: { status: "COMPLETED" }

    App->>BE: POST /verifications
    BE-->>App: 201 Created
```

---

## S3 장애 시나리오

```mermaid
sequenceDiagram
    participant App as Flutter App
    participant BE as Backend
    participant S3 as AWS S3

    App->>BE: POST /upload-sessions
    BE-->>App: { presignedUrl, uploadSessionId }

    App->>S3: PUT {presignedUrl}
    S3-->>App: ❌ 업로드 실패

    Note over App: 재시도 (최대 3회)
    App->>S3: PUT {presignedUrl}
    S3-->>App: ❌ 실패 반복

    App-->>App: 사용자에게 재시도 안내

    Note over App,BE: 유예 시간 내 재시도
    App->>BE: POST /upload-sessions (새 세션)
    BE-->>App: 새 presignedUrl
    App->>S3: PUT {presignedUrl}
    S3-->>App: ✅ 성공
```

---

## 에러 케이스 요약

| 상황 | 에러 코드 | 처리 |
|------|-----------|------|
| upload_session이 COMPLETED 아님 | `UPLOAD_SESSION_NOT_COMPLETED` | S3 업로드 완료 대기 후 재시도 |
| upload_session 만료 | `UPLOAD_SESSION_EXPIRED` | 새 세션 발급 후 재업로드 |
| TEXT 크루에서 upload-session 생성 | `400 Bad Request` | 클라이언트 로직 오류 |
| 마감 시간 초과 | `VERIFICATION_DEADLINE_EXCEEDED` | `requested_at` 기준이므로 업로드 지연은 무관 |

---

## 핵심 설계 결정

- **인증 시간 기준**: `upload_session.requested_at` (서버 기록) — 클라이언트 조작 불가
- **중복 인증 방지**: DB `UNIQUE(upload_session_id)` — 같은 세션으로 두 번 인증 불가
- **S3 직접 업로드**: presigned URL로 클라이언트 → S3 직접 전송, 백엔드 트래픽 없음
- **SSE vs 폴링**: SSE 우선, 타임아웃(60초) 시 폴링 fallback
