# API 명세 — 크루 (Crew)

> 전체 인덱스: [`../api-spec.md`](../api-spec.md) · 이 문서가 API 계약 정본이다. 코드보다 이 문서를 먼저 수정한다.

---

### GET /crews/{crewId}/feed (크루 피드 조회)

크루원들의 인증 목록과 나의 챌린지 현황을 조회한다.

**요청 (Request)**
```
GET /crews/{crewId}/feed?page=0&size=20 HTTP/1.1
Authorization: Bearer <token>
```

**쿼리 파라미터:**
- `page`: (선택) 페이지 번호 (기본값 0)
- `size`: (선택) 페이지 크기 (기본값 20, 최대 50)

**성공 응답 (200 OK) — 활성 챌린지 있는 경우**
```json
{
  "success": true,
  "data": {
    "verifications": [
      {
        "id": "ver_789",
        "userId": "user_456",
        "nickname": "김철수",
        "profileImageUrl": "https://img.kakao.com/profile.jpg",
        "imageUrl": "https://s3.../image.jpg",
        "textContent": "오늘도 달리기 완료!",
        "targetDate": "2026-03-04",
        "slotAttempt": 1,
        "createdAt": "2026-03-04T14:30:00"
      }
    ],
    "myProgress": {
      "challengeId": "chal_123",
      "status": "IN_PROGRESS",
      "completedDays": 1,
      "targetDays": 3
    },
    "hasNext": false
  },
  "error": null
}
```

**성공 응답 (200 OK) — 활성 챌린지 없는 경우 (myProgress: null)**
```json
{
  "success": true,
  "data": {
    "verifications": [],
    "myProgress": null,
    "hasNext": false
  },
  "error": null
}
```

**필드 설명:**
- `verifications`: 크루 인증 목록 (최신순 정렬)
  - `id`: 인증 ID
  - `userId`: 작성자 ID
  - `nickname`: 작성자 닉네임
  - `profileImageUrl`: 작성자 프로필 이미지 (nullable)
  - `imageUrl`: 인증 이미지 URL (nullable — 텍스트 인증 크루)
  - `textContent`: 인증 텍스트 (nullable — 사진 인증 크루에서 텍스트 미입력 시)
  - `targetDate`: 인증 대상 날짜
  - `slotAttempt`: 해당 슬롯의 제출 회차 (취소·수정 이력 포함). `_FeedCard`의 ⋯ 메뉴(수정/취소) 활성 여부 판단에 사용
  - `createdAt`: 인증 생성 시각
- `myProgress`: 나의 챌린지 현황 (**nullable** — 활성 챌린지가 없으면 null)
  - `challengeId`: 챌린지 ID
  - `status`: 챌린지 상태 (IN_PROGRESS, SUCCESS, FAILED, ENDED)
  - `completedDays`: 완료한 일수
  - `targetDays`: 목표 일수 (3)
- `hasNext`: 다음 페이지 존재 여부

**에러 응답**
| HTTP | 코드 | 메시지 | 설명 |
|------|------|--------|------|
| 401 | A003 | 인증이 필요합니다. | 미인증 |
| 403 | CREW_ACCESS_DENIED | 크루 멤버만 조회할 수 있습니다. | 크루 미참여 |
| 404 | CREW_NOT_FOUND | 존재하지 않는 크루입니다. | 크루 없음 |

---

### GET /crews/{crewId}/my-verifications (내 인증 현황 조회)

크루 내 내 인증 날짜, 연속 스트릭, 작심삼일 달성 횟수를 조회한다.

**요청 (Request)**
```
GET /crews/{crewId}/my-verifications HTTP/1.1
Authorization: Bearer <token>
```

**성공 응답 (200 OK)**
```json
{
  "success": true,
  "data": {
    "verifiedDates": ["2026-03-01", "2026-03-02", "2026-03-03"],
    "streakCount": 3,
    "completedChallenges": 2,
    "myProgress": {
      "challengeId": "chg_abc123",
      "status": "IN_PROGRESS",
      "completedDays": 2,
      "targetDays": 3
    },
    "todaySlot": {
      "verificationId": "ver_789",
      "slotAttempt": 1,
      "textContent": "오늘도 달리기 완료!",
      "imageUrl": null
    }
  },
  "error": null
}
```

