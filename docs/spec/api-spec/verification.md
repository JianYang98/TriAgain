# API 명세 — 인증 업로드 (Verification)

> 전체 인덱스: [`../api-spec.md`](../api-spec.md) · 이 문서가 API 계약 정본이다. 코드보다 이 문서를 먼저 수정한다.

---

### POST /upload-sessions (이미지 업로드 세션 생성)

클라이언트가 S3에 직접 업로드할 수 있도록 Presigned URL을 발급받는 API

**`crewId` / `habitId` 중 정확히 하나 필수 (XOR)** — 크루 인증용 세션은 `crewId`로, 솔로(습관) 인증용 세션은 `habitId`로 발급한다. 세션은 발급 컨텍스트에 구속되며, 인증 생성 시 해당 컨텍스트(`crewId` 또는 `habitId`)와 대조한다 (크루는 기존 `UPLOAD_SESSION_CREW_MISMATCH`, 솔로는 `HABIT_UPLOAD_SESSION_MISMATCH`). 하위 호환: 기존 크루 호출부는 계속 `crewId`만 전송하며 거동 불변.

**요청 (Request) — 크루 인증용**
```json
POST /upload-sessions HTTP/1.1
Authorization: Bearer <token>
Content-Type: application/json

{
  "crewId": "crew_123",
  "fileName": "verification_image.jpg",
  "fileType": "image/jpeg",
  "fileSize": 2048576
}
```

**요청 (Request) — 솔로(습관) 인증용**
```json
POST /upload-sessions HTTP/1.1
Authorization: Bearer <token>
Content-Type: application/json

{
  "habitId": "HBIT-a1b2c3d4e5f60708",
  "fileName": "verification_image.jpg",
  "fileType": "image/jpeg",
  "fileSize": 2048576
}
```

**성공 응답 (201 Created)**
```json
{
  "success": true,
  "data": {
    "uploadSessionId": 123,
    "presignedUrl": "https://s3.amazonaws.com/bucket/verifications/user_456/2026-02-18/abc123.jpg?X-Amz-Algorithm=...",
    "imageUrl": "https://s3.amazonaws.com/bucket/verifications/user_456/2026-02-18/abc123.jpg",
    "expiresAt": "2026-02-18T15:00:00Z",
    "maxFileSize": 5242880,
    "allowedTypes": ["image/jpeg"]
  },
  "error": null
}
```

**필드 설명:**
- `uploadSessionId`: 업로드 세션 ID (추적용)
- `presignedUrl`: S3에 직접 업로드할 URL (15분 유효)
- `imageUrl`: 업로드 완료 후 사용할 이미지 URL
- `expiresAt`: Presigned URL 만료 시간
- `maxFileSize`: 최대 파일 크기 (5MB)
- `allowedTypes`: 허용된 파일 타입

