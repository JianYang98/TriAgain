# 부하 테스트 시드 데이터 가이드

> 부하 테스트용 시드 데이터 구조, 실행 방법, 테스트 시나리오별 매핑

---

## 시드 데이터 구조

### 데이터 규모

| 테이블 | 수량 | 설명 |
|--------|------|------|
| users | 50 | load-user-001 ~ load-user-050 |
| crews | 5 | TEXT 3개 + PHOTO 1개 + 빈 크루 1개 |
| crew_members | 41 | 4개 크루 × 10명 + 빈 크루 리더 1명 |
| challenges | 80 | SUCCESS 40개 (과거) + IN_PROGRESS 40개 (현재) |
| verifications | 120 | 크루당 30건 (10명 × 3일) |
| upload_session | 10 | PHOTO 크루 COMPLETED 세션 |

### 크루 매핑

| 크루 ID | 타입 | 유저 범위 | invite_code | 용도 |
|---------|------|-----------|-------------|------|
| CREW-load-text-1 | TEXT | 001~010 | LDT001 | 피드 조회 메인 타겟 |
| CREW-load-text-2 | TEXT | 011~020 | LDT002 | 피드 조회 추가 부하 |
| CREW-load-text-3 | TEXT | 021~030 | LDT003 | 피드 조회 추가 부하 |
| CREW-load-photo-1 | PHOTO | 031~040 | LDP001 | 사진 인증 테스트 |
| CREW-load-empty-1 | TEXT | 041 (리더만) | LDE001 | 크루 참여 테스트 |

### 유저 매핑

| 유저 범위 | 크루 소속 | 용도 |
|-----------|-----------|------|
| load-user-001 ~ 010 | TEXT 크루 1 | 피드 조회 + 텍스트 인증 |
| load-user-011 ~ 020 | TEXT 크루 2 | 피드 조회 + 텍스트 인증 |
| load-user-021 ~ 030 | TEXT 크루 3 | 피드 조회 + 텍스트 인증 |
| load-user-031 ~ 040 | PHOTO 크루 | 사진 인증 테스트 |
| load-user-041 | 빈 크루 리더 | 크루 참여 테스트 리더 |
| load-user-042 ~ 050 | 미소속 | 크루 참여 테스트용 (자유 유저) |

### 챌린지 구조

- **과거 SUCCESS** (cycle 1): 10일 전 시작 → 8일 전 완료, completed_days=3
- **현재 IN_PROGRESS** (cycle 2): 오늘 시작, completed_days=0 → POST /verifications 테스트 가능

### 인증 데이터 (피드용)

- 날짜: CURRENT_DATE-10, -9, -8 (과거 사이클 3일)
- 상태: 모두 APPROVED (피드 노출 대상)
- TEXT 크루: text_content만 있음
- PHOTO 크루: image_url + text_content 있음

### 업로드 세션

- ID: 10001 ~ 10010 (OVERRIDING SYSTEM VALUE)
- 상태: COMPLETED (verification 생성 가능)
- 아직 verification에 연결되지 않은 상태 → 테스트에서 사용

---

## 실행 방법

### 로컬 환경 (psql 직접 실행)

```bash
# PostgreSQL 접속
psql -h localhost -p 5432 -U triagain -d triagain

# 시드 데이터 실행
\i src/main/resources/load-test-data.sql

# 또는 커맨드라인에서 직접 실행
psql -h localhost -p 5432 -U triagain -d triagain \
  -f src/main/resources/load-test-data.sql
```

### dev 서버

```bash
# SSH 접속 후
psql -h <RDS_ENDPOINT> -p 5432 -U <DB_USER> -d triagain \
  -f load-test-data.sql
```

### 실행 확인