**필드 설명:**
- `verifiedDates`: APPROVED 인증 날짜 목록 (크루 기간 범위 내, ASC 정렬). **타입·의미 불변** — 취소·수정 기능 추가와 무관하게 유지된다 (FE 캘린더가 그대로 사용)
- `streakCount`: 최근 날짜부터 역방향 연속 인증 일수
- `completedChallenges`: challenges.status = SUCCESS 개수 (작심삼일 달성 횟수)
- `todaySlot`: 오늘 슬롯의 인증 현황 (**nullable** — 오늘 인증이 없으면 null)
  - `verificationId`: 오늘 슬롯의 유효(비-CANCELLED) 인증 ID
  - `slotAttempt`: 오늘 슬롯의 제출 회차 — FE가 수정/취소 가능 여부·잔여 횟수(상한 대비)를 판단하는 데 사용. 과거 날짜의 `slotAttempt`는 노출하지 않는다(캘린더는 `verifiedDates`만 사용)
  - `textContent`: 인증 텍스트 (nullable — 사진 인증 크루에서 텍스트 미입력 시). 수정 다이얼로그 프리필용
  - `imageUrl`: 인증 이미지 URL (nullable — 텍스트 인증 크루). 수정 다이얼로그 프리필용
- `myProgress`: 나의 현재 챌린지 현황 (**nullable** — 활성 챌린지가 없으면 null)
  - `challengeId`: 챌린지 ID
  - `status`: 챌린지 상태 (IN_PROGRESS, SUCCESS, FAILED, ENDED)
  - `completedDays`: 완료한 일수
  - `targetDays`: 목표 일수 (3)

**에러 응답**
| HTTP | 코드 | 메시지 | 설명 |
|------|------|--------|------|
| 401 | A003 | 인증이 필요합니다. | 미인증 |
| 403 | CR009 | 크루 멤버만 조회할 수 있습니다. | 크루 미참여 |
| 404 | CR001 | 크루를 찾을 수 없습니다. | 크루 없음 |

---

### GET /crews/invite/{inviteCode} (초대코드로 크루 미리보기)

초대코드로 크루 정보를 미리 조회한다. 가입하지 않고 조회만 수행하며, 가입 가능 여부(joinable)와 차단 사유(joinBlockedReason)를 함께 반환한다.

**요청 (Request)**
```
GET /crews/invite/ABC123 HTTP/1.1
Authorization: Bearer <token>
```

**성공 응답 (200 OK)**
```json
{
  "success": true,
  "data": {
    "id": "crew_123",
    "creatorId": "user_001",
    "name": "작심삼일 크루",
    "goal": "매일 운동하기",
    "verificationContent": "운동 완료 인증샷 찍기",
    "verificationType": "PHOTO",
    "maxMembers": 10,
    "currentMembers": 3,
    "status": "RECRUITING",
    "startDate": "2026-03-10",
    "endDate": "2026-03-24",
    "allowLateJoin": true,
    "deadlineTime": "23:59:59",
    "createdAt": "2026-03-01T10:00:00",
    "category": "EXERCISE",
    "visibility": "PUBLIC",
    "members": [
      {
        "userId": "user_001",
        "nickname": "크루장닉네임",
        "profileImageUrl": "https://...",
        "role": "LEADER",
        "joinedAt": "2026-03-01T10:00:00"
      },
      {
        "userId": "user_002",
        "nickname": "멤버닉네임",
        "profileImageUrl": null,
        "role": "MEMBER",
        "joinedAt": "2026-03-02T14:00:00"
      }
    ],
    "joinable": true,
    "joinBlockedReason": null
  },
  "error": null
}
```

**필드 설명:**
- `joinable`: 현재 유저가 이 크루에 가입 가능한지 여부
- `joinBlockedReason`: 가입 불가 시 사유 (joinable=true이면 null)

**joinBlockedReason 값:**

