# Backend API 현황

> **Last Updated:** 2026-02-28
> **Branch:** `feat/upload-sessions-happy-path`
> **Base URL:** `http://localhost:8080`

---

## 1. 공통 응답 형식

### ApiResponse\<T\>

모든 API는 아래 형식으로 응답한다.

```json
{
  "success": true,
  "data": { ... },
  "error": null
}
```

```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "CR001",
    "message": "크루를 찾을 수 없습니다"
  }
}
```

### ErrorCode 전체 목록

| Code | HTTP | 설명 |
|------|------|------|
| **Common** | | |
| C001 | 400 | 잘못된 입력값 (Validation 실패 포함) |
| C002 | 500 | 서버 내부 오류 |
| C003 | 404 | 리소스를 찾을 수 없음 |
| **User** | | |
| U001 | 404 | 사용자를 찾을 수 없음 |
| U002 | 400 | 이메일 중복 |
| U003 | 400 | 이메일 필수 |
| U004 | 400 | 닉네임 필수 |
| **Crew** | | |
| CR001 | 404 | 크루를 찾을 수 없음 |
| CR002 | 409 | 크루 정원 초과 |
| CR003 | 400 | 모집 중이 아닌 크루 |
| CR004 | 409 | 이미 참여한 크루 |
| CR005 | 404 | 챌린지를 찾을 수 없음 |
| CR006 | 404 | 유효하지 않은 초대코드 |
| CR007 | 400 | 활성 상태가 아닌 크루 |
| CR008 | 400 | 크루 참여 기한 초과 |
| CR009 | 403 | 크루 접근 권한 없음 |
| CR010 | 400 | 잘못된 최대 인원 |
| CR011 | 400 | 잘못된 시작 날짜 |
| CR012 | 400 | 잘못된 종료 날짜 |
| CR013 | 400 | 진행 중이 아닌 챌린지 |
| **Verification** | | |
| V001 | 404 | 인증을 찾을 수 없음 |
| V002 | 400 | 인증 마감 시간 초과 |
| V003 | 409 | 이미 인증 완료 (당일 중복) |
| V004 | 404 | 업로드 세션을 찾을 수 없음 |
| V005 | 400 | 업로드 세션 미완료 |
| V006 | 400 | 업로드 세션 만료 |
| V007 | 400 | 지원하지 않는 파일 타입 |
| V008 | 400 | 파일 크기 초과 |
| V009 | 400 | 사진 인증 시 사진 필수 |
| V010 | 400 | 텍스트 인증 시 텍스트 필수 |
| V011 | 400 | 이미지 URL 필수 |
| V012 | 400 | 사용자 ID 필수 |
| V013 | 400 | 이미지 키 필수 |
| V014 | 400 | PENDING 상태가 아닌 세션 |
| **Moderation** | | |
| M001 | 404 | 신고를 찾을 수 없음 |
| M002 | 400 | 이미 신고한 인증 |
| M003 | 404 | 검토를 찾을 수 없음 |
| M004 | 400 | 이미 처리된 신고 |
| M005 | 400 | 본인 인증 신고 불가 |
| M006 | 403 | 크루 멤버가 아님 |
| M007 | 400 | 인증 ID 필수 |
| M008 | 400 | 신고자 ID 필수 |
| **Support** | | |
| S001 | 404 | 알림을 찾을 수 없음 |
| S002 | 404 | 반응을 찾을 수 없음 |
| S003 | 400 | 이모지 필수 |

---

## 2. 구현 완료 API

### 인증 헤더

대부분의 API는 `X-User-Id` 헤더가 필요하다. (Phase 1에서는 JWT 미적용, 헤더로 userId 직접 전달)

```
X-User-Id: {userId}
```

---

### 2-1. GET /health

헬스체크 엔드포인트.

- **인증:** 불필요
- **Response:** `200 OK`

```json
{
  "success": true,
  "data": {
    "status": "UP",
    "database": "UP"
  },
  "error": null
}
```

---

### 2-2. POST /crews

크루 생성.

- **인증:** `X-User-Id` 필수
- **Response:** `201 Created`

**Request Body:**

| 필드 | 타입 | 필수 | 검증 |
|------|------|------|------|
| name | String | O | @NotBlank |
| goal | String | O | @NotBlank |
| verificationType | String | O | @NotNull — `"TEXT"` \| `"PHOTO"` |
| maxMembers | int | O | @Min(1) @Max(10) |
| startDate | String (ISO) | O | @NotNull — `"2026-03-01"` 형식 |
| endDate | String (ISO) | O | @NotNull — `"2026-03-03"` 형식 |
| allowLateJoin | boolean | X | 기본값 false |

