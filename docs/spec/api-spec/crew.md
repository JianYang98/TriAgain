# API 명세 — 크루 (Crew)

> 전체 인덱스: [`../api-spec.md`](../api-spec.md) · 이 문서가 크루 관련 HTTP 계약의 정본이다.

## 1. 공통 계약

- 별도 표기가 없으면 `Authorization: Bearer <accessToken>`이 필요하다.
- JSON 성공 응답은 `{"success":true,"data":...,"error":null}` 형식이다.
- 실패 응답은 `{"success":false,"data":null,"error":{"code":"...","message":"..."}}` 형식이다.
- 인증이 필요한 API의 토큰 누락·실패는 `401 A003`이다.
- Request Body의 필수값, 길이, enum 형식 등 Bean Validation 실패는 `400 C001`이다.
- 날짜·시각은 각각 ISO-8601 `yyyy-MM-dd`, `yyyy-MM-dd'T'HH:mm:ss` 형식이다.

## 2. 응답 필드 계약

아래 표의 nullable이 `아니요`인 필드는 성공 응답에서 항상 존재한다. 배열은 결과가 없으면
`null`이 아니라 `[]`이다.

### 2.1 크루 기본 필드

아래는 여러 크루 응답에서 반복되는 필드의 뜻이다. 모든 응답이 표의 필드를 전부 반환한다는
뜻은 아니며, 각 API의 정확한 필드 집합은 해당 성공 응답 JSON과 전용 모델 표를 따른다.

| 필드 | JSON 타입 | nullable | 의미·허용값 |
|---|---|---|---|
| `id` / `crewId` | string | 아니요 | 크루 ID. 생성·수정 결과만 `crewId`, 조회 결과는 `id` |
| `creatorId` | string | 아니요 | 크루를 만든 사용자 ID |
| `name` | string | 아니요 | 크루 이름 |
| `goal` | string | 아니요 | 크루 목표 |
| `verificationContent` | string | 아니요 | 크루장이 정한 인증 내용 |
| `verificationType` | string | 아니요 | `TEXT`, `PHOTO` |
| `maxMembers` | number | 아니요 | 최대 인원, 백엔드 허용 범위 `1~10` |
| `currentMembers` | number | 아니요 | 현재 멤버 수 |
| `status` | string | 아니요 | `RECRUITING`, `ACTIVE`, `COMPLETED` |
| `startDate` | string(date) | 아니요 | 크루 시작일 |
| `endDate` | string(date) | 아니요 | 크루 종료일 |
| `allowLateJoin` | boolean | 아니요 | `ACTIVE` 이후 중간 가입 허용 여부 |
| `inviteCode` | string | 아니요 | 6자리 초대코드 |
| `createdAt` | string(date-time) | 아니요 | 크루 생성 시각 |
| `deadlineTime` | string(time) | 아니요 | 일일 인증 마감 시각 |
| `category` | string | 예 | `EXERCISE`, `STUDY`, `LIFESTYLE`, `SELF_DEV`, `ETC`; 과거 데이터는 null 가능 |
| `visibility` | string | 아니요 | `PUBLIC`, `PRIVATE` |

### 2.2 목록 모델 `CrewSummaryResult`

`GET /crews`의 배열 원소다.