| 값 | 설명 |
|------|------|
| `ALREADY_MEMBER` | 이미 가입한 크루 |
| `CREW_ENDED` | 크루가 종료(COMPLETED)됨 |
| `CREW_FULL` | 정원 초과 |
| `LATE_JOIN_NOT_ALLOWED` | 중간 가입 비허용 (ACTIVE 크루) |
| `CREW_JOIN_DEADLINE_PASSED` | 참여 마감 기한 초과 |

**에러 응답**
| HTTP | 코드 | 메시지 | 설명 |
|------|------|--------|------|
| 404 | CR006 | 유효하지 않은 초대 코드입니다. | 존재하지 않는 초대코드 |

---

### GET /crews/{crewId}/preview (공개 크루 미리보기)

크루 ID로 공개 크루 정보를 미리 조회한다.
검색 결과에서 상세를 확인할 때 사용하며, 초대코드 미리보기(GET /crews/invite/{inviteCode})와 동일한 응답을 반환한다.
PUBLIC 크루만 조회 가능하다.

**요청 (Request)**
```
GET /crews/{crewId}/preview HTTP/1.1
Authorization: Bearer <token>
```

**성공 응답 (200 OK)**
```json
{
  "success": true,
  "data": {
    "id": "crew_123",
    "creatorId": "user_001",
    "name": "작심삼일 크루",
    "goal": "매일 운동하기",
    "verificationContent": "운동 완료 인증샷 찍기",
    "verificationType": "PHOTO",
    "maxMembers": 10,
    "currentMembers": 3,
    "status": "RECRUITING",
    "startDate": "2026-03-10",
    "endDate": "2026-03-24",
    "allowLateJoin": true,
    "deadlineTime": "23:59:59",
    "createdAt": "2026-03-01T10:00:00",
    "category": "EXERCISE",
    "visibility": "PUBLIC",
    "members": [
      {
        "userId": "user_001",
        "nickname": "크루장닉네임",
        "profileImageUrl": "https://...",
        "role": "LEADER",
        "joinedAt": "2026-03-01T10:00:00"
      }
    ],
    "joinable": true,
    "joinBlockedReason": null
  },
  "error": null
}
```

**필드 설명:**
- `joinable`: 현재 유저가 이 크루에 가입 가능한지 여부
- `joinBlockedReason`: 가입 불가 시 사유 (joinable=true이면 null)

**joinBlockedReason 값:**

| 값 | 설명 |
|------|------|
| `ALREADY_MEMBER` | 이미 가입한 크루 |
| `CREW_ENDED` | 크루가 종료(COMPLETED)됨 |
| `CREW_FULL` | 정원 초과 |
| `LATE_JOIN_NOT_ALLOWED` | 중간 가입 비허용 (ACTIVE 크루) |
| `CREW_JOIN_DEADLINE_PASSED` | 참여 마감 기한 초과 |

**에러 응답**

| HTTP | 코드 | 메시지 | 설명 |
|------|------|--------|------|
| 400 | CR022 | 공개 크루가 아닙니다. | PRIVATE 크루에 접근 시도 |
| 404 | CR001 | 크루를 찾을 수 없습니다. | 존재하지 않는 crewId |

---

### POST /crews/join (초대코드로 크루 참여)

초대코드를 사용하여 크루에 참여한다. 크루가 RECRUITING 상태이고, 정원이 남아있는 경우에만 참여 가능.

**요청 (Request)**
```
POST /crews/join HTTP/1.1
Authorization: Bearer <token>
Content-Type: application/json
```
```json
{
  "inviteCode": "ABC123"
}
```

**필드 설명:**
- `inviteCode`: (필수) 크루 초대코드 (6자리)

**성공 응답 (201 Created)**
```json
{
  "success": true,
  "data": {
    "userId": "1234567890",
    "crewId": "crew_123",
    "role": "MEMBER",
    "currentMembers": 3,
    "joinedAt": "2026-03-04T10:00:00Z"
  },
  "error": null
}
```

**에러 응답**
| HTTP | 코드 | 메시지 | 설명 |
|------|------|--------|------|
| 400 | CR003 | 모집 중인 크루가 아닙니다. | 크루 상태가 RECRUITING이 아님 |
| 400 | CR008 | 크루 참여 마감 기한이 지났습니다. | 중간 가입 불가 시 기한 초과 |
| 404 | CR006 | 유효하지 않은 초대 코드입니다. | 존재하지 않는 초대코드 |
| 409 | CR002 | 크루 정원이 가득 찼습니다. | 정원 초과 |
| 409 | CR004 | 이미 참여 중인 크루입니다. | 중복 참여 |
| 409 | CR023 | 동시 요청 충돌이 발생했습니다. 다시 시도해주세요. | 낙관적 락 재시도 3회 실패 |