**실패 응답**
```json
// 400 Bad Request - 파일 타입 불허
{
  "success": false,
  "data": null,
  "error": {
    "code": "V007",
    "message": "지원하지 않는 파일 형식입니다."
  }
}

// 400 Bad Request - 파일 크기 초과
{
  "success": false,
  "data": null,
  "error": {
    "code": "V008",
    "message": "파일 크기가 너무 큽니다."
  }
}

// 401 Unauthorized
{
  "success": false,
  "data": null,
  "error": {
    "code": "A003",
    "message": "로그인이 필요합니다."
  }
}

// 400 Bad Request - TEXT 크루에서 upload session 생성 시도
{
  "success": false,
  "data": null,
  "error": {
    "code": "V017",
    "message": "텍스트 인증 크루에서는 업로드 세션이 필요하지 않습니다."
  }
}

// 400 Bad Request - 크루가 ACTIVE 상태가 아님
{
  "success": false,
  "data": null,
  "error": {
    "code": "CR007",
    "message": "활성 상태의 크루가 아닙니다."
  }
}

// 400 Bad Request - 크루 시작 전
{
  "success": false,
  "data": null,
  "error": {
    "code": "V018",
    "message": "크루가 아직 시작되지 않았습니다."
  }
}

// 400 Bad Request - 크루 기간 종료
{
  "success": false,
  "data": null,
  "error": {
    "code": "CR015",
    "message": "크루 기간이 종료되었습니다."
  }
}

// 400 Bad Request - 인증 마감 시간 초과
// 발생 조건: min(슬롯 일일마감(crew.deadlineTime, 기본 23:59:59), 사이클 마감(challenge.deadline)) + 5분 초과
{
  "success": false,
  "data": null,
  "error": {
    "code": "V002",
    "message": "인증 마감 시간이 지났습니다."
  }
}

// 403 Forbidden - 크루 멤버 아님
{
  "success": false,
  "data": null,
  "error": {
    "code": "CR009",
    "message": "크루 멤버만 조회할 수 있습니다."
  }
}

// 404 Not Found - 습관을 찾을 수 없음 (솔로 세션)
{
  "success": false,
  "data": null,
  "error": {
    "code": "HB001",
    "message": "습관을 찾을 수 없습니다."
  }
}

// 403 Forbidden - 본인 습관이 아님 (솔로 세션)
{
  "success": false,
  "data": null,
  "error": {
    "code": "HB005",
    "message": "본인 습관만 이용할 수 있습니다."
  }
}

// 400 Bad Request - 멈춘 습관 (솔로 세션)
{
  "success": false,
  "data": null,
  "error": {
    "code": "HB008",
    "message": "멈춘 습관입니다. 재개 후 이용할 수 있습니다."
  }
}

// 400 Bad Request - crewId/habitId 둘 다 없거나 둘 다 존재 (XOR 위반)
{
  "success": false,
  "data": null,
  "error": {
    "code": "C001",
    "message": "잘못된 입력값입니다."
  }
}

```

**제약 사항:**
- 최대 크기: 5MB
- 허용 타입: JPEG, PNG, WebP
- 파일명: UUID 기반 자동 생성
- Presigned URL 유효기간: 15분
- 미사용 이미지: 업로드 후 7일 경과 시 자동 삭제

---

### POST /verifications (인증 생성)

**요청 (Request)**
```json
POST /verifications HTTP/1.1
Authorization: Bearer <token>
Content-Type: application/json

{
  "crewId": "crew_123",
  "challengeId": "chal_123",
  "uploadSessionId": 123,
  "textContent": "오늘도 달리기 완료!"
}
```

**필드 설명:**
- `challengeId`: (조건부) 챌린지 ID — `crewId`와 둘 중 하나 이상 필수
- `crewId`: (조건부) 크루 ID — `challengeId`와 둘 중 하나 이상 필수
- `uploadSessionId`: (선택) 업로드 세션 ID — 사진 인증 크루에서만 필요
- `textContent`: (선택) 인증 텍스트

**challengeId / crewId 조합 규칙:**
| challengeId | crewId | 동작 |
|:-----------:|:------:|------|
| O | O | 챌린지 조회 후 crewId 일치 검증 (불일치 시 CHALLENGE_CREW_MISMATCH) |
| O | X | challengeId로 챌린지 조회, crewId는 챌린지에서 추출 |
| X | O | crewId로 활성 챌린지 조회 또는 자동 생성 |
| X | X | 400 Bad Request |

```json
// challengeId 생략 예시 (새 챌린지 자동 생성)
{
  "crewId": "crew_123",
  "textContent": "오늘도 달리기 완료!"
}
```

**성공 응답 (201 Created)**
```json
{
  "success": true,
  "data": {
    "verificationId": "ver_789",
    "challengeId": "chal_123",
    "userId": "user_456",
    "crewId": "crew_123",
    "imageUrl": "https://s3.../image.jpg",
    "textContent": "오늘도 달리기 완료!",
    "status": "APPROVED",
    "reviewStatus": "NOT_REQUIRED",
    "reportCount": 0,
    "targetDate": "2026-02-18",
    "slotAttempt": 1,
    "createdAt": "2026-02-18T14:30:00Z"
  },
  "error": null
}
```

**필드 설명 (추가):**
- `slotAttempt`: 해당 슬롯(같은 유저·크루·날짜)의 제출 회차. 최초 인증은 1, 취소 후 재인증·수정(치환)마다 1씩 증가한다. `triagain.verification.slot-attempt-limit`(기본 3) 도달 시 더 이상 수정·취소 불가(V021)