| 필드 | JSON 타입 | nullable | 의미 |
|---|---|---|---|
| `id` | string | 아니요 | 크루 ID |
| `name` | string | 아니요 | 크루 이름 |
| `goal` | string | 아니요 | 크루 목표 |
| `verificationContent` | string | 아니요 | 인증 내용 |
| `verificationType` | string | 아니요 | `TEXT`, `PHOTO` |
| `currentMembers` | number | 아니요 | 현재 멤버 수 |
| `maxMembers` | number | 아니요 | 최대 멤버 수 |
| `status` | string | 아니요 | 크루 상태 |
| `startDate` | string(date) | 아니요 | 시작일 |
| `endDate` | string(date) | 아니요 | 종료일 |
| `createdAt` | string(date-time) | 아니요 | 생성 시각 |
| `category` | string | 예 | 크루 카테고리. 과거 데이터는 null 가능 |
| `visibility` | string | 아니요 | 공개 범위 |
| `todayVerified` | boolean | 아니요 | `ACTIVE` 크루의 오늘 승인 인증 여부. 그 외 상태는 `false` |
| `successCount` | number | 아니요 | 요청자의 성공 챌린지 수. `COMPLETED`만 집계하며 그 외 상태의 `0`은 미집계 |
| `verifiedDayCount` | number | 아니요 | 요청자의 승인 인증일 수. `COMPLETED`만 집계하며 그 외 상태의 `0`은 미집계 |
| `inviteCode` | string | 아니요 | 요청자가 멤버이므로 반환하는 초대코드 |
| `challengeProgress` | object | 예 | 요청자의 `IN_PROGRESS` 챌린지. 없으면 null |

### 2.3 챌린지 진행 모델

| 필드 | JSON 타입 | nullable | 의미 |
|---|---|---|---|
| `challengeId` | string | 아니요 | 피드·내 인증 응답에서만 반환하는 챌린지 ID |
| `challengeStatus` / `status` | string | 아니요 | 현재 조회는 활성 챌린지만 대상으로 하므로 `IN_PROGRESS` |
| `completedDays` | number | 아니요 | 현재 사이클의 승인 인증 일수 |
| `targetDays` | number | 아니요 | 목표 일수, 현재 `3` |

크루 목록·상세는 상태 필드명이 `challengeStatus`이고, 피드·내 인증은 `status`다.
피드·내 인증만 `challengeId`를 포함한다.

### 2.4 멤버와 미리보기 모델

| 필드 | JSON 타입 | nullable | 의미 |
|---|---|---|---|
| `members` | array | 아니요 | 현재 크루 멤버 목록 |
| `members[].userId` | string | 아니요 | 멤버 사용자 ID |
| `members[].nickname` | string | 예 | User 프로필 조회 결과가 없으면 null |
| `members[].profileImageUrl` | string | 예 | 프로필 이미지가 없으면 null |
| `members[].role` | string | 아니요 | `LEADER`, `MEMBER` |
| `members[].joinedAt` | string(date-time) | 아니요 | 가입 시각 |
| `members[].successCount` | number | 아니요 | 상세 응답에만 존재. 해당 크루의 성공 챌린지 수 |
| `members[].challengeProgress` | object | 예 | 상세 응답에만 존재. 활성 챌린지가 없으면 null |
| `joinable` | boolean | 아니요 | 미리보기 응답에만 존재. 현재 요청자의 가입 가능 여부 |
| `joinBlockedReason` | string | 예 | 미리보기 응답에만 존재. 가입 가능하면 null |

### 2.5 피드 모델

| 필드 | JSON 타입 | nullable | 의미 |
|---|---|---|---|
| `verifications` | array | 아니요 | `APPROVED` 인증의 최신순 목록 |
| `verifications[].id` | string | 아니요 | 인증 ID |
| `verifications[].userId` | string | 아니요 | 작성자 ID |
| `verifications[].nickname` | string | 아니요 | 작성자 닉네임 |
| `verifications[].profileImageUrl` | string | 예 | 작성자 프로필 이미지 |
| `verifications[].imageUrl` | string | 예 | 사진 인증 이미지. 텍스트 인증은 null |
| `verifications[].textContent` | string | 예 | 인증 텍스트. 사진 인증에서 미입력 가능 |
| `verifications[].targetDate` | string(date) | 아니요 | 인증 대상 날짜 |
| `verifications[].slotAttempt` | number | 아니요 | 해당 날짜 슬롯의 제출 회차 |
| `verifications[].createdAt` | string(date-time) | 아니요 | 현재 인증 행 생성 시각 |
| `verifications[].reactions` | array | 아니요 | 이모지별 반응 요약. 없으면 `[]` |
| `reactions[].emojiType` | string | 아니요 | v1 노출값 `LIKE` |
| `reactions[].count` | number | 아니요 | 해당 이모지를 남긴 사용자 수 |
| `reactions[].reactedByMe` | boolean | 아니요 | 요청자가 해당 이모지를 남겼는지 여부 |
| `reactions[].users` | array | 아니요 | 반응 사용자 전원. 없으면 그룹 자체가 반환되지 않음 |
| `users[].userId` | string | 아니요 | 반응 사용자 ID |
| `users[].nickname` | string | 아니요 | 반응 사용자 닉네임 |
| `myProgress` | object | 예 | 요청자의 활성 챌린지. 없으면 null |
| `hasNext` | boolean | 아니요 | 다음 페이지 존재 여부 |