---

### GET /crews/{crewId} (크루 상세 조회)

크루 멤버가 상세 화면을 볼 때 사용한다. 멤버가 아니면 403.

**요청 (Request)**
```
GET /crews/{crewId} HTTP/1.1
Authorization: Bearer {accessToken}
```

**응답 (Response)**
```json
{
  "success": true,
  "data": {
    "id": "crew-uuid",
    "creatorId": "user-uuid",
    "name": "새벽 러닝 크루",
    "goal": "매일 아침 5km 러닝",
    "verificationContent": "러닝 완료 후 기록 인증",
    "verificationType": "PHOTO",
    "maxMembers": 5,
    "currentMembers": 3,
    "status": "ACTIVE",
    "startDate": "2026-03-10",
    "endDate": "2026-03-24",
    "allowLateJoin": true,
    "inviteCode": "ABC123",
    "createdAt": "2026-03-01T10:00:00",
    "deadlineTime": "23:59:59",
    "category": "EXERCISE",
    "visibility": "PUBLIC",
    "members": [
      {
        "userId": "user-uuid-1",
        "nickname": "크루장닉네임",
        "profileImageUrl": "https://...",
        "role": "LEADER",
        "joinedAt": "2026-03-01T10:00:00",
        "successCount": 2,
        "challengeProgress": {
          "challengeStatus": "IN_PROGRESS",
          "completedDays": 1,
          "targetDays": 3
        }
      },
      {
        "userId": "user-uuid-2",
        "nickname": "멤버닉네임",
        "profileImageUrl": null,
        "role": "MEMBER",
        "joinedAt": "2026-03-02T14:00:00",
        "successCount": 0,
        "challengeProgress": null
      }
    ]
  },
  "error": null
}
```

**필드 설명:**
- `successCount`: 해당 크루에서의 작심삼일(3일 연속 인증) 달성 횟수. 활성 챌린지 유무와 무관하게 항상 표시
- `challengeProgress`: 현재 활성(IN_PROGRESS) 챌린지 진행 상황. 활성 챌린지가 없으면 `null`
  - `challengeStatus`: 챌린지 상태 (IN_PROGRESS, SUCCESS, FAILED, ENDED)
  - `completedDays`: 완료한 일수
  - `targetDays`: 목표 일수 (3)

**에러 응답**
| HTTP | 코드 | 메시지 | 설명 |
|------|------|--------|------|
| 403 | CR009 | 크루 멤버만 조회할 수 있습니다. | 비멤버 접근 |
| 404 | CR001 | 크루를 찾을 수 없습니다. | 존재하지 않는 crewId |

---

### POST /crews (크루 생성)

새로운 크루를 생성한다. 생성자는 자동으로 LEADER 역할의 첫 번째 멤버로 추가된다.

**요청 (Request)**
```
POST /crews HTTP/1.1
Authorization: Bearer <token>
Content-Type: application/json
```
```json
{
  "name": "새벽 러닝 크루",
  "goal": "매일 아침 5km 러닝",
  "verificationContent": "러닝 완료 후 기록 인증",
  "verificationType": "PHOTO",
  "maxMembers": 5,
  "startDate": "2026-03-10",
  "endDate": "2026-03-24",
  "allowLateJoin": true,
  "deadlineTime": "23:59:59",
  "category": "EXERCISE",
  "visibility": "PUBLIC"
}
```

