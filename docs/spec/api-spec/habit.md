# API 명세 — 습관 · 솔로 모드 (Habit)

> 전체 인덱스: [`../api-spec.md`](../api-spec.md) · 이 문서가 API 계약 정본이다. 코드보다 이 문서를 먼저 수정한다.

---

## Habit (솔로 모드)

> 유저가 크루 없이 '습관'을 등록하고 3일짜리 '작심' 사이클을 반복하는 개인 모드. 전 엔드포인트 `Authorization: Bearer <token>` 필수(미인증 시 A003). 응답 형식은 공통 래핑(`{success, data, error}`)을 따른다.
> 모든 변경 API(등록 제외)는 habit을 `status <> 'ENDED'` 조건으로 조회 — ENDED 습관 접근 시 HB001.

### POST /habits (습관 등록)

**요청 (Request)**
```json
POST /habits HTTP/1.1
Authorization: Bearer <token>
Content-Type: application/json

{
  "name": "매일 물 2L",
  "verificationType": "TEXT",
  "verificationContent": "운동 완료 인증샷 찍기",
  "deadlineTime": "23:59:59"
}
```

**필드 설명:**
- `name`: 필수, 1~50자
- `verificationType`: 필수, `TEXT` | `PHOTO`
- `verificationContent`: 선택, 최대 100자 — 인증 화면 안내 문구 (V24). 빈 문자열은 null로 정규화(안내 없음)
- `deadlineTime`: 선택, 기본 `23:59:59` (v1 FE는 미노출 — 서버 스펙만 지원)

**성공 응답 (201 Created)**
```json
{
  "success": true,
  "data": {
    "habitId": "HBIT-a1b2c3d4e5f60708",
    "name": "매일 물 2L",
    "verificationType": "TEXT",
    "verificationContent": "운동 완료 인증샷 찍기",
    "deadlineTime": "23:59:59",
    "status": "ACTIVE",
    "createdAt": "2026-07-05T14:30:00",
    "endedAt": null
  },
  "error": null
}
```

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

// 401 Unauthorized
{
  "success": false,
  "data": null,
  "error": {
    "code": "A003",
    "message": "로그인이 필요합니다."
  }
}
```

**핵심 규칙:**
- 습관 등록은 사이클을 만들지 않는다 — FE가 등록 직후 `POST /habits/{habitId}/cycles`를 이어 호출

---

### GET /habits (내 습관 목록 조회)

홈 탭(오늘 할 일/오늘 완료/예정)의 솔로 데이터 소스. `status IN ('ACTIVE','PAUSED')`인 본인 습관 전체(ENDED는 `GET /habits/archived`로 분리). 크루 `GET /crews`와 병렬로 홈에서 병합.

**성공 응답 (200 OK)**
```json
{
  "success": true,
  "data": [
    {
      "habitId": "HBIT-a1b2c3d4e5f60708",
      "name": "매일 물 2L",
      "verificationType": "TEXT",
      "verificationContent": "운동 완료 인증샷 찍기",
      "deadlineTime": "23:59:59",
      "status": "ACTIVE",
      "successCount": 4,
      "todayVerified": false,
      "activeCycle": {
        "cycleId": "HCYC-0102030405060708",
        "cycleNumber": 7,
        "completedDays": 1,
        "targetDays": 3,
        "status": "IN_PROGRESS",
        "startDate": "2026-07-04",
        "deadline": "2026-07-07T23:59:59"
      }
    },
    {
      "habitId": "HBIT-b2c3d4e5f6071819",
      "name": "달리기 30분",
      "verificationType": "PHOTO",
      "verificationContent": null,
      "deadlineTime": "23:59:59",
      "status": "PAUSED",
      "successCount": 2,
      "todayVerified": false,
      "activeCycle": null
    }
  ],
  "error": null
}
```

**필드 설명:**
- `verificationContent`: 인증 안내 문구 — 미설정 시 null (V24). 솔로 인증 화면 가이드 노출용
- `successCount`: SUCCESS 사이클 COUNT (별도 캐시 컬럼 없음)
- `todayVerified`: 오늘 인증 존재 여부
- `activeCycle`: IN_PROGRESS 사이클 없으면 null (FAILED/SUCCESS 직후·PAUSED·등록 직후). `startDate`가 미래면 FE는 "내일부터 시작" 표기
- 정렬: `createdAt` 오름차순
- 단건 조회 `GET /habits/{id}`는 v1 미제공 (목록 payload로 충분)

---

### GET /habits/archived (지난기록 — 종료한 습관)

마이페이지 지난기록 화면의 솔로 섹션 데이터 소스. `status = 'ENDED'`인 본인 습관, `endedAt` 내림차순(최근 종료순).

**성공 응답 (200 OK)**
```json
{
  "success": true,
  "data": [
    {
      "habitId": "HBIT-a1b2c3d4e5f60708",
      "name": "매일 물 2L",
      "verificationType": "TEXT",
      "successCount": 6,
      "endedAt": "2026-07-05T21:30:00"
    }
  ],
  "error": null
}
```

**필드 설명:**
- `successCount`: 종료 시점까지 누적 성공(SUCCESS 사이클 COUNT)
- 읽기전용 카드 — 재개/재시작 액션 없음. `activeCycle`·`todayVerified`는 무의미하므로 미포함

**실패 응답**
```json
// 401 Unauthorized
{
  "success": false,
  "data": null,
  "error": {
    "code": "A003",
    "message": "로그인이 필요합니다."
  }
}
```

---

### PATCH /habits/{habitId} (습관 이름 수정)

**요청 (Request)**
```json
PATCH /habits/HBIT-a1b2c3d4e5f60708 HTTP/1.1
Authorization: Bearer <token>
Content-Type: application/json