**실패 응답**
```json
// 400 Bad Request - 잘못된 입력값
{
  "success": false,
  "data": null,
  "error": {
    "code": "C001",
    "message": "잘못된 입력값입니다."
  }
}

// 400 Bad Request - 사진 인증 필수
{
  "success": false,
  "data": null,
  "error": {
    "code": "V009",
    "message": "사진 인증이 필요합니다."
  }
}

// 400 Bad Request - 업로드 세션 미완료
{
  "success": false,
  "data": null,
  "error": {
    "code": "V005",
    "message": "업로드 세션이 완료되지 않았습니다."
  }
}

// 400 Bad Request - 업로드 세션 만료
{
  "success": false,
  "data": null,
  "error": {
    "code": "V006",
    "message": "업로드 세션이 만료되었습니다."
  }
}

// 400 Bad Request - 인증 마감 시간 초과
// 발생 조건: min(슬롯 일일마감(crew.deadlineTime, 기본 23:59:59), 사이클 마감(challenge.deadline)) + 5분 초과
{
  "success": false,
  "data": null,
  "error": {
    "code": "V002",
    "message": "인증 마감 시간이 지났습니다."
  }
}

// 401 Unauthorized
{
  "success": false,
  "data": null,
  "error": {
    "code": "A003",
    "message": "로그인이 필요합니다."
  }
}

// 403 Forbidden - 크루 멤버 아님
{
  "success": false,
  "data": null,
  "error": {
    "code": "CR009",
    "message": "크루 멤버만 조회할 수 있습니다."
  }
}

// 404 Not Found - 업로드 세션 없음
{
  "success": false,
  "data": null,
  "error": {
    "code": "V004",
    "message": "업로드 세션을 찾을 수 없습니다."
  }
}

// 400 Bad Request - upload session의 crewId와 요청 crewId 불일치
{
  "success": false,
  "data": null,
  "error": {
    "code": "V016",
    "message": "업로드 세션의 크루 정보가 일치하지 않습니다."
  }
}

// 409 Conflict - 중복 인증
{
  "success": false,
  "data": null,
  "error": {
    "code": "V003",
    "message": "이미 해당 날짜에 인증이 존재합니다."
  }
}

```

**핵심 규칙:**
- upload_session이 COMPLETED 상태여야 함 (Lambda가 S3 업로드 완료 감지 후 COMPLETED 전환)
- verification 생성 시 session COMPLETED 확인만 수행, 중복 사용은 DB UNIQUE constraint(verification.upload_session_id)로 방지
- 텍스트 인증 크루인 경우 uploadSessionId, imageUrl 없이 호출 가능
- 마감 시간 기준: upload_session.requested_at (서버 기록, 조작 불가)
- targetDate는 슬롯(챌린지의 미인증 당일 = startDate+completedDays)으로 서버가 귀속한다 (grace 자정 케이스 포함)

---

### DELETE /verifications/{verificationId} (인증 취소)

마감 전 유저가 자신의 인증을 스스로 취소한다.

**요청 (Request)**
```
DELETE /verifications/ver_789 HTTP/1.1
Authorization: Bearer <token>
```
본문 없음.

**성공 응답 (200 OK)**
```json
{
  "success": true,
  "data": {
    "verificationId": "ver_789",
    "status": "CANCELLED",
    "slotAttempt": 1,
    "challengeProgress": {
      "challengeId": "chal_123",
      "completedDays": 2,
      "targetDays": 3,
      "status": "IN_PROGRESS"
    }
  },
  "error": null
}
```

**필드 설명:**
- `verificationId`: 취소한 인증 ID (요청 경로와 동일)
- `status`: 항상 `"CANCELLED"`
- `slotAttempt`: 취소된 인증의 제출 회차
- `challengeProgress`: 취소 직후 챌린지 현황 — FE가 재조회 없이 진행 카드를 즉시 갱신하는 데 사용
  - `completedDays`·`status`는 취소로 인해 감소·역전이된 값을 반영한다 (예: 3일차 취소 시 `SUCCESS→IN_PROGRESS`, `3→2`)