**필드 설명:**
- `name`: (필수) 크루 이름
- `goal`: (필수) 크루 목표
- `verificationContent`: (필수) 인증 내용 (최대 50자)
- `verificationType`: (필수) 인증 방식 — `TEXT` / `PHOTO`
- `maxMembers`: (필수) 최대 정원 (1~10)
- `startDate`: (필수) 크루 시작일 (오늘+1 이후)
- `endDate`: (필수) 크루 종료일 (시작일 + 최소 6일 = 최소 7일 기간 / 최대 `crew.max-duration-days`일, 기본 30일)
- `allowLateJoin`: (선택) 중간 가입 허용 여부 (기본값 false)
- `deadlineTime`: (선택) 일일 인증 마감 시간 (기본값 23:59:59)
- `category`: (필수) 크루 카테고리 — `EXERCISE` / `STUDY` / `LIFESTYLE` / `SELF_DEV` / `ETC`
- `visibility`: (선택) 공개 설정 — `PUBLIC` / `PRIVATE` (기본값 `PRIVATE`)

**성공 응답 (201 Created)**
```json
{
  "success": true,
  "data": {
    "crewId": "crew_123",
    "creatorId": "user_456",
    "name": "새벽 러닝 크루",
    "goal": "매일 아침 5km 러닝",
    "verificationContent": "러닝 완료 후 기록 인증",
    "verificationType": "PHOTO",
    "maxMembers": 5,
    "currentMembers": 1,
    "status": "RECRUITING",
    "startDate": "2026-03-10",
    "endDate": "2026-03-24",
    "allowLateJoin": true,
    "inviteCode": "ABC123",
    "createdAt": "2026-03-09T10:00:00",
    "deadlineTime": "23:59:59",
    "category": "EXERCISE",
    "visibility": "PUBLIC"
  },
  "error": null
}
```

**에러 응답**
| HTTP | 코드 | 메시지 | 설명 |
|------|------|--------|------|
| 400 | CR011 | 시작일은 내일 이후여야 합니다. | startDate가 오늘 이전 |
| 400 | CR012 | 종료일은 시작일 이후여야 합니다. | endDate ≤ startDate |
| 400 | CR024 | 크루 기간은 최소 7일 이상이어야 합니다. | (endDate - startDate) < 6일 |
| 400 | CR016 | 크루 기간은 최대 {N}일까지 가능합니다. | (endDate - startDate) > `crew.max-duration-days` |

---

### GET /crews (내 크루 목록 조회)

내가 참여 중인 크루 목록을 조회한다. 홈 화면에서 사용한다.

**요청 (Request)**
```
GET /crews HTTP/1.1
Authorization: Bearer <token>
```

**성공 응답 (200 OK)**
```json
{
  "success": true,
  "data": [
    {
      "id": "crew_123",
      "name": "새벽 러닝 크루",
      "goal": "매일 아침 5km 러닝",
      "verificationContent": "러닝 완료 후 기록 인증",
      "verificationType": "PHOTO",
      "currentMembers": 3,
      "maxMembers": 5,
      "status": "ACTIVE",
      "startDate": "2026-03-10",
      "endDate": "2026-03-24",
      "createdAt": "2026-03-01T10:00:00",
      "category": "EXERCISE",
      "visibility": "PUBLIC",
      "todayVerified": false,
      "successCount": 2,
      "verifiedDayCount": 8,
      "inviteCode": "A1B2C3",
      "challengeProgress": {
        "challengeStatus": "IN_PROGRESS",
        "completedDays": 1,
        "targetDays": 3
      }
    }
  ],
  "error": null
}
```