{
  "name": "매일 물 3L"
}
```
- `name`만 수정 가능(v1) — `verificationType`/`deadlineTime` 변경 불가

**성공 응답 (200 OK)** — POST /habits와 동일한 habit payload

**실패 응답**
```json
// 404 Not Found
{
  "success": false,
  "data": null,
  "error": {
    "code": "HB001",
    "message": "습관을 찾을 수 없습니다."
  }
}

// 403 Forbidden
{
  "success": false,
  "data": null,
  "error": {
    "code": "HB005",
    "message": "본인 습관만 이용할 수 있습니다."
  }
}

// 400 Bad Request - 잘못된 입력값
{
  "success": false,
  "data": null,
  "error": {
    "code": "C001",
    "message": "잘못된 입력값입니다."
  }
}
```

---

### POST /habits/{habitId}/end (습관 종료 — 아카이브)

기존 '삭제'를 대체(D10) — 데이터를 지우지 않고 status만 `ENDED`로 전이하므로 `DELETE`가 아닌 하위 액션 `POST`로 설계.

**성공 응답 (200 OK)** — habit payload (`status=ENDED`, `endedAt` set). IN_PROGRESS 사이클이 있으면 같은 트랜잭션에서 `fail()` 처리. 종료 후 습관은 `GET /habits` 목록에서 사라지고 `GET /habits/archived`에 노출

**실패 응답**
```json
// 404 Not Found
{
  "success": false,
  "data": null,
  "error": {
    "code": "HB001",
    "message": "습관을 찾을 수 없습니다."
  }
}

// 403 Forbidden
{
  "success": false,
  "data": null,
  "error": {
    "code": "HB005",
    "message": "본인 습관만 이용할 수 있습니다."
  }
}
```

**핵심 규칙:**
- 터미널 — ENDED 습관은 재개/재시작/재종료 불가. 다시 하려면 새 습관 등록(성공 카운트 0부터)
- v1은 완전 삭제(하드 삭제) 없음 — 종료가 기록 보존까지 겸함

---

### POST /habits/{habitId}/pause · POST /habits/{habitId}/resume (습관 멈춤 · 재개)

**성공 응답 (200 OK)** — habit payload (status 변경 반영)

**실패 응답**
```json
// 400 Bad Request - pause 전용, IN_PROGRESS 사이클 존재
{
  "success": false,
  "data": null,
  "error": {
    "code": "HB004",
    "message": "진행 중인 작심이 있으면 멈출 수 없습니다."
  }
}

// 404 Not Found
{
  "success": false,
  "data": null,
  "error": {
    "code": "HB001",
    "message": "습관을 찾을 수 없습니다."
  }
}

// 403 Forbidden
{
  "success": false,
  "data": null,
  "error": {
    "code": "HB005",
    "message": "본인 습관만 이용할 수 있습니다."
  }
}
```

**핵심 규칙:**
- pause: IN_PROGRESS 사이클이 없을 때만 가능(HB004). 알림 없음·기록 보존·재개 가능
- resume: ACTIVE 상태에서 호출 시 no-op 200

---

### POST /habits/{habitId}/cycles (사이클 시작 — 첫 시작/재시작 통합)

**요청 (Request)**
```json
POST /habits/HBIT-a1b2c3d4e5f60708/cycles HTTP/1.1
Authorization: Bearer <token>
Content-Type: application/json

