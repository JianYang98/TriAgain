# 🔍 크루 검색 시스템 설계

## 1. 배경

현재 TriAgain은 초대코드로만 크루에 가입할 수 있다. 크루 검색 기능을 도입하여 유저가 관심 있는 크루를 찾아 직접 가입할 수 있게 한다.

---

## 2. 핵심 변경 사항

### 2.1 크루 공개/비공개 도입

| 구분 | 공개 (PUBLIC) | 비공개 (PRIVATE) |
|------|-------------|-----------------|
| 검색 노출 | O | X |
| 가입 방식 | 검색 → 바로 가입 | 초대코드로만 가입 (기존 방식) |
| 초대코드 | 있음 (공유도 가능) | 있음 (필수) |

- 기존 모든 크루는 `PRIVATE`으로 마이그레이션
- 크루 생성 시 공개/비공개 선택 추가 (기본값: PRIVATE)

### 2.2 크루 카테고리 도입

카테고리를 enum으로 관리한다. 4~5개 고정 카테고리에 별도 테이블은 오버엔지니어링.

**CrewCategory enum:**

| enum 값 | displayName | emoji |
|---------|-------------|-------|
| EXERCISE | 운동 | 💪 |
| STUDY | 공부 | 📚 |
| LIFESTYLE | 생활습관 | 🌱 |
| SELF_DEV | 자기계발 | 🚀 |
| ETC | 기타 | ✨ |

- 크루 생성 시 카테고리 필수 선택
- Crew 엔티티에 `category` 필드 (VARCHAR, enum 매핑)
- displayName, emoji는 프론트에서 enum 값 기준으로 매핑

---

## 3. DB 변경

### 3.1 crew 테이블 변경

```sql
-- 카테고리 enum 컬럼 추가 (FK 없음, 문자열로 저장)
ALTER TABLE crew ADD COLUMN category VARCHAR(20);

-- 공개/비공개 추가
ALTER TABLE crew ADD COLUMN visibility VARCHAR(10) NOT NULL DEFAULT 'PRIVATE';
-- visibility: 'PUBLIC' / 'PRIVATE'
```

### 3.2 기존 데이터 마이그레이션

```sql
-- 기존 크루는 모두 PRIVATE
UPDATE crew SET visibility = 'PRIVATE' WHERE visibility IS NULL;

-- category는 nullable로 시작, 이후 크루 생성 시 필수
-- 기존 크루는 카테고리 미지정 상태 (null 허용)
```

---

## 4. API 설계

### 4.1 GET /crews/search (크루 검색)

공개(PUBLIC) + 모집중(RECRUITING) 크루를 검색한다.

**쿼리 파라미터:**

| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|------|------|
| keyword | String | X | 크루 이름/목표 검색 (LIKE 검색) |
| category | String | X | 카테고리 필터 (enum 값: EXERCISE, STUDY 등) |
| page | int | X | 페이지 번호 (기본값 0) |
| size | int | X | 페이지 크기 (기본값 20) |

- keyword와 categoryId 모두 없으면 → 전체 공개+모집중 크루 목록 반환
- keyword만 있으면 → 키워드로 필터
- categoryId만 있으면 → 카테고리로 필터
- 둘 다 있으면 → AND 조건

**성공 응답 (200 OK):**

```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": "crew_123",
        "name": "새벽 러닝 크루",
        "goal": "매일 아침 5km 러닝",
        "category": "EXERCISE",
        "verificationType": "PHOTO",
        "currentMembers": 3,
        "maxMembers": 5,
        "status": "RECRUITING",
        "startDate": "2026-03-20",
        "endDate": "2026-04-01",
        "createdAt": "2026-03-15T10:00:00"
      }
    ],
    "totalElements": 15,
    "totalPages": 1,
    "currentPage": 0
  }
}
```

**검색 조건:**
- visibility = PUBLIC (공개 크루만)
- 상태 조건 (OR):
  - status = RECRUITING (모집중)
  - status = ACTIVE + allowLateJoin = true + endDate - today >= 6일 (중간 가입 가능 + 잔여 6일 이상)