**멱등:** 이미 `CANCELLED`인 대상에 재요청하면 **200**을 반환하고 `challengeProgress`는 현재 값을 담는다 (중복 감산 없음). 단, **수정으로 치환되어 `CANCELLED`가 된 id**에 DELETE를 보내는 것은 유저 의도(현재 인증 취소)와 다를 수 있다 — 이 구분은 서버가 할 수 없으므로 FE가 최신 `verificationId`를 유지해야 한다.

**에러 응답**
| HTTP | 코드 | 메시지 | 설명 |
|------|------|--------|------|
| 400 | V019 | 인증 마감 시간이 지나 취소·수정할 수 없습니다. | 슬롯 마감 지남 |
| 400 | V020 | 마감이 임박해 취소할 수 없습니다. 내용을 바꾸려면 수정하기를 이용해 주세요. | 마감 임박 컷오프(기본 5분) 이내 |
| 400 | V021 | 오늘은 더 이상 인증을 수정하거나 취소할 수 없습니다. | 슬롯당 상한(기본 3회) 초과 |
| 400 | CR013 | 진행 중인 챌린지만 처리할 수 있습니다. | 취소 역연산 CAS 실패(희귀 레이스) — 트랜잭션 전체 롤백, 재조회 후 재시도 |
| 409 | V023 | 신고 검토 중인 인증은 수정하거나 취소할 수 없습니다. | 대상이 `REPORTED`/`HIDDEN`/`REJECTED` |
| 403 | CR009 | 크루 멤버만 조회할 수 있습니다. | 남의 인증 (`CREW_ACCESS_DENIED` 재사용, 전용 코드 없음) |
| 404 | V001 | 인증을 찾을 수 없습니다. | 존재하지 않는 인증 |

---

### PATCH /verifications/{verificationId} (인증 수정)

마감 전 텍스트·사진을 바꾼다. 내부적으로 **옛 행을 CANCELLED 처리하고 새 행을 만드는 치환 방식**이다.

**요청 (Request)**
```json
PATCH /verifications/ver_789 HTTP/1.1
Authorization: Bearer <token>
Content-Type: application/json

{
  "uploadSessionId": 456,
  "textContent": "오늘도 달리기 완료! (오타 수정)"
}
```

**필드 설명:**
- `uploadSessionId`: (조건부) 사진을 교체할 때만 전달. 없으면 기존 `imageUrl`을 새 행이 승계한다
- `textContent`: (조건부) 텍스트 교체. 없으면 기존 값을 새 행이 승계한다
- 둘 다 없으면 **400 (`INVALID_INPUT`)** — 바꿀 내용이 없는 요청

**크루 타입별 규칙:**
| 크루 타입 | `uploadSessionId` 있음 | 없음 |
|:---:|---|---|
| TEXT | 400 `V017`(`UPLOAD_SESSION_NOT_REQUIRED`) | 텍스트만 교체 |
| PHOTO | 새 사진 + 텍스트 교체 | 기존 `imageUrl` 승계 + 텍스트만 교체 (재업로드 불필요) |

> PHOTO 크루에서 `uploadSessionId` 없는 요청은 `PHOTO_REQUIRED`(V009)가 **아니다** — 텍스트만 수정하는 정상 케이스다.

**성공 응답 (200 OK) — ⚠️ `verificationId`가 바뀐다**
```json
{
  "success": true,
  "data": {
    "verificationId": "ver_901",
    "previousVerificationId": "ver_789",
    "challengeId": "chal_123",
    "userId": "user_456",
    "crewId": "crew_123",
    "imageUrl": "https://s3.../image.jpg",
    "textContent": "오늘도 달리기 완료! (오타 수정)",
    "status": "APPROVED",
    "reviewStatus": "NOT_REQUIRED",
    "reportCount": 0,
    "targetDate": "2026-02-18",
    "slotAttempt": 2,
    "createdAt": "2026-02-18T14:52:00Z"
  },
  "error": null
}
```

`POST /verifications`의 응답과 같은 형태에 `previousVerificationId`·`slotAttempt` 2필드를 더한 것이다.