{
  "startOption": "TODAY"
}
```
- `startOption`: 선택, `TODAY`(기본) | `TOMORROW`

**성공 응답 (201 Created)**
```json
{
  "success": true,
  "data": {
    "cycleId": "HCYC-0102030405060708",
    "cycleNumber": 7,
    "completedDays": 0,
    "targetDays": 3,
    "status": "IN_PROGRESS",
    "startDate": "2026-07-05",
    "deadline": "2026-07-08T23:59:59"
  },
  "error": null
}
```
- `startDate = today | today+1`, `deadline = startDate.plusDays(3).atTime(habit.deadlineTime)`
- `cycleNumber = findMaxCycleNumber(habitId) + 1` (첫 시작이면 1)

**실패 응답**
```json
// 409 Conflict - 이미 진행 중인 작심이 있음 (더블탭은 유니크 제약 catch 후 기존 사이클을 200으로 반환 — 멱등 처리)
{
  "success": false,
  "data": null,
  "error": {
    "code": "HB002",
    "message": "이미 진행 중인 작심이 있습니다."
  }
}

// 400 Bad Request - TODAY인데 오늘 마감+유예 경과
{
  "success": false,
  "data": null,
  "error": {
    "code": "V002",
    "message": "인증 마감 시간이 지났습니다."
  }
}

// 409 Conflict - TODAY인데 오늘 이미 인증함 (좀비 사이클 방지)
{
  "success": false,
  "data": null,
  "error": {
    "code": "V003",
    "message": "오늘은 이미 인증을 완료했어요. 내일부터 시작할 수 있어요."
  }
}

// 400 Bad Request - PAUSED 습관
{
  "success": false,
  "data": null,
  "error": {
    "code": "HB008",
    "message": "멈춘 습관입니다. 재개 후 시작할 수 있습니다."
  }
}

// 404 Not Found
{
  "success": false,
  "data": null,
  "error": {
    "code": "HB001",
    "message": "습관을 찾을 수 없습니다."
  }
}
```

**핵심 규칙:**
- `startOption=TODAY`는 마감 전(V002) + 오늘 미인증(V003) 두 가드를 모두 통과해야 함. `TOMORROW`는 두 가드 모두 무관하게 항상 허용

---

### DELETE /habits/{habitId}/cycles/current (시작 전 사이클 취소)

**성공 응답 (204 No Content)** — `today < startDate`인 IN_PROGRESS 사이클을 hard delete (시작 전엔 인증이 존재할 수 없어 자식 행 없음)

**실패 응답**
```json
// 400 Bad Request - 활성 사이클 없음
{
  "success": false,
  "data": null,
  "error": {
    "code": "HB003",
    "message": "진행 중인 작심이 없습니다."
  }
}

// 400 Bad Request - 시작일 도래 후 취소 시도
{
  "success": false,
  "data": null,
  "error": {
    "code": "HB007",
    "message": "시작일이 지난 작심은 취소할 수 없습니다."
  }
}

// 404 Not Found
{
  "success": false,
  "data": null,
  "error": {
    "code": "HB001",
    "message": "습관을 찾을 수 없습니다."
  }
}
```

---

### POST /habits/{habitId}/verifications (솔로 인증 생성)

**요청 (Request)**
```json
POST /habits/HBIT-a1b2c3d4e5f60708/verifications HTTP/1.1
Authorization: Bearer <token>
Content-Type: application/json

{
  "uploadSessionId": 123,
  "textContent": "오늘도 물 2L 클리어"
}
```
- `uploadSessionId`: PHOTO 습관 필수, TEXT 습관은 보내지 않음
- `textContent`: TEXT 습관 필수, PHOTO 습관 선택
- `Idempotency-Key` 헤더 미도입 — 습관 행 비관적 락(`CreateHabitVerificationService:45-46`)이 요청을 직렬화해 더블카운트를 차단한다. 두 번째 요청은 기대 슬롯 가드에서 `V002`로 끝난다(실측 — `HabitVerificationConcurrentApiTest`)

**성공 응답 (201 Created)**
```json
{
  "success": true,
  "data": {
    "verificationId": "HVRF-1122334455667788",
    "habitCycleId": "HCYC-0102030405060708",
    "habitId": "HBIT-a1b2c3d4e5f60708",
    "imageUrl": null,
    "textContent": "오늘도 물 2L 클리어",
    "targetDate": "2026-07-05",
    "attemptNumber": 2,
    "cycle": {
      "completedDays": 2,
      "targetDays": 3,
      "status": "IN_PROGRESS"
    }
  },
  "error": null
}
```
- `cycle.status == "SUCCESS"`면 FE가 성공 연출(작심 1회 달성)을 노출

**실패 응답**
```json
// 400 Bad Request - 활성 사이클 없음(FAILED 후 등)
{
  "success": false,
  "data": null,
  "error": {
    "code": "HB003",
    "message": "진행 중인 작심이 없습니다."
  }
}