```json
{
  "name": "아침 운동 크루",
  "goal": "매일 30분 운동하기",
  "verificationType": "PHOTO",
  "maxMembers": 5,
  "startDate": "2026-03-01",
  "endDate": "2026-03-03",
  "allowLateJoin": true
}
```

**Response Body:**

| 필드 | 타입 | 설명 |
|------|------|------|
| crewId | String | 크루 ID |
| creatorId | String | 생성자 ID |
| name | String | 크루명 |
| goal | String | 목표 |
| verificationType | String | `"TEXT"` \| `"PHOTO"` |
| maxMembers | int | 최대 인원 |
| currentMembers | int | 현재 인원 (생성 시 1) |
| status | String | `"RECRUITING"` |
| startDate | String | 시작일 |
| endDate | String | 종료일 |
| allowLateJoin | boolean | 중간 참여 허용 여부 |
| inviteCode | String | 초대코드 |
| createdAt | String | ISO DateTime |

---

### 2-3. POST /crews/join

초대코드로 크루 참여.

- **인증:** `X-User-Id` 필수
- **Response:** `201 Created`

**Request Body:**

| 필드 | 타입 | 필수 | 검증 |
|------|------|------|------|
| inviteCode | String | O | @NotBlank |

```json
{
  "inviteCode": "ABC123"
}
```

**Response Body:**

| 필드 | 타입 | 설명 |
|------|------|------|
| userId | String | 참여한 사용자 ID |
| crewId | String | 크루 ID |
| role | String | `"MEMBER"` |
| currentMembers | int | 참여 후 현재 인원 |
| joinedAt | String | ISO DateTime |

**에러 케이스:**
- `CR006` — 유효하지 않은 초대코드
- `CR002` — 크루 정원 초과
- `CR003` — 모집 중이 아닌 크루
- `CR004` — 이미 참여한 크루
- `CR008` — 참여 기한 초과

---

### 2-4. GET /crews

내가 속한 크루 목록 조회.

- **인증:** `X-User-Id` 필수
- **Response:** `200 OK`

**Response Body:** `List<CrewSummaryResult>`

| 필드 | 타입 | 설명 |
|------|------|------|
| id | String | 크루 ID |
| name | String | 크루명 |
| goal | String | 목표 |
| verificationType | String | `"TEXT"` \| `"PHOTO"` |
| currentMembers | int | 현재 인원 |
| maxMembers | int | 최대 인원 |
| status | String | `"RECRUITING"` \| `"ACTIVE"` \| `"COMPLETED"` |
| startDate | String | 시작일 |
| endDate | String | 종료일 |
| createdAt | String | ISO DateTime |

---

### 2-5. GET /crews/{crewId}

크루 상세 조회.

- **인증:** `X-User-Id` 필수
- **Path:** `crewId` — String
- **Response:** `200 OK`

**Response Body:**

| 필드 | 타입 | 설명 |
|------|------|------|
| id | String | 크루 ID |
| creatorId | String | 크루장 ID |
| name | String | 크루명 |
| goal | String | 목표 |
| verificationType | String | `"TEXT"` \| `"PHOTO"` |
| maxMembers | int | 최대 인원 |
| currentMembers | int | 현재 인원 |
| status | String | `"RECRUITING"` \| `"ACTIVE"` \| `"COMPLETED"` |
| startDate | String | 시작일 |
| endDate | String | 종료일 |
| allowLateJoin | boolean | 중간 참여 허용 여부 |
| inviteCode | String | 초대코드 |
| createdAt | String | ISO DateTime |
| members | List | 멤버 목록 (아래 참조) |

**members 항목:**

| 필드 | 타입 | 설명 |
|------|------|------|
| userId | String | 사용자 ID |
| role | String | `"LEADER"` \| `"MEMBER"` |
| joinedAt | String | ISO DateTime |

**에러 케이스:**
- `CR001` — 크루를 찾을 수 없음
- `CR009` — 크루 접근 권한 없음

---

### 2-6. POST /upload-sessions

사진 인증용 업로드 세션 생성. Pre-signed URL을 반환한다.

- **인증:** `X-User-Id` 필수
- **Response:** `201 Created`

**Request Body:**

| 필드 | 타입 | 필수 | 검증 |
|------|------|------|------|
| fileName | String | O | @NotBlank |
| fileType | String | O | @NotBlank — `"image/jpeg"`, `"image/png"` 등 |
| fileSize | long | O | @Positive |

```json
{
  "fileName": "morning-run.jpg",
  "fileType": "image/jpeg",
  "fileSize": 2048000
}
```

**Response Body:**

