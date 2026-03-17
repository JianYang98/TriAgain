# 🔧 백엔드 에이전트 작업 지시서: 크루 관리 기능 (수정/삭제/탈퇴)

## 📋 작업 개요

크루 활성화 전(WAITING 상태)에서의 크루 관리 기능 3가지를 구현한다.

| 기능 | 누가 | 조건 | 행위 |
|------|------|------|------|
| 크루 수정 | 크루장(LEADER) | 크루 상태 = WAITING | 제목, 목표, 인증내용 수정 |
| 크루 삭제 | 크루장(LEADER) | 크루 상태 = WAITING + 멤버가 LEADER 본인 1명뿐 | hard delete (DB에서 완전 삭제) |
| 크루 탈퇴 | 크루원(MEMBER) | 크루 상태 = WAITING + 본인이 LEADER가 아님 | crew_member 레코드 삭제 |

---

## 🏗️ 기존 구조 참고 (확인 후 수정할 것)

- **Crew 엔티티**: status 필드 존재 (enum: WAITING, ACTIVE 등)
- **crew_member 조인 테이블**: 크루-유저 관계 관리
- **인증**: JWT 기반 (현재 사용자 ID 추출)

> ⚠️ 작업 시작 전 CLAUDE.md에서 현재 패키지 구조, 엔티티 필드명, enum 값을 반드시 확인할 것

---

## 📡 API 설계

### 1. 크루 수정 — `PATCH /api/crews/{crewId}`

**요청 조건**
- 인증 필수 (JWT)
- 요청자 = 해당 크루의 LEADER
- 크루 상태 = WAITING

**Request Body**
```json
{
  "title": "수정된 제목",           // optional
  "goal": "수정된 목표",            // optional
  "verificationContent": "수정된 인증내용"  // optional
}
```

**비즈니스 규칙**
- 3개 필드 모두 optional (부분 수정 가능, PATCH 시맨틱)
- 최소 1개 이상 필드가 있어야 함 (빈 body 거부)
- 빈 문자열("") 또는 공백만 있는 값은 거부

**응답**
- `200 OK` — 수정된 크루 정보 반환
- `400 Bad Request` — 수정할 필드가 없음 / 유효성 실패
- `403 Forbidden` — 크루장이 아님
- `409 Conflict` — 크루가 WAITING 상태가 아님

---

### 2. 크루 삭제 — `DELETE /api/crews/{crewId}`

**요청 조건**
- 인증 필수 (JWT)
- 요청자 = 해당 크루의 LEADER
- 크루 상태 = WAITING
- crew_member 수 = 1 (LEADER 본인만 존재)

**비즈니스 규칙**
- hard delete: Crew 엔티티 + crew_member(리더 본인) 레코드 DB에서 삭제
- LEADER 외 크루원이 1명이라도 있으면 삭제 불가 → 409 응답
- 삭제 시 관련 데이터 cascade 확인 (S3에 업로드된 크루 이미지 등 있으면 정리)

**응답**
- `204 No Content` — 삭제 성공
- `403 Forbidden` — 크루장이 아님
- `409 Conflict` — 크루가 WAITING 상태가 아님 / 크루원이 존재함

---

### 3. 크루 탈퇴 — `DELETE /api/crews/{crewId}/members/me`

**요청 조건**
- 인증 필수 (JWT)
- 요청자 ≠ 해당 크루의 LEADER
- 크루 상태 = WAITING

**비즈니스 규칙**
- crew_member 테이블에서 해당 유저의 레코드 삭제
- LEADER는 탈퇴 불가 (크루 삭제를 사용해야 함)
- 이미 탈퇴한 상태(crew_member 레코드 없음)면 404

**응답**
- `204 No Content` — 탈퇴 성공
- `403 Forbidden` — LEADER는 탈퇴 불가 (크루 삭제를 이용하세요)
- `404 Not Found` — 해당 크루의 멤버가 아님
- `409 Conflict` — 크루가 WAITING 상태가 아님

---

## 🛡️ 공통 검증 로직 (Service 레이어)

```
validateCrewOwnership(crewId, userId, requiredRole)
  → Crew 조회 (없으면 404)
  → crew_member에서 해당 유저의 role 확인
  → role이 requiredRole과 일치하는지 검증

validateCrewStatus(crew, expectedStatus)
  → crew.status != expectedStatus → 409 Conflict
```

> 기존에 비슷한 검증 로직이 있다면 재사용할 것. 없으면 CrewValidator 또는 서비스 메서드로 추출.

---

## 🧪 테스트 시나리오

### 크루 수정
- ✅ LEADER가 WAITING 상태 크루의 제목만 수정 → 200
- ✅ LEADER가 3개 필드 모두 수정 → 200
- ❌ MEMBER가 수정 시도 → 403
- ❌ ACTIVE 상태 크루 수정 시도 → 409
- ❌ 빈 body로 수정 시도 → 400

### 크루 삭제
- ✅ LEADER가 WAITING + 본인만 있는 크루 삭제 → 204
- ❌ LEADER가 WAITING + 크루원 있는 크루 삭제 시도 → 409
- ❌ MEMBER가 삭제 시도 → 403
- ❌ ACTIVE 상태 크루 삭제 시도 → 409

### 크루 탈퇴
- ✅ MEMBER가 WAITING 크루에서 탈퇴 → 204
- ❌ LEADER가 탈퇴 시도 → 403
- ❌ ACTIVE 상태 크루에서 탈퇴 시도 → 409
- ❌ 소속되지 않은 크루에서 탈퇴 시도 → 404

---

## 📂 예상 작업 파일

```
src/main/java/.../crew/
├── controller/CrewController.java      ← 엔드포인트 추가
├── service/CrewService.java            ← 비즈니스 로직
├── dto/CrewUpdateRequest.java          ← 수정 요청 DTO (새로 생성)
├── domain/Crew.java                    ← update 메서드 추가
└── repository/CrewMemberRepository.java ← 멤버 수 조회 쿼리 추가

src/test/java/.../crew/
├── controller/CrewControllerTest.java
└── service/CrewServiceTest.java
```

---

## ⚠️ 주의사항

1. **동시성**: 삭제/탈퇴 시 동시 요청으로 인한 race condition 고려
   - 삭제 전 멤버 수 조회 → 삭제 사이에 가입이 일어날 수 있음
   - 비관적 락 또는 트랜잭션 격리 수준 검토
2. **PATCH vs PUT**: 부분 수정이므로 PATCH 사용. null인 필드는 수정하지 않는 로직 필요
3. **cascade 확인**: Crew 삭제 시 crew_member, 관련 verification 등 FK 관계 확인
4. **응답 일관성**: 기존 API의 에러 응답 포맷(ErrorResponse 등)과 일치시킬 것

---

## 🔗 작업 완료 후

- [ ] API 스펙 확정 후 **프론트엔드 작업 지시서** 작성 예정
- [ ] CLAUDE.md에 새 엔드포인트 반영
- [ ] debugging-log.md에 설계 판단 기록 (hard delete 선택 이유 등)