**필드 설명:**
- `id`: 크루 ID
- `name`: 크루 이름
- `goal`: 크루 목표
- `verificationContent`: 인증 내용
- `verificationType`: 인증 방식 (`TEXT` / `PHOTO`)
- `currentMembers`: 현재 멤버 수
- `maxMembers`: 최대 정원
- `status`: 크루 상태 (`RECRUITING`, `ACTIVE`, `COMPLETED`)
- `startDate`: 크루 시작일
- `endDate`: 크루 종료일
- `createdAt`: 크루 생성 시각
- `category`: 크루 카테고리 (nullable — 기존 크루는 null)
- `visibility`: 공개 설정 (`PUBLIC` / `PRIVATE`)
- `todayVerified`: 오늘 인증 완료 여부 (boolean)
- `successCount` (int): 요청자가 이 크루에서 달성한 작심삼일(연속 3일 인증 성공) 횟수. `COMPLETED` 크루만 실집계, `RECRUITING`/`ACTIVE`는 `0`(미집계) — ACTIVE 크루의 `0`을 "달성 0회"로 오해 금지(미집계 ≠ 0회 달성).
- `verifiedDayCount` (int): 요청자가 이 크루에서 `APPROVED` 인증을 한 총 일수. `COMPLETED` 크루만 실집계, `RECRUITING`/`ACTIVE`는 `0`(미집계) — ACTIVE 크루의 `0`을 "달성 0회"로 오해 금지(미집계 ≠ 0회 달성).
- `inviteCode`: 크루 초대코드 (6자리 — 본인이 멤버인 크루 목록이므로 노출 안전)
- `challengeProgress` (nullable): 요청자의 현재 진행 중인 챌린지 진행도. 활성(IN_PROGRESS) 챌린지 없으면 `null`.
  - `challengeStatus`: 챌린지 상태 (`IN_PROGRESS`)
  - `completedDays`: 완료한 인증 일수 (0 ~ targetDays-1 — 목표 도달 시 챌린지가 SUCCESS로 전환되어 목록엔 미노출)
  - `targetDays`: 목표 일수 (현재 항상 3)

**에러 응답**
| HTTP | 코드 | 메시지 | 설명 |
|------|------|--------|------|
| 401 | A003 | 인증이 필요합니다. | 미인증 |

---

### PATCH /crews/{crewId} (크루 수정)

크루장이 RECRUITING 상태 크루의 정보를 부분 수정한다. 최소 1개 이상 필드가 포함되어야 한다.

**요청 (Request)**
```json
PATCH /crews/{crewId} HTTP/1.1
Authorization: Bearer <token>
Content-Type: application/json

{
  "name": "수정된 이름",
  "goal": "수정된 목표",
  "verificationContent": "수정된 인증내용",
  "category": "STUDY",
  "visibility": "PUBLIC"
}
```

**필드 설명:**
- `name`: (선택) 크루 이름
- `goal`: (선택) 크루 목표
- `verificationContent`: (선택) 인증 내용
- `category`: (선택) 크루 카테고리 — `EXERCISE` / `STUDY` / `LIFESTYLE` / `SELF_DEV` / `ETC`
- `visibility`: (선택) 공개 설정 — `PUBLIC` / `PRIVATE`
- 5개 필드 모두 optional (PATCH 시맨틱), 최소 1개 이상 필수
- 빈 문자열("") 또는 공백만 있는 값은 거부

**성공 응답 (200 OK)**
```json
{
  "success": true,
  "data": {
    "crewId": "crew_123",
    "creatorId": "user_456",
    "name": "수정된 이름",
    "goal": "수정된 목표",
    "verificationContent": "수정된 인증내용",
    "verificationType": "PHOTO",
    "maxMembers": 5,
    "currentMembers": 1,
    "status": "RECRUITING",
    "startDate": "2026-03-10",
    "endDate": "2026-03-24",
    "allowLateJoin": true,
    "inviteCode": "ABC123",
    "createdAt": "2026-03-09T10:00:00",
    "deadlineTime": "23:59:59",
    "category": "STUDY",
    "visibility": "PUBLIC"
  },
  "error": null
}
```

**에러 응답**
| HTTP | 코드 | 메시지 | 설명 |
|------|------|--------|------|
| 400 | CR003 | 모집 중인 크루가 아닙니다. | RECRUITING 상태가 아님 |
| 400 | CR017 | 수정할 필드가 없습니다. | 빈 body / 모든 필드 null |
| 400 | CR018 | 유효하지 않은 값입니다. | 빈 문자열 또는 공백만 있는 값 |
| 403 | CR009 | 크루장만 수정할 수 있습니다. | LEADER가 아님 |
| 404 | CR001 | 크루를 찾을 수 없습니다. | 존재하지 않는 crewId |
| 404 | CR021 | 해당 크루의 멤버가 아닙니다. | 크루 미참여 |

---

### DELETE /crews/{crewId} (크루 삭제)

크루장이 혼자이고 인증을 시작하지 않은 크루를 삭제한다. hard delete (DB에서 완전 삭제, FK-safe).