| 필드 | 타입 | 설명 |
|------|------|------|
| uploadSessionId | Long | 세션 ID |
| presignedUrl | String | S3 업로드용 Pre-signed URL |
| imageUrl | String | 업로드 완료 후 이미지 접근 URL |
| expiresAt | String | 세션 만료 시간 (ISO DateTime) |
| maxFileSize | long | 최대 파일 크기 (bytes) |
| allowedTypes | List\<String\> | 허용 파일 타입 목록 |

```json
{
  "success": true,
  "data": {
    "uploadSessionId": 1,
    "presignedUrl": "https://s3.amazonaws.com/bucket/...",
    "imageUrl": "https://cdn.triagain.com/images/...",
    "expiresAt": "2026-02-28T12:15:00",
    "maxFileSize": 10485760,
    "allowedTypes": ["image/jpeg", "image/png", "image/webp"]
  },
  "error": null
}
```

---

### 2-7. GET /upload-sessions/{id}/events

업로드 완료 알림을 위한 SSE 구독.

- **인증:** 불필요
- **Path:** `id` — Long (uploadSessionId)
- **Content-Type:** `text/event-stream`
- **Timeout:** 60초

**이벤트 형식:**

```
event: upload-complete
data: COMPLETED
```

- 클라이언트는 SSE 연결 후 `upload-complete` 이벤트를 수신하면 연결이 자동으로 닫힌다.
- 60초 안에 이벤트가 오지 않으면 타임아웃. 클라이언트는 폴링으로 fallback 해야 한다.

---

### 2-8. PUT /internal/upload-sessions/{id}/complete

Lambda가 S3 업로드 완료 시 호출하는 내부 API.

- **인증:** 불필요 (VPC 내부 전용, Phase 1에서는 Spring Security 미설정)
- **Path:** `id` — Long (uploadSessionId)
- **Response:** `200 OK`

```json
{
  "success": true,
  "data": null,
  "error": null
}
```

- 세션 상태를 `COMPLETED`로 변경하고, SSE `upload-complete` 이벤트를 발행한다.
- 이미 `COMPLETED`인 경우 no-op (멱등성 보장).

---

### 2-9. POST /verifications

인증 제출 (텍스트 또는 사진).

- **인증:** `X-User-Id` 필수
- **Response:** `201 Created`

**Request Body:**

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| challengeId | String | O | @NotBlank — 인증 대상 챌린지 |
| uploadSessionId | Long | 조건부 | 사진 인증 시 필수. COMPLETED 상태여야 함 |
| textContent | String | 조건부 | 텍스트 인증 시 필수 |

**사진 인증 예시:**
```json
{
  "challengeId": "ch-001",
  "uploadSessionId": 1,
  "textContent": "오늘도 운동 완료!"
}
```

**텍스트 인증 예시:**
```json
{
  "challengeId": "ch-001",
  "textContent": "오늘 30분 독서했습니다"
}
```

**Response Body:**

| 필드 | 타입 | 설명 |
|------|------|------|
| verificationId | String | 인증 ID |
| challengeId | String | 챌린지 ID |
| userId | String | 작성자 ID |
| crewId | String | 크루 ID |
| imageUrl | String \| null | 사진 인증 시 이미지 URL |
| textContent | String \| null | 텍스트 내용 |
| status | String | `"APPROVED"` (기본값) |
| reviewStatus | String | `"NOT_REQUIRED"` (기본값) |
| reportCount | int | 신고 수 (기본 0) |
| targetDate | String | 인증 대상 날짜 (`"2026-02-28"`) |
| createdAt | String | ISO DateTime |

**에러 케이스:**
- `V005` — 업로드 세션이 COMPLETED가 아님
- `V003` — 당일 이미 인증 완료
- `V009` — 사진 인증인데 uploadSessionId 없음
- `V010` — 텍스트 인증인데 textContent 없음
- `CR005` — 챌린지를 찾을 수 없음
- `CR013` — 진행 중이 아닌 챌린지

---

### 2-10. GET /crews/{crewId}/feed

크루 피드 (인증 목록 + 내 진행률) 조회.

- **인증:** `X-User-Id` 필수
- **Path:** `crewId` — String
- **Query Params:**

| 파라미터 | 타입 | 기본값 | 설명 |
|---------|------|--------|------|
| page | int | 0 | 페이지 번호 (음수 → 0으로 보정) |
| size | int | 20 | 페이지 크기 (0 이하 또는 50 초과 → 20으로 보정) |

- **Response:** `200 OK`

**Response Body:**

| 필드 | 타입 | 설명 |
|------|------|------|
| verifications | List | 인증 목록 (아래 참조) |
| myProgress | Object | 내 챌린지 진행률 |
| hasNext | boolean | 다음 페이지 존재 여부 |