### 2.6 내 인증 모델

| 필드 | JSON 타입 | nullable | 의미 |
|---|---|---|---|
| `verifiedDates` | array(string) | 아니요 | 크루 기간의 승인 인증일, 오름차순. 없으면 `[]` |
| `streakCount` | number | 아니요 | 가장 최근 승인일부터 역방향으로 연속된 날짜 수 |
| `completedChallenges` | number | 아니요 | 해당 크루의 `SUCCESS` 챌린지 수 |
| `myProgress` | object | 예 | 요청자의 활성 챌린지. 없으면 null |
| `todaySlot` | object | 예 | 오늘 비취소 인증. 없으면 null |
| `todaySlot.verificationId` | string | 아니요 | 오늘 활성 인증 ID |
| `todaySlot.slotAttempt` | number | 아니요 | 오늘 슬롯의 제출 회차 |
| `todaySlot.textContent` | string | 예 | 인증 텍스트 |
| `todaySlot.imageUrl` | string | 예 | 인증 이미지 URL |

### 2.7 가입 결과 모델

| 필드 | JSON 타입 | nullable | 의미 |
|---|---|---|---|
| `userId` | string | 아니요 | 가입한 사용자 ID |
| `crewId` | string | 아니요 | 가입한 크루 ID |
| `role` | string | 아니요 | 항상 `MEMBER` |
| `currentMembers` | number | 아니요 | 가입 반영 후 멤버 수 |
| `joinedAt` | string(date-time) | 아니요 | 가입 시각 |

### 2.8 에러 코드와 메시지

엔드포인트별 표에는 발생 조건을 적고, 실제 기본 메시지는 이 표를 공통으로 사용한다.

| HTTP | 코드 | 기본 메시지 |
|---|---|---|
| 400 | C001 | 잘못된 입력값입니다. 또는 필드 검증 메시지 |
| 401 | A003 | 인증이 필요합니다. |
| 404 | CR001 | 크루를 찾을 수 없습니다. |
| 409 | CR002 | 크루 정원이 가득 찼습니다. |
| 400 | CR003 | 모집 중인 크루가 아닙니다. |
| 409 | CR004 | 이미 참여 중인 크루입니다. |
| 404 | CR006 | 유효하지 않은 초대 코드입니다. |
| 400 | CR008 | 크루 참여 마감 기한이 지났습니다. |
| 403 | CR009 | 크루 멤버만 조회할 수 있습니다. |
| 400 | CR011 | 시작일은 내일 이후여야 합니다. |
| 400 | CR012 | 종료일은 시작일 이후여야 합니다. |
| 400 | CR016 | 크루 기간은 최대 `{N}`일까지 가능합니다. |
| 400 | CR017 | 수정할 필드가 없습니다. |
| 400 | CR018 | 유효하지 않은 값입니다. |
| 409 | CR019 | 크루원이 있는 크루는 삭제할 수 없습니다. |
| 403 | CR020 | 크루장은 탈퇴할 수 없습니다. |
| 404 | CR021 | 해당 크루의 멤버가 아닙니다. |
| 400 | CR022 | 공개 크루가 아닙니다. |
| 409 | CR023 | 동시 요청 충돌이 발생했습니다. 다시 시도해주세요. |
| 400 | CR024 | 크루 기간은 최소 7일 이상이어야 합니다. |
| 400 | CR025 | 진행 중인 크루는 챌린지를 시작하지 않은 멤버만 탈퇴할 수 있습니다. |
| 400 | CR026 | 인증을 시작한 크루는 삭제할 수 없습니다. |