**처리 정책**
- RECRUITING + 혼자(멤버 1명) → 삭제 가능 (기존)
- ACTIVE + 혼자 + 인증 전(`challenges`에 `crew_id` 레코드 없음) → 삭제 가능 (신규)
- ACTIVE + 인증 시작(`challenges`에 `crew_id` 레코드 존재) → 거부 (`CR026`)
- COMPLETED → 거부 (`CR026`)
- 멤버 2명 이상 → 거부 (`CR019`)
- **검증 순서**: 상태 게이트(`CR026`)가 멤버 수 체크(`CR019`)보다 먼저

**요청 (Request)**
```
DELETE /crews/{crewId} HTTP/1.1
Authorization: Bearer <token>
```

**성공 응답 (204 No Content)**

응답 body 없음.

**에러 응답**
| HTTP | 코드 | 메시지 | 설명 |
|------|------|--------|------|
| 400 | CR026 | 인증을 시작한 크루는 삭제할 수 없습니다. | ACTIVE+인증시작 / COMPLETED 등 삭제 불가 상태 |
| 403 | CR009 | 크루장만 삭제할 수 있습니다. | LEADER가 아님 |
| 404 | CR001 | 크루를 찾을 수 없습니다. | 존재하지 않는 crewId |
| 404 | CR021 | 해당 크루의 멤버가 아닙니다. | 크루 미참여 |
| 409 | CR019 | 크루원이 있는 크루는 삭제할 수 없습니다. | 멤버가 LEADER 본인 외에 존재 |

---

### DELETE /crews/{crewId}/members/me (크루 탈퇴)

크루원(MEMBER)이 크루에서 탈퇴한다. RECRUITING은 무조건 가능, ACTIVE는 챌린지를 한 번도 시작하지 않은 멤버만 가능. LEADER는 탈퇴 불가 (크루 삭제 또는 회원탈퇴 시 자동 위임 사용).

**요청 (Request)**
```
DELETE /crews/{crewId}/members/me HTTP/1.1
Authorization: Bearer <token>
```

**처리 정책:**
- RECRUITING → 무조건 탈퇴 가능
- ACTIVE + 챌린지 미시작(`challenges` 테이블에 (user_id, crew_id) 레코드 없음) → 탈퇴 가능
- ACTIVE + 챌린지 시작 / COMPLETED / FAILED → 거부 (`CR025`)

**성공 응답 (204 No Content)**

응답 body 없음.

**에러 응답**
| HTTP | 코드 | 메시지 | 설명 |
|------|------|--------|------|
| 400 | CR025 | 진행 중인 크루는 챌린지를 시작하지 않은 멤버만 탈퇴할 수 있습니다. | ACTIVE + 챌린지 시작 / COMPLETED / FAILED |
| 403 | CR020 | 크루장은 탈퇴할 수 없습니다. | LEADER 탈퇴 시도 |
| 404 | CR001 | 크루를 찾을 수 없습니다. | 존재하지 않는 crewId |
| 404 | CR021 | 해당 크루의 멤버가 아닙니다. | crew_member 레코드 없음 |

---

### GET /crews/search (크루 검색)

공개(PUBLIC) 크루를 검색한다. 비로그인 사용자도 조회 가능 (permitAll).

**요청 (Request)**
```
GET /crews/search?keyword=러닝&category=EXERCISE&page=0&size=20 HTTP/1.1
```

**쿼리 파라미터:**
- `keyword`: (선택) 검색어 — 크루 이름, 목표에서 LIKE 검색
- `category`: (선택) 카테고리 필터 — `EXERCISE` / `STUDY` / `LIFESTYLE` / `SELF_DEV` / `ETC`
- `page`: (선택) 페이지 번호 (기본값 0)
- `size`: (선택) 페이지 크기 (기본값 20, 최대 50)

**검색 조건:**
- `visibility = PUBLIC`
- AND (`status = RECRUITING` OR (`status = ACTIVE` AND `allowLateJoin = true` AND `endDate - today >= 6`))
- 정렬: `createdAt DESC`