- keyword → crew.name ILIKE '%keyword%' OR crew.goal ILIKE '%keyword%'
- category → crew.category = category (enum 값 일치)

**왜 잔여 6일인가?**
- TriAgain은 3일 챌린지 앱 → 최소 작심삼일 2회차를 할 수 있는 여유가 있어야 의미 있는 합류
- 잔여 3일 이하면 1회차도 빠듯 → 가입해봤자 의미 없음
- 임계값은 설정값으로 외부화하여 운영 중 조정 가능

**인증:** 로그인 필수 (JWT)

---

### 4.2 카테고리 목록

별도 API 없음. enum이므로 프론트에서 하드코딩하거나 앱 설정으로 관리.

```dart
// 프론트에서 enum 매핑
enum CrewCategory {
  EXERCISE('운동', '💪'),
  STUDY('공부', '📚'),
  LIFESTYLE('생활습관', '🌱'),
  SELF_DEV('자기계발', '🚀'),
  ETC('기타', '✨');
}
```

---

### 4.3 POST /crews (크루 생성 — 변경)

기존 크루 생성 API에 필드 추가:

```json
{
  "name": "새벽 러닝 크루",
  "goal": "매일 아침 5km 러닝",
  "verificationContent": "러닝 완료 후 기록 인증",
  "verificationType": "PHOTO",
  "maxMembers": 5,
  "startDate": "2026-03-20",
  "endDate": "2026-04-01",
  "allowLateJoin": true,
  "deadlineTime": "23:59:59",
  "category": "EXERCISE",      // ← 신규 (필수, enum)
  "visibility": "PUBLIC"       // ← 신규 (선택, 기본값 PRIVATE)
}
```

**응답에도 category, visibility 추가:**

```json
{
  "success": true,
  "data": {
    "crewId": "crew_123",
    "category": "EXERCISE",
    "visibility": "PUBLIC",
    ...기존 필드들
  }
}
```

---

### 4.4 POST /crews/{crewId}/join (공개 크루 가입 — 신규)

검색 결과에서 공개 크루에 바로 가입한다. 기존 초대코드 가입(POST /crews/join)과 별도 엔드포인트.

**요청:**
```
POST /crews/{crewId}/join HTTP/1.1
Authorization: Bearer <token>
```

body 없음. crewId로 직접 가입.

**성공 응답 (200 OK):**
```json
{
  "success": true,
  "data": {
    "crewId": "crew_123",
    "role": "MEMBER",
    "currentMembers": 4,
    "joinedAt": "2026-03-18T14:00:00"
  }
}
```

**비즈니스 규칙:**
- visibility = PUBLIC인 크루만 가입 가능
- status = RECRUITING인 크루만 가입 가능
- 정원 초과 불가
- 이미 참여 중이면 409
- PRIVATE 크루에 이 API로 가입 시도 → 403

**에러 응답:**
| HTTP | 코드 | 설명 |
|------|------|------|
| 403 | CR022 | 공개 크루만 직접 가입할 수 있습니다 |
| 400 | CR003 | 모집 중인 크루가 아닙니다 |
| 404 | CR001 | 크루를 찾을 수 없습니다 |
| 409 | CR002 | 크루 정원이 가득 찼습니다 |
| 409 | CR004 | 이미 참여 중인 크루입니다 |

---

## 5. 프론트엔드 UI 설계

### 5.1 크루 탐색 화면 (새 화면)

홈 화면에서 진입할 수 있는 크루 탐색(검색) 화면.