## 3. 조회

### GET /crews

내가 참여 중인 크루 목록을 조회한다. 결과가 없으면 `data`는 `[]`이다.

**성공: `200 OK`**

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
      "successCount": 0,
      "verifiedDayCount": 0,
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

- `status`: `RECRUITING`, `ACTIVE`, `COMPLETED`
- `verificationType`: `TEXT`, `PHOTO`
- `category`: `EXERCISE`, `STUDY`, `LIFESTYLE`, `SELF_DEV`, `ETC`
- `visibility`: `PUBLIC`, `PRIVATE`
- `todayVerified`: `ACTIVE` 크루에서 오늘 승인된 인증이 있는지 나타낸다. 그 외 상태에서는 `false`다.
- `successCount`, `verifiedDayCount`: `COMPLETED` 크루만 집계한다. 다른 상태의 `0`은 미집계 값이다.
- `inviteCode`: 본인이 멤버인 크루이므로 포함한다.
- `challengeProgress`: 요청자의 `IN_PROGRESS` 챌린지가 없으면 `null`이다.

### GET /crews/{crewId}

크루 멤버가 상세와 멤버별 챌린지 현황을 조회한다.

**성공: `200 OK`**

```json
{
  "success": true,
  "data": {
    "id": "crew_123",
    "creatorId": "user_001",
    "name": "새벽 러닝 크루",
    "goal": "매일 아침 5km 러닝",
    "verificationContent": "러닝 완료 후 기록 인증",
    "verificationType": "PHOTO",
    "maxMembers": 5,
    "currentMembers": 2,
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
        "userId": "user_001",
        "nickname": "지안",
        "profileImageUrl": null,
        "role": "LEADER",
        "joinedAt": "2026-03-01T10:00:00",
        "successCount": 2,
        "challengeProgress": {
          "challengeStatus": "IN_PROGRESS",
          "completedDays": 1,
          "targetDays": 3
        }
      }
    ]
  },
  "error": null
}
```

- `nickname`, `profileImageUrl`: User 조회 결과가 없거나 이미지가 없으면 `null`일 수 있다.
- `successCount`: 해당 크루에서 멤버가 성공한 챌린지 수다.
- `challengeProgress`: 해당 멤버의 `IN_PROGRESS` 챌린지가 없으면 `null`이다.

| HTTP | 코드 | 조건 |
|---|---|---|
| 403 | CR009 | 요청자가 크루 멤버가 아님 |
| 404 | CR001 | 크루 없음 |

### GET /crews/invite/{inviteCode}

초대코드로 가입 전 크루를 미리 본다. **인증이 필요하다.** 조회 자체로 가입되지는 않는다.

### GET /crews/{crewId}/preview

검색 결과에서 `PUBLIC` 크루를 미리 본다. **인증이 필요하다.**

두 API의 성공 응답 `data`는 다음과 같이 같다.

```json
{
  "id": "crew_123",
  "creatorId": "user_001",
  "name": "작심삼일 크루",
  "goal": "매일 운동하기",
  "verificationContent": "운동 완료 인증샷",
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
      "nickname": "크루장",
      "profileImageUrl": null,
      "role": "LEADER",
      "joinedAt": "2026-03-01T10:00:00"
    }
  ],
  "joinable": true,
  "joinBlockedReason": null
}
```

`joinBlockedReason`은 가입 가능하면 `null`, 불가능하면 아래 값 중 하나다. 서버 검사 순서상
여러 사유가 겹치면 표의 위쪽 값이 반환된다.