**성공 응답 (200 OK)**
```json
{
  "success": true,
  "data": {
    "crews": [
      {
        "id": "crew_123",
        "name": "새벽 러닝 크루",
        "goal": "매일 아침 5km 러닝",
        "verificationContent": "러닝 완료 후 기록 인증",
        "category": "EXERCISE",
        "verificationType": "PHOTO",
        "allowLateJoin": true,
        "currentMembers": 3,
        "maxMembers": 5,
        "status": "RECRUITING",
        "startDate": "2026-03-10",
        "endDate": "2026-03-24",
        "createdAt": "2026-03-01T10:00:00"
      }
    ],
    "hasNext": false
  },
  "error": null
}
```

**필드 설명:**
- `crews`: 검색 결과 크루 목록
  - `id`: 크루 ID
  - `name`: 크루 이름
  - `goal`: 크루 목표
  - `verificationContent`: 인증 내용
  - `category`: 크루 카테고리
  - `verificationType`: 인증 방식 (`TEXT` / `PHOTO`)
  - `allowLateJoin`: 중간 가입 허용 여부
  - `currentMembers`: 현재 멤버 수
  - `maxMembers`: 최대 정원
  - `status`: 크루 상태 (`RECRUITING`, `ACTIVE`)
  - `startDate`: 크루 시작일
  - `endDate`: 크루 종료일
  - `createdAt`: 크루 생성 시각
- `hasNext`: 다음 페이지 존재 여부

---

### POST /crews/{crewId}/join (공개 크루 가입)

공개 크루에 직접 가입한다. 비공개 크루는 초대코드(POST /crews/join)로만 가입 가능.

**요청 (Request)**
```
POST /crews/{crewId}/join HTTP/1.1
Authorization: Bearer <token>
```

**성공 응답 (201 Created)**
```json
{
  "success": true,
  "data": {
    "userId": "1234567890",
    "crewId": "crew_123",
    "role": "MEMBER",
    "currentMembers": 4,
    "joinedAt": "2026-03-04T10:00:00Z"
  },
  "error": null
}
```

**비즈니스 규칙:**
- `visibility = PUBLIC`인 크루만 직접 가입 가능
- `status = RECRUITING` 또는 (`status = ACTIVE` AND `allowLateJoin = true` AND 참여 마감 기한 이내)
- 정원 미초과, 중복 참여 불가

**에러 응답**
| HTTP | 코드 | 메시지 | 설명 |
|------|------|--------|------|
| 400 | CR022 | 공개 크루가 아닙니다. | visibility=PRIVATE인 크루 |
| 400 | CR003 | 모집 중인 크루가 아닙니다. | 크루 상태가 가입 불가 |
| 400 | CR008 | 크루 참여 마감 기한이 지났습니다. | 중간 가입 기한 초과 |
| 404 | CR001 | 크루를 찾을 수 없습니다. | 존재하지 않는 crewId |
| 409 | CR002 | 크루 정원이 가득 찼습니다. | 정원 초과 |
| 409 | CR004 | 이미 참여 중인 크루입니다. | 중복 참여 |
| 409 | CR023 | 동시 요청 충돌이 발생했습니다. 다시 시도해주세요. | 낙관적 락 재시도 3회 실패 |

---

### GET /invite/{inviteCode} (초대 링크 랜딩 페이지)

초대코드를 포함한 HTML 랜딩 페이지를 반환한다. 인증 불필요.

**요청 (Request)**
```
GET /invite/ABC123 HTTP/1.1
Host: triagain.kr
Accept: text/html
```

**경로 파라미터:**
- `inviteCode`: 6자리 초대코드 (URL에서 추출, DB 검증 없음)

**성공 응답 (200 OK)**
```
Content-Type: text/html;charset=UTF-8

[HTML 랜딩 페이지 — Thymeleaf 렌더링]
```

**보안:**
- Spring Security `permitAll()` 적용: `/invite/**`, `/images/**`, `/css/**`, `/feedback`
- 기존 API 인증 흐름에 영향 없음

**정적 리소스:**
- `/images/logo.png` — TriAgain 로고 (frontend에서 복사)

**참고:**
- DB 조회 없음 — URL의 inviteCode를 그대로 Thymeleaf Model에 담아 템플릿에 전달
- 잘못된 코드 별도 검증 없음 — 앱에서 입력 시 검증됨
- Phase 2: 딥링크(App Links / Universal Links) 추가 예정

---