**필드 설명:**
- `verificationId`: 치환으로 새로 생성된 인증 ID — **응답을 받으면 FE는 로컬 상태를 이 id로 즉시 교체해야 한다** (옛 id를 들고 있지 않는다)
- `previousVerificationId`: 치환 전 인증 ID. 디버깅·로깅용이며 화면 로직에는 쓰지 않는다
- `reviewStatus`·`reportCount`: 새 행이므로 `NOT_REQUIRED`·`0`으로 초기화된다. 신고 이력은 옛 행에 남는다 (다만 `REPORTED` 대상은 애초에 수정이 막힌다)
- `slotAttempt`: 새 행의 제출 회차 (옛 값 + 1)

> ⚠️ **`PATCH`인데 리소스 id가 바뀐다.** 치환 설계의 필연적 결과로, REST 관례에서 벗어나므로 계약에 명시한다. `V022 NOT_ACTIVE`를 받으면 목록을 재조회하고 최신 id로 재시도할 수 있음을 안내한다 — 낡은 id로 재요청해도 404가 아니라 행은 존재하되 `CANCELLED`라서 `V022`가 나가는 것이다.

**에러 응답**
| HTTP | 코드 | 메시지 | 설명 |
|------|------|--------|------|
| 400 | V019 | 인증 마감 시간이 지나 취소·수정할 수 없습니다. | 슬롯 마감 지남 (수정에는 컷오프 미적용) |
| 400 | V021 | 오늘은 더 이상 인증을 수정하거나 취소할 수 없습니다. | 슬롯당 상한(기본 3회) 초과 |
| 400 | V017 | 텍스트 인증 크루에서는 업로드 세션이 필요하지 않습니다. | TEXT 크루에 `uploadSessionId` 첨부 |
| 400 | C001 | 잘못된 입력값입니다. | `uploadSessionId`·`textContent` 둘 다 없음 |
| 400 | V005 / V006 | 업로드 세션이 완료되지 않았습니다. / 업로드 세션이 만료되었습니다. | 세션이 COMPLETED 아님 |
| 400 | V016 | 업로드 세션의 크루 정보가 일치하지 않습니다. | 세션이 다른 크루 소속 |
| 409 | V015 | 이미 사용된 업로드 세션입니다. | 세션 이미 사용됨 |
| 409 | V022 | 이미 취소되었거나 수정된 인증입니다. | 대상이 이미 `CANCELLED` (취소됐거나 수정으로 치환되어 id가 낡음) |
| 409 | V023 | 신고 검토 중인 인증은 수정하거나 취소할 수 없습니다. | 대상이 `REPORTED`/`HIDDEN`/`REJECTED` |
| 403 | CR009 | 크루 멤버만 조회할 수 있습니다. | 남의 인증 (`CREW_ACCESS_DENIED` 재사용, 전용 코드 없음) |
| 404 | V001 | 인증을 찾을 수 없습니다. | 존재하지 않는 인증 |

**공통 참고:** 취소·수정 마감/컷오프는 `triagain.verification` 설정값(기본 `cancel-cutoff-minutes: 5`, `slot-attempt-limit: 3`)을 따르며, 판정 시 `DeadlinePolicy.isWithinDeadline()`(grace 5분 포함)이 아닌 **grace 미포함 순수 비교**를 사용한다.

---

### GET /upload-sessions/{id} (업로드 세션 상태 조회)

> ⚠️ **계약 확정·구현 대기.** 아래는 목표 계약이며 **현재 백엔드에 이 라우트가 없다**
> (`UploadSessionController`에는 `POST /upload-sessions`와 `GET /upload-sessions/{id}/events` 두 개만 존재).
> 지금 호출하면 아래 에러표의 `404 V004` 봉투가 아니라 **라우트 미존재 404**가 돌아온다.
> 응용 계층 `UploadSessionQueryService.findByIdAndUserId(id, userId)`는 이미 소유권 필터를 포함해 존재한다.

SSE 이벤트 누락·연결 실패에 대비해 업로드 세션의 현재 상태를 조회하는 폴링 API.
**호출 순서** — 클라이언트는 S3 업로드 **전에** SSE를 구독하고, **S3 업로드 후** 이 API의 2초 간격 폴링을 시작한다.
두 채널 중 먼저 `COMPLETED` 또는 `EXPIRED`를 확인한 결과를 사용하고 나머지 대기를 종료한다.