| 값 | 조건 |
|---|---|
| `ALREADY_MEMBER` | 이미 가입함 |
| `CREW_ENDED` | `COMPLETED` 크루 |
| `CREW_FULL` | 정원 도달 |
| `LATE_JOIN_NOT_ALLOWED` | `ACTIVE`이고 중간 가입을 허용하지 않음 |
| `CREW_JOIN_DEADLINE_PASSED` | 오늘이 `endDate - 3일`보다 늦음 |

| API | HTTP | 코드 | 조건 |
|---|---|---|---|
| 초대코드 미리보기 | 404 | CR006 | 유효한 초대코드가 아님 |
| 공개 미리보기 | 400 | CR022 | `PRIVATE` 크루 |
| 공개 미리보기 | 404 | CR001 | 크루 없음 |

### GET /crews/search

인증 없이 `PUBLIC` 크루를 검색한다.

**Query**

| 이름 | 필수 | 기본값 | 규칙 |
|---|---|---|---|
| `keyword` | 아니요 | 없음 | 이름·목표의 대소문자 무시 부분 검색 |
| `category` | 아니요 | 없음 | Crew category enum |
| `page` | 아니요 | `0` | 음수이면 `0` |
| `size` | 아니요 | `20` | `1~50`; 범위를 벗어나면 `20` |

검색 대상은 `PUBLIC`이면서 다음 중 하나인 크루다.

- `RECRUITING`
- `ACTIVE`, `allowLateJoin=true`, `endDate >= 오늘 + crew.search.min-remaining-days`

기본 `crew.search.min-remaining-days`는 코드의 `@Value` 기준 `6`이며, 결과는 `createdAt DESC`다.

잘못된 `category`, `page`, `size` query parameter 바인딩은 `400 C001`이다.

**성공: `200 OK`**

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

### GET /crews/{crewId}/feed

크루 멤버가 승인된 인증 피드와 자신의 진행 중 챌린지를 조회한다.

**Query:** `page` 기본 `0`, `size` 기본 `20`. `page < 0`은 `0`, `size`가 `1~50` 밖이면 `20`이다.

**성공: `200 OK`**