```sql
SELECT COUNT(*) AS user_count FROM users WHERE id LIKE 'load-user-%';               -- 50
SELECT COUNT(*) AS crew_count FROM crews WHERE id LIKE 'CREW-load-%';               -- 5
SELECT COUNT(*) AS member_count FROM crew_members WHERE crew_id LIKE 'CREW-load-%'; -- 41
SELECT COUNT(*) AS challenge_count FROM challenges WHERE user_id LIKE 'load-user-%';-- 80
SELECT COUNT(*) AS vrfy_count FROM verifications WHERE user_id LIKE 'load-user-%';  -- 120
SELECT COUNT(*) AS session_count FROM upload_session WHERE user_id LIKE 'load-user-%'; -- 10
```

---

## k6 테스트 시 JWT 발급

### test-login 엔드포인트 (dev/test 전용)

```bash
# JWT 토큰 발급
curl -s -X POST http://localhost:8080/auth/test-login \
  -H "Content-Type: application/json" \
  -d '{"userId":"load-user-001"}' | jq '.data.accessToken'
```

### 응답 예시

```json
{
  "success": true,
  "data": {
    "isNewUser": false,
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiJ9...",
    "accessTokenExpiresIn": 1800,
    "user": {
      "id": "load-user-001",
      "nickname": "loaduser001",
      "profileImageUrl": null
    }
  }
}
```

### k6에서 대량 토큰 발급

```javascript
import http from 'k6/http';

// setup 단계에서 모든 유저 토큰 일괄 발급
export function setup() {
  const tokens = {};
  for (let i = 1; i <= 50; i++) {
    const userId = `load-user-${String(i).padStart(3, '0')}`;
    const res = http.post(
      'http://localhost:8080/auth/test-login',
      JSON.stringify({ userId }),
      { headers: { 'Content-Type': 'application/json' } }
    );
    tokens[userId] = JSON.parse(res.body).data.accessToken;
  }
  return { tokens };
}
```

### 토큰 만료 주의

- accessToken: 30분 만료
- 장시간 테스트 시 setup에서 발급한 토큰이 만료될 수 있음
- 해결: k6 시나리오 내에서 주기적으로 `/auth/refresh` 호출하거나, 테스트 시간을 30분 이내로 제한

---

## 테스트 시나리오별 사용법

### 시나리오 1: GET /crews/{crewId}/feed (메인 타겟)

**목표:** 조회 API breaking point 찾기 (VUser 10→100→500)

```javascript
// k6 시나리오
export default function (data) {
  const userIndex = (__VU % 10) + 1; // 1~10
  const userId = `load-user-${String(userIndex).padStart(3, '0')}`;
  const token = data.tokens[userId];

  // 피드 조회 (크루당 30건의 과거 인증 데이터)
  const res = http.get(
    'http://localhost:8080/crews/CREW-load-text-1/feed?page=0&size=20',
    { headers: { 'Authorization': `Bearer ${token}` } }
  );

  check(res, {
    'status is 200': (r) => r.status === 200,
    'has verifications': (r) => JSON.parse(r.body).data.verifications.length > 0,
  });
}
```

**확인 포인트:**
- p50/p95/p99 응답 시간
- 페이지네이션 깊은 페이지 (page=5) 성능 변화
- DB Connection Pool 사용률 (HikariCP)

### 시나리오 2: POST /verifications (서브 타겟)

**목표:** 쓰기 부하 + 동시성 검증 (VUser 10→50)

```javascript
export default function (data) {
  const userIndex = (__VU % 30) + 1; // 1~30 (TEXT 크루 유저만)
  const userId = `load-user-${String(userIndex).padStart(3, '0')}`;
  const token = data.tokens[userId];

  // 소속 크루 결정
  let crewId;
  if (userIndex <= 10) crewId = 'CREW-load-text-1';
  else if (userIndex <= 20) crewId = 'CREW-load-text-2';
  else crewId = 'CREW-load-text-3';

  const res = http.post(
    'http://localhost:8080/verifications',
    JSON.stringify({
      crewId: crewId,
      textContent: `부하 테스트 인증 - VU ${__VU} iter ${__ITER}`
    }),
    { headers: {
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json'
    }}
  );

  check(res, {
    'status is 201 or 409': (r) => r.status === 201 || r.status === 409,
  });
}
```