```
┌─────────────────────────────────┐
│ ← 크루 찾기                      │
│                                 │
│ ┌─────────────────────────────┐ │
│ │ 🔍 크루 검색...              │ │  ← 검색바
│ └─────────────────────────────┘ │
│                                 │
│ 💪운동  📚공부  🌱생활습관  🚀자기계발│  ← 카테고리 칩 (가로 스크롤)
│                                 │
│ ┌─────────────────────────────┐ │
│ │ 새벽 러닝 크루         💪    │ │  ← 검색 결과 카드
│ │ 매일 아침 5km 러닝          │ │
│ │ 📷사진 필수  👤 3/5명        │ │
│ │ 3/20 ~ 4/1                  │ │
│ │              [ 참여하기 ]    │ │
│ └─────────────────────────────┘ │
│                                 │
│ ┌─────────────────────────────┐ │
│ │ 영어 단어 외우기 크루  📚    │ │
│ │ 하루 50단어 암기             │ │
│ │ ✏️텍스트만  👤 2/5명         │ │
│ │ 3/21 ~ 4/2                  │ │
│ │              [ 참여하기 ]    │ │
│ └─────────────────────────────┘ │
└─────────────────────────────────┘
```

**동작:**
1. 진입 시 GET /categories로 카테고리 칩 표시
2. GET /crews/search로 전체 공개+모집중 크루 목록 로드
3. 검색바 입력 → keyword 파라미터로 실시간 검색 (debounce 300ms)
4. 카테고리 칩 탭 → categoryId 필터 적용 (토글, 중복 선택 불가)
5. "참여하기" → POST /crews/{crewId}/join → 성공 시 크루 상세 화면 이동

### 5.2 크루 생성 화면 — 변경

기존 크루 만들기 화면에 두 가지 추가:

**카테고리 선택** (필수):
```
카테고리
💪운동  📚공부  🌱생활습관  🚀자기계발   ← 칩 선택 (하나만)
```

**공개/비공개 선택**:
```
공개 설정
🔓 공개    🔒 비공개                      ← 토글 (기존 인증 방식/중간 가입처럼)
```
- 공개: 크루 탐색에서 검색 가능, 누구나 가입
- 비공개: 초대코드로만 가입 (기본값)

### 5.3 홈 화면 — 진입점 추가

홈 화면에 크루 탐색으로 가는 버튼 추가:
- FAB(플로팅 버튼) 또는 상단에 "크루 찾기" 버튼
- 또는 하단 네비게이션에 "탐색" 탭 추가

---

## 6. 구현 순서 (백엔드 → 프론트)

### Phase 1: 백엔드 기반 작업
1. CrewCategory enum 생성
2. crew 테이블에 category, visibility 컬럼 추가 + 마이그레이션
3. POST /crews 변경 (category, visibility 추가)
4. GET /crews/search API 구현
5. POST /crews/{crewId}/join API 구현
6. api-spec.md, biz-logic.md 업데이트
7. CLAUDE.md 반영

### Phase 2: 프론트엔드
1. 크루 생성 화면에 카테고리 선택 + 공개/비공개 추가
2. 크루 탐색 화면 새로 생성
3. 홈 화면에 진입점 추가
4. 검색 결과 → 공개 크루 가입 플로우 연결

---

## 7. 기존 API 영향도

| API | 변경 내용 |
|-----|----------|
| POST /crews (크루 생성) | category(필수, enum), visibility(선택) 파라미터 추가 |
| GET /crews (내 크루 목록) | 응답에 category, visibility 추가 |
| GET /crews/{crewId} (크루 상세) | 응답에 category, visibility 추가 |
| POST /crews/join (초대코드 가입) | 변경 없음 (기존 그대로 유지) |
| PATCH /crews/{crewId} (크루 수정) | visibility 수정 가능 추가 검토 |

---

## 8. 주의사항

1. **기존 크루 호환성**: 기존 크루는 category = null, visibility = PRIVATE. 크루 목록/상세 API에서 category가 null일 수 있음을 프론트가 처리해야 함.
2. **검색 성능**: 초기에는 ILIKE로 충분. 유저/크루 수가 늘면 Full-Text Search 또는 Elasticsearch 검토.
3. **공개 크루 가입과 초대코드 가입 공존**: 공개 크루도 초대코드 공유 가능. 두 가입 경로가 공존.
4. **카테고리 변경**: 크루 수정(PATCH) 시 카테고리도 변경 가능할지? → RECRUITING 상태에서만 허용 권장.