**인증:** 필요 (본인이 생성한 업로드 세션만 조회 가능) — 목표 계약

**요청 (Request)**
```http
GET /upload-sessions/123 HTTP/1.1
Authorization: Bearer <token>
```

**파라미터:**
- `id`: (필수) 업로드 세션 ID (Long)

**성공 응답 (200 OK)**
```json
{
  "success": true,
  "data": {
    "uploadSessionId": 123,
    "status": "PENDING"
  },
  "error": null
}
```

**필드 설명:**
- `uploadSessionId`: 조회한 업로드 세션 ID
- `status`: `PENDING`(처리 대기), `COMPLETED`(업로드 완료), `EXPIRED`(만료)

**에러 응답**
| HTTP | 코드 | 메시지 | 설명 |
|------|------|--------|------|
| 401 | A003 | 로그인이 필요합니다. | 인증 토큰 없음·만료 |
| 404 | V004 | 업로드 세션을 찾을 수 없습니다. | 세션이 없거나 요청자 소유가 아님 |

---

### GET /upload-sessions/{id}/events (SSE 구독 — 업로드 완료 알림)

업로드 세션의 상태 변경을 실시간으로 수신하는 SSE 엔드포인트. Lambda가 세션을 COMPLETED로 변경하면 이벤트를 받는다.

**구독 시점: S3 업로드(PUT) 전.** 서버는 미구독 세션의 완료 이벤트를 보관하지 않고 버리므로
(`SseEmitterAdapter.send()`가 등록된 emitter가 없으면 그대로 반환), 구독 전에 Lambda 콜백이 도착하면 이벤트가 영구 유실된다.

> ⚠️ **인증은 계약 확정·구현 대기.** 아래 "인증: 필요"와 `401 A003`은 **목표 계약**이며 현재 구현과 다르다.
> 현재 이 엔드포인트는 `SecurityConfig`에서 `/upload-sessions/*/events`가 `permitAll()`이고
> 컨트롤러가 인증 주체를 받지 않아(`subscribe(@PathVariable Long id)`) **소유권 검증이 없다.**
> 세션 id만 알면 무인증 구독이 가능하다(노출 정보는 `COMPLETED` 문자열).

**인증:** 필요 (본인이 생성한 업로드 세션만 구독 가능) — 목표 계약

**요청 (Request)**
```
GET /upload-sessions/{id}/events HTTP/1.1
Authorization: Bearer <token>
Accept: text/event-stream
```

**파라미터:**
- `id`: (필수) 업로드 세션 ID (Long)

**성공 응답 (200 OK, `text/event-stream`)**
```
event: upload-complete
data: COMPLETED
```

**에러 응답**
| HTTP | 코드 | 메시지 | 설명 |
|------|------|--------|------|
| 401 | A003 | 로그인이 필요합니다. | 인증 토큰 없음·만료 |
| 404 | V004 | 업로드 세션을 찾을 수 없습니다. | 세션이 없거나 요청자 소유가 아님 |

**제약 사항:**
- SSE 타임아웃: 60초
- 클라이언트는 SSE 이벤트 누락·연결 실패에 대비해 `GET /upload-sessions/{id}`를 **S3 업로드 후** 2초 간격으로 병렬 폴링한다 (해당 API는 구현 대기)
- SSE와 폴링 중 먼저 `COMPLETED` 또는 `EXPIRED`를 확인한 결과를 사용하고 나머지 대기를 종료한다

---


### PUT /verifications/{verificationId}/reactions (인증 좋아요 남기기)

크루원이 인증에 이모지 반응을 남긴다. 유저당 인증 1개이며 **재요청은 교체**(멱등).

**요청 (Request)**
```json
PUT /verifications/ver_789/reactions HTTP/1.1
Authorization: Bearer <token>
Content-Type: application/json

{
  "emojiType": "LIKE"
}
```

**파라미터:**
- `emojiType`: (필수) **v1은 `"LIKE"`만 허용**. 서버는 `LIKE`·`FIRE`·`CLAP`·`HEART`·`LAUGH`를 정의하되 활성 세트 밖 값은 400으로 거부한다 (구버전 클라이언트가 렌더할 수 없는 값의 저장 차단)