```json
{
  "success": true,
  "data": {
    "verifications": [
      {
        "id": "ver_789",
        "userId": "user_456",
        "nickname": "김철수",
        "profileImageUrl": null,
        "imageUrl": "https://s3.example/image.jpg",
        "textContent": "오늘도 완료!",
        "targetDate": "2026-03-04",
        "slotAttempt": 1,
        "createdAt": "2026-03-04T14:30:00",
        "reactions": [
          {
            "emojiType": "LIKE",
            "count": 2,
            "reactedByMe": true,
            "users": [{"userId": "user_222", "nickname": "지안"}]
          }
        ]
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

- 피드는 `APPROVED` 인증을 최신순으로 반환한다.
- `profileImageUrl`, `imageUrl`, `textContent`는 인증 방식과 사용자 설정에 따라 `null`일 수 있다.
- `reactions`는 없으면 `[]`이며, 사용자 순서는 서버의 `created_at`, `user_id` 정렬을 따른다.
- 취소된 인증은 피드에서 사라진다. 수정은 새 인증 행이므로 이전 행의 반응을 승계하지 않는다.
- `myProgress`는 `IN_PROGRESS` 챌린지가 없으면 `null`이다.

| HTTP | 코드 | 조건 |
|---|---|---|
| 403 | CR009 | 요청자가 크루 멤버가 아님 |
| 404 | CR001 | 멤버십 검증 중 크루 없음 |

### GET /crews/{crewId}/my-verifications

크루 안에서 요청자의 인증 날짜, 스트릭, 챌린지 현황과 오늘 슬롯을 조회한다.

**성공: `200 OK`**

```json
{
  "success": true,
  "data": {
    "verifiedDates": ["2026-03-01", "2026-03-02", "2026-03-03"],
    "streakCount": 3,
    "completedChallenges": 2,
    "myProgress": {
      "challengeId": "chal_123",
      "status": "IN_PROGRESS",
      "completedDays": 2,
      "targetDays": 3
    },
    "todaySlot": {
      "verificationId": "ver_789",
      "slotAttempt": 1,
      "textContent": "오늘도 완료!",
      "imageUrl": null
    }
  },
  "error": null
}
```

- `verifiedDates`: 크루 기간 안의 `APPROVED` 인증일을 오름차순으로 반환한다.
- `streakCount`: `verifiedDates`의 가장 최근 날짜부터 역방향으로 연속된 날짜 수다.
- `completedChallenges`: 해당 크루에서 `SUCCESS`인 챌린지 수다.
- `myProgress`: `IN_PROGRESS` 챌린지가 없으면 `null`이다.
- `todaySlot`: 오늘의 비취소 인증이 없으면 `null`이다. 내용 수정 화면의 초기값으로 사용한다.

| HTTP | 코드 | 조건 |
|---|---|---|
| 403 | CR009 | 요청자가 크루 멤버가 아님 |
| 404 | CR001 | 멤버십·기간 조회 중 크루 없음 |

## 4. 생성·수정

### POST /crews

크루를 만들고 요청자를 `LEADER` 멤버로 등록한다.

**Request**

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

| 필드 | 필수 | 규칙 |
|---|---|---|
| `name` | 예 | 공백 불가, 최대 50자 |
| `goal` | 예 | 공백 불가, 최대 500자 |
| `verificationContent` | 예 | 공백 불가, 최대 50자 |
| `verificationType` | 예 | `TEXT`, `PHOTO` |
| `maxMembers` | 예 | `1~10` |
| `startDate` | 예 | 오늘보다 미래 |
| `endDate` | 예 | 시작일부터 최소 7일 기간, 최대 `crew.max-duration-days` |
| `allowLateJoin` | 아니요 | 누락 시 Java boolean 기본값 `false` |
| `deadlineTime` | 아니요 | 누락 시 `23:59:59` |
| `category` | 예 | Crew category enum |
| `visibility` | 아니요 | 누락 시 `PRIVATE` |

`crew.max-duration-days`의 현재 설정값은 `30`이다. 기간 계산은 `startDate`와 `endDate`의
날짜 차이이며 최소 `6`, 최대 `30`이어야 한다.

**성공: `201 Created`**

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

| HTTP | 코드 | 조건 |
|---|---|---|
| 400 | C001 | 요청 필수값·길이·형식 검증 실패 |
| 400 | CR011 | `startDate`가 오늘보다 미래가 아님 |
| 400 | CR012 | `endDate`가 `startDate`보다 늦지 않음 |
| 400 | CR024 | 날짜 차이가 6일 미만 |
| 400 | CR016 | 날짜 차이가 설정된 최대 일수 초과 |

### PATCH /crews/{crewId}

`LEADER`가 `RECRUITING` 크루의 일부 정보를 수정한다.

**Request:** `name`, `goal`, `verificationContent`, `category`, `visibility` 중 하나 이상.
문자열은 공백만 보낼 수 없고 각각 생성 요청과 같은 최대 길이를 적용한다.

**성공: `200 OK`** — `POST /crews`의 `data`와 같은 필드를 갱신된 값으로 반환한다.

| HTTP | 코드 | 조건 |
|---|---|---|
| 400 | C001 | 필드 길이·enum 형식 검증 실패 |
| 400 | CR003 | `RECRUITING` 상태가 아님 |
| 400 | CR017 | 모든 수정 필드가 `null` 또는 없음 |
| 400 | CR018 | 문자열 필드가 빈 문자열 또는 공백뿐임 |
| 403 | CR009 | 요청자가 `LEADER`가 아님 |
| 404 | CR001 | 크루 없음 |
| 404 | CR021 | 요청자가 크루 멤버가 아님 |

## 5. 가입

### POST /crews/join

초대코드로 `PUBLIC`·`PRIVATE` 크루에 가입한다.

```json
{"inviteCode":"ABC123"}
```

### POST /crews/{crewId}/join

검색한 `PUBLIC` 크루에 직접 가입한다. Request Body는 없다.

두 API 모두 성공 시 `201 Created`와 다음 `data`를 반환한다.

```json
{
  "userId": "user_123",
  "crewId": "crew_123",
  "role": "MEMBER",
  "currentMembers": 4,
  "joinedAt": "2026-03-04T10:00:00"
}
```

가입 가능 상태는 `RECRUITING`, 또는 `ACTIVE && allowLateJoin=true`다.
오늘이 `endDate - 3일`보다 늦으면 가입할 수 없다.

| API | HTTP | 코드 | 조건 |
|---|---|---|---|
| 초대코드 | 400 | C001 | `inviteCode` 누락·공백 |
| 초대코드 | 404 | CR006 | 유효한 초대코드가 아님 |
| 공개 가입 | 400 | CR022 | `PRIVATE` 크루 |
| 공개 가입 | 404 | CR001 | 크루 없음 |
| 공통 | 400 | CR003 | 가입 가능한 상태가 아님 |
| 공통 | 400 | CR008 | 참여 마감 기한 경과 |
| 공통 | 409 | CR002 | 정원 도달 |
| 공통 | 409 | CR004 | 이미 가입함 |
| 공통 | 409 | CR023 | `OPTIMISTIC` 전략에서 설정된 재시도를 모두 소진함 |

현재 기본 `triagain.crew.lock-strategy`는 `CONDITIONAL`이다. 이 전략은
`current_members < max_members` 조건부 원자적 UPDATE로 정원을 지키고,
`(crew_id, user_id)` 유니크 제약 위반을 `CR004`로 변환한다. 별도 Idempotency-Key나
응답 캐시는 사용하지 않는다. `CR023`은 설정을 `OPTIMISTIC`으로 바꿨을 때만 발생 가능한 계약이다.

## 6. 삭제·탈퇴

### DELETE /crews/{crewId}

`LEADER`가 삭제 가능한 크루를 hard delete한다.

- `RECRUITING`이고 리더 혼자이면 가능
- `ACTIVE`이고 해당 크루의 챌린지 행이 하나도 없으며 리더 혼자이면 가능
- 상태 검증을 멤버 수 검증보다 먼저 수행

**성공: `204 No Content`** — 응답 Body 없음.

| HTTP | 코드 | 조건 |
|---|---|---|
| 400 | CR026 | `ACTIVE`에서 챌린지가 존재하거나 삭제 불가 상태 |
| 403 | CR009 | 요청자가 `LEADER`가 아님 |
| 404 | CR001 | 크루 없음 |
| 404 | CR021 | 요청자가 크루 멤버가 아님 |
| 409 | CR019 | 리더 외 멤버가 존재함 |

### DELETE /crews/{crewId}/members/me

`MEMBER`가 크루에서 탈퇴한다.

- `RECRUITING`: 탈퇴 가능
- `ACTIVE`: 요청자의 `(user_id, crew_id)` 챌린지 행이 없을 때만 가능
- `LEADER`, 챌린지를 시작한 `ACTIVE` 멤버, 그 밖의 상태는 탈퇴 불가

**성공: `204 No Content`** — 응답 Body 없음.

| HTTP | 코드 | 조건 |
|---|---|---|
| 400 | CR025 | 진행 중 챌린지가 있거나 탈퇴 불가 상태 |
| 403 | CR020 | `LEADER`가 탈퇴를 요청함 |
| 404 | CR001 | 크루 없음 |
| 404 | CR021 | 요청자가 크루 멤버가 아님 |

## 7. 초대 링크 랜딩

### GET /invite/{inviteCode}

인증 없이 `text/html;charset=UTF-8` 랜딩 페이지를 반환한다.

- DB에서 초대코드를 검증하지 않는다.
- URL의 `inviteCode`를 Thymeleaf model에 전달한다.
- 현재 요청의 base URL로 `ogImageUrl`과 `ogUrl`을 만든다.
- `/invite/**`, `/images/**`, `/css/**`는 Spring Security `permitAll`이다.
- 실제 초대코드 검증은 앱이 `GET /crews/invite/{inviteCode}`를 호출할 때 수행한다.