**주의:**
- 각 유저는 하루에 크루당 1회만 인증 가능 (unique constraint)
- 첫 요청: 201 성공, 이후 요청: 409 VERIFICATION_ALREADY_EXISTS
- 이것이 정상 동작 — 동시성 하에서 unique constraint 정확성을 검증하는 시나리오

### 시나리오 3: POST /verifications PHOTO (업로드 세션 활용)

```javascript
// PHOTO 크루 인증 (사전 생성된 COMPLETED 세션 사용)
export default function (data) {
  const userIndex = 31 + (__VU % 10); // 31~40
  const userId = `load-user-${String(userIndex).padStart(3, '0')}`;
  const sessionId = 10000 + (userIndex - 30); // 10001~10010

  const res = http.post(
    'http://localhost:8080/verifications',
    JSON.stringify({
      crewId: 'CREW-load-photo-1',
      uploadSessionId: sessionId,
      textContent: '식단 인증 테스트'
    }),
    { headers: {
      'Authorization': `Bearer ${data.tokens[userId]}`,
      'Content-Type': 'application/json'
    }}
  );
}
```

---

## 데이터 초기화

### 부하 테스트 데이터만 삭제

```sql
-- load-test-data.sql 재실행 시 자동으로 기존 데이터 삭제됨 (멱등성)
-- 수동 삭제가 필요한 경우:

BEGIN;

DELETE FROM verifications WHERE user_id LIKE 'load-user-%';
DELETE FROM upload_session WHERE user_id LIKE 'load-user-%';
DELETE FROM challenges WHERE user_id LIKE 'load-user-%';
DELETE FROM crew_members WHERE crew_id LIKE 'CREW-load-%';
DELETE FROM crews WHERE id LIKE 'CREW-load-%';
DELETE FROM users WHERE id LIKE 'load-user-%';

COMMIT;
```

### 테스트 중 생성된 인증 데이터 초기화

POST /verifications 테스트 후, 현재 사이클 인증 데이터를 삭제하고 챌린지를 리셋해야 재테스트 가능:

```sql
BEGIN;

-- 현재 사이클에서 생성된 인증 삭제 (오늘 날짜)
DELETE FROM verifications
WHERE user_id LIKE 'load-user-%'
  AND target_date = CURRENT_DATE;

-- IN_PROGRESS 챌린지 리셋 (completed_days → 0)
UPDATE challenges
SET completed_days = 0,
    status = 'IN_PROGRESS',
    deadline = CURRENT_DATE + TIME '23:59:59'
WHERE user_id LIKE 'load-user-%'
  AND status IN ('IN_PROGRESS', 'SUCCESS')
  AND cycle_number = 2;

COMMIT;
```

### upload_session 시퀀스 리셋 (필요 시)

```sql
-- 시드 데이터에서 OVERRIDING SYSTEM VALUE로 ID 10001~10010을 사용했으므로
-- auto-increment가 꼬일 수 있음. 필요 시 시퀀스 조정:
SELECT setval(
  pg_get_serial_sequence('upload_session', 'id'),
  GREATEST(
    (SELECT MAX(id) FROM upload_session),
    10010
  )
);
```

---

## ID 네이밍 규칙

시드 데이터의 ID는 아래 규칙을 따른다:

| 엔티티 | 패턴 | 예시 |
|--------|------|------|
| User | `load-user-{NNN}` | load-user-001 |
| Crew | `CREW-load-{type}-{N}` | CREW-load-text-1 |
| CrewMember | `CRMB-{crew}-{NNN}` | CRMB-lt1-001 |
| Challenge | `CHAL-{crew}-{NNN}-c{cycle}` | CHAL-lt1-001-c1 (SUCCESS), CHAL-lt1-001-c2 (IN_PROGRESS) |
| Verification | `VRFY-{crew}-{NNN}-d{day}` | VRFY-lt1-001-d1 |

crew 약어: lt1=text-1, lt2=text-2, lt3=text-3, lp1=photo-1, le1=empty-1