**성공 응답 (200 OK)**
```json
{
  "success": true,
  "data": {
    "verificationId": "ver_789",
    "reactions": [
      {
        "emojiType": "LIKE",
        "count": 2,
        "reactedByMe": true,
        "users": [
          {"userId": "user_222", "nickname": "지안"},
          {"userId": "user_333", "nickname": "영희"}
        ]
      }
    ]
  },
  "error": null
}
```

**필드 설명:**
- `reactions`: 해당 인증의 **갱신된 전체 요약** (이모지별 그룹, 없으면 `[]`) — FE가 피드 재조회 없이 카드를 갱신하는 데 사용
  - `count`: 그 이모지를 남긴 인원 수
  - `reactedByMe`: 요청자가 그 이모지를 남겼는지
  - `users`: 남긴 사람 전원 (크루 정원 10명 → 본인 인증에도 남길 수 있어 최대 10명이라 전량 반환). 크루를 떠난 사람의 과거 리액션도 포함된다
  - 표시 순서는 서버 정렬(`created_at`, `user_id`)을 따른다

**제약 사항:**
- 같은 유저가 다른 이모지로 재요청하면 **교체**된다 (행이 늘지 않는다). 같은 이모지 재요청도 200 — 상태가 같은 값으로 수렴한다
- 좋아요 **취소는 이 API가 아니라 DELETE**다 (같은 이모지 재탭 = 취소는 클라이언트가 DELETE로 구현)
- 본인 인증에도 남길 수 있다

**에러 응답**

| HTTP | 코드 | 메시지 | 설명 |
|------|------|--------|------|
| 400 | S003 | 이모지는 필수입니다. | `emojiType` 누락·공백 |
| 400 | S006 | 지원하지 않는 이모지입니다. | 정의 밖 값 또는 활성 세트 밖 이모지 |
| 403 | CR009 | 크루 멤버만 조회할 수 있습니다. | 해당 크루의 현재 멤버가 아님 (`CREW_ACCESS_DENIED` 재사용 — 취소 API 선례) |
| 404 | CR001 | 크루를 찾을 수 없습니다. | 인증이 속한 크루 없음 |
| 404 | S005 | 반응을 남길 인증을 찾을 수 없습니다. | 인증 미존재 **또는** 노출 상태(`APPROVED`)가 아님 — 취소·신고 숨김 등. 서버는 이 둘을 구분하지 않는다 (원자 처리) |

---

### DELETE /verifications/{verificationId}/reactions (내 좋아요 취소)

요청자가 그 인증에 남긴 반응을 제거한다. **멱등** — 남긴 반응이 없어도 200이다.

**요청 (Request)**
```
DELETE /verifications/ver_789/reactions HTTP/1.1
Authorization: Bearer <token>
```
본문 없음. (이모지를 지정하지 않는다 — 유저당 1개이므로 대상이 유일하다)

**성공 응답 (200 OK)** — PUT과 동일 구조
```json
{
  "success": true,
  "data": {
    "verificationId": "ver_789",
    "reactions": [
      {
        "emojiType": "LIKE",
        "count": 1,
        "reactedByMe": false,
        "users": [{"userId": "user_333", "nickname": "영희"}]
      }
    ]
  },
  "error": null
}
```

**제약 사항:**
- 마지막 반응이 사라지면 `reactions`는 `[]`
- **취소된 인증에 대한 DELETE도 200**이다 (비노출 데이터 제거라 무해). 이때 `reactions`는 항상 `[]` — 노출 상태가 아닌 인증의 요약은 어떤 응답에도 실리지 않는다
- 응답의 `reactions`는 PUT과 같은 조회를 쓰므로 위 규칙이 두 API에 동일하게 적용된다

**에러 응답**

| HTTP | 코드 | 메시지 | 설명 |
|------|------|--------|------|
| 403 | CR009 | 크루 멤버만 조회할 수 있습니다. | 해당 크루의 현재 멤버가 아님 |
| 404 | CR001 | 크루를 찾을 수 없습니다. | 인증이 속한 크루 없음 |
| 404 | S005 | 반응을 남길 인증을 찾을 수 없습니다. | 인증 미존재 (취소된 인증은 **에러가 아니다** — 위 참조) |

---