// 400 Bad Request - 시작일 전(TOMORROW 사이클 사전 인증)
{
  "success": false,
  "data": null,
  "error": {
    "code": "HB006",
    "message": "아직 시작일이 되지 않은 작심입니다."
  }
}

// 400 Bad Request - 멈춘 습관
{
  "success": false,
  "data": null,
  "error": {
    "code": "HB008",
    "message": "멈춘 습관입니다. 재개 후 이용할 수 있습니다."
  }
}

// 400 Bad Request - 다른 습관용으로 발급된 업로드 세션
{
  "success": false,
  "data": null,
  "error": {
    "code": "HB009",
    "message": "다른 습관용으로 발급된 업로드 세션입니다."
  }
}

// 409 Conflict - 오늘 이미 인증함
{
  "success": false,
  "data": null,
  "error": {
    "code": "V003",
    "message": "이미 해당 날짜에 인증이 존재합니다."
  }
}

// 409 Conflict - 같은 업로드 세션 재사용 (uk_habit_verifications_upload_session)
{
  "success": false,
  "data": null,
  "error": {
    "code": "V015",
    "message": "이미 사용된 업로드 세션입니다."
  }
}

// 400 Bad Request - 마감 초과 / 기대 슬롯 불일치(자정 넘긴 grace 인증) / 더블탭 2번째 요청
{
  "success": false,
  "data": null,
  "error": {
    "code": "V002",
    "message": "인증 마감 시간이 지났습니다."
  }
}

// 400 Bad Request - 크루용 세션 교차 사용
{
  "success": false,
  "data": null,
  "error": {
    "code": "V016",
    "message": "업로드 세션의 크루 정보가 일치하지 않습니다."
  }
}

// 404 Not Found
{
  "success": false,
  "data": null,
  "error": {
    "code": "HB001",
    "message": "습관을 찾을 수 없습니다."
  }
}

// 403 Forbidden
{
  "success": false,
  "data": null,
  "error": {
    "code": "HB005",
    "message": "본인 습관만 이용할 수 있습니다."
  }
}
```

**핵심 규칙:**
- 인증 시 `targetDate == cycle.startDate + completedDays`(기대 슬롯) 강제 — 위반 시 V002. 건너뛴 날 마스킹, 자정 넘긴 grace 인증(예: 00:02 "어제 것")을 원천 차단("자정 넘기면 그 날은 실패" 확정)
- 저장 + `cycle.recordCompletion()`은 같은 트랜잭션(원자적). `attemptNumber = completedDays + 1`
- 가드 순서: 습관 존재+소유자 → 활성(ACTIVE) → 사이클 IN_PROGRESS → 시작일 도래 → 기대 슬롯+중복 → 타입/세션 → 마감
- **더블탭(같은 슬롯 재요청)은 `V002`다** — `findByIdForUpdate`(`CreateHabitVerificationService:45-46`)가 사이클을 읽기 전에 습관 행 비관적 락으로 요청을 직렬화한다. 두 번째 요청은 `completedDays`가 이미 증가한 사이클을 읽어 기대 슬롯 가드(`:78-80`)에서 걸린다. 동시·순차 모두 같다(실측 — `HabitVerificationConcurrentApiTest`)
- **`uk_habit_verifications_habit_date` → `HB010` 매핑은 이 엔드포인트로 도달하지 않는다** — 락이 경합을 앞에서 흡수해 제약까지 가지 않는다. 제약·매핑 자체는 실재한다(`HabitUniqueConstraintsIntegrationTest`가 리포지토리 레벨에서 검증). `:82-83`의 `V003` 선검사도 도달 불가 — 그 전제(오늘자 인증 행 + `expectedSlot == today`)를 `StartHabitCycleService:78-85` 좀비 사이클 가드가 막는다

---