**verifications 항목:**

| 필드 | 타입 | 설명 |
|------|------|------|
| id | String | 인증 ID |
| userId | String | 작성자 ID |
| nickname | String | 작성자 닉네임 |
| profileImageUrl | String \| null | 프로필 이미지 URL |
| imageUrl | String \| null | 인증 이미지 URL |
| textContent | String \| null | 텍스트 내용 |
| targetDate | String | 인증 대상 날짜 |
| createdAt | String | ISO DateTime |

**myProgress:**

| 필드 | 타입 | 설명 |
|------|------|------|
| challengeId | String | 챌린지 ID |
| status | String | `"IN_PROGRESS"` \| `"SUCCESS"` \| `"FAILED"` \| `"ENDED"` |
| completedDays | int | 인증 완료 일수 |
| targetDays | int | 목표 일수 |

---

## 3. 사진 인증 플로우

```
Client                  Backend              S3                Lambda
  │                       │                   │                   │
  │ 1. POST               │                   │                   │
  │    /upload-sessions    │                   │                   │
  │ ─────────────────────► │                   │                   │
  │                        │ presignedUrl 생성  │                   │
  │ ◄───────────────────── │                   │                   │
  │  {uploadSessionId,     │                   │                   │
  │   presignedUrl,        │                   │                   │
  │   imageUrl}            │                   │                   │
  │                        │                   │                   │
  │ 2. GET                 │                   │                   │
  │    /upload-sessions    │                   │                   │
  │    /{id}/events (SSE)  │                   │                   │
  │ ─────────────────────► │                   │                   │
  │  ◄ ─ ─ SSE 연결 ─ ─ ─ │                   │                   │
  │                        │                   │                   │
  │ 3. PUT presignedUrl    │                   │                   │
  │ ──────────────────────────────────────────►│                   │
  │                        │                   │                   │
  │                        │                   │ 4. S3 Event       │
  │                        │                   │ ─────────────────►│
  │                        │                   │                   │
  │                        │ 5. PUT /internal/ │                   │
  │                        │    upload-sessions│                   │
  │                        │    /{id}/complete │                   │
  │                        │ ◄─────────────────────────────────────│
  │                        │                   │                   │
  │  6. SSE event:         │                   │                   │
  │     upload-complete    │                   │                   │
  │ ◄ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─│                   │                   │
  │                        │                   │                   │
  │ 7. POST                │                   │                   │
  │    /verifications      │                   │                   │
  │    {challengeId,       │                   │                   │
  │     uploadSessionId}   │                   │                   │
  │ ─────────────────────► │                   │                   │
  │                        │ 인증 생성          │                   │
  │ ◄───────────────────── │                   │                   │
  │  VerificationResult    │                   │                   │
```

### 텍스트 인증 (간소화)

업로드 세션 없이 바로 인증 제출:

```
Client                  Backend
  │                       │
  │ POST /verifications   │
  │ {challengeId,         │
  │  textContent}         │
  │ ─────────────────────►│
  │                       │ 인증 생성
  │ ◄─────────────────────│
  │  VerificationResult   │
```

### SSE Fallback

- SSE 타임아웃: **60초**
- 타임아웃 시 클라이언트는 `POST /verifications`를 시도하여 세션 상태를 확인한다.
  - `V005` 에러 → 아직 업로드 미완료 → 재시도
  - 성공 → 업로드 완료된 상태

---

## 4. Phase 1 TODO

- [ ] User Context 구현 (카카오 OAuth 로그인, 프로필 CRUD)
- [ ] Spring Security 설정 (JWT 토큰 인증)
- [ ] `/internal/**` 경로 VPC 내부 접근 제한
- [ ] 크루 탈퇴 API (`DELETE /crews/{crewId}/members/me`)
- [ ] 챌린지 자동 생성 스케줄러 (크루 활성화 시 3일 단위 사이클)
- [ ] GlobalExceptionHandler 에러 응답 통일 (현재 기본 구현 완료, 세부 조정 필요)
- [ ] 배포 파이프라인 (GitHub Actions → EC2 + RDS)

---

## 5. Phase 2/3 TODO

### Phase 2

- [ ] Redis 캐시 (ElastiCache) — 피드 조회, 크루 정보 캐싱
- [ ] AWS SQS — 비동기 이벤트 처리
- [ ] 신고/검토 기능 (Moderation Context)
- [ ] 리액션(이모지) 기능 (Support Context)
- [ ] FCM 푸시 알림 (Support Context)
- [ ] JPA vs MyBatis 성능 비교

### Phase 3

- [ ] AI 기반 인증 검토 (OpenAI)
- [ ] 크루 검색/추천
- [ ] 통계/리더보드
