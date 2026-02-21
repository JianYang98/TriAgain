# 비즈니스 규칙 (Business Logic)

## 1. 핵심 기능 요구사항

### 1.1 챌린지 생성 및 참여

AS [매일 운동을 하는 습관을 들이고 싶은 직장인],
I want to [오픈크루를 모집하거나, 오픈된 크루에 참여하여 작심삼일 챌린지를 시작한다],
so that [함께 가볍게 시작한다]

**세부 요구사항:**
- 같은 목표를 가진 크루원 3~10명을 모집하여, 최소 인원이 차면 챌린지 시작
- 챌린지 생성 시 목표, 인증방법, 인증시간, 기간, 최소-최대인원 설정
- 시작일 D-1 전 크루원들에게 알림 전송
- 최소 크루원이 모이지 못 할 경우 혼자 할 것인지 선택 가능

### 1.2 초대 크루 (부가 옵션)

AS [친구와 함께하고 싶은 대학생],
I want to [친구만의 크루를 만든다 (초대코드를 입력한 친구들이 크루에 등록된다)],
so that [친구들과 함께 가볍게 시작한다]

**초대 코드 스펙:**
- 형식: 6자리 영숫자 (0/O/I/L 제외)
- 유효 기간: 크루 시작 전까지

### 1.3 일일 인증 및 연속성 추적

AS [작심삼일을 반복하는 대학생],
I want to [매일 나의 습관 실천을 사진/텍스트로 인증하고, 3일 연속 달성 여부를 확인한다],
so that [진행 상황이 명확히 보이고, 연속성을 지키는 동기가 된다]

**세부 요구사항:**
- 일일 인증: 사진 업로드 or 간단한 텍스트
- 인증 시간 제한: 해당 날짜 or 정해진 시간까지만 인증 가능
- 크루원 인증 현황 공유

### 1.4 실패 후 재시작

AS [3일 챌린지 도중 실패한 크루원],
I want to [현재 3일 챌린지는 종료되지만, 탈락이 아니라, 새로운 다음 3일 챌린지가 시작된다],
so that [실패가 끝이 아니라 새로운 시작으로 인식되어, 습관 형성을 지속할 수 있다]

### 1.5 알림 및 리마인더 시스템 (Phase 2)

AS [바쁜 직장인으로 앱을 깜빡하는 사람],
I want to [인증 마감 N시간 전에 푸시 알림을 받는다],
so that [깜빡해서 3일 연속이 끊기는 불상사를 방지할 수 있다]

> Phase 2에서 구현 예정

### 1.6 크루 내 상호 응원

AS [혼자서는 동기부여가 약한 직장인],
I want to [크루원들의 인증에 반응을 남긴다],
so that [누군가 나를 보고 있다는 느낌을 받아 동기부여를 받을 수 있다]

**세부 요구사항:**
- Phase 1: 좋아요
- Phase 2: 이모지 확장 검토 (확장 가능하게 설계)

---

## 2. 인증 방식 및 상태 정의

### 2.1 인증 방식

크루 생성 시 크루장이 선택한다.

| 모드 | 필수 | 선택 |
|------|------|------|
| 텍스트 인증 (TEXT) | 텍스트 | - |
| 사진 인증 (PHOTO) | 사진 | 텍스트 |

### 2.2 인증 업로드 흐름

**텍스트 인증:**
```
인증 버튼 클릭 → POST /verifications (텍스트 포함) → 인증 완료
```

**사진 인증:**
```
인증 버튼 클릭 → POST /upload-sessions (URL 발급)
→ S3 직접 업로드
→ POST /verifications (이미지 key + 텍스트 선택)
→ 인증 완료
```

### 2.3 상태 정의

**upload_session 상태:**

| 상태 | 의미 |
|------|------|
| PENDING | presignedUrl 발급, S3 업로드 대기 |
| COMPLETED | S3 업로드 완료 (verification 생성 가능) |
| EXPIRED | 시간 초과 / 만료 |

**verification 상태:**

| 상태 | 의미 |
|------|------|
| APPROVED | 정상 인증 (기본값) |
| REPORTED | 신고 접수됨 (3건 이상) |
| HIDDEN | 검토 중 숨김 처리 |
| REJECTED | 검토 후 반려됨 |

**핵심 규칙:**
- S3 업로드 성공 후에만 verification 생성 (선택 A)
- verification INSERT 성공 후에만 upload_session을 COMPLETED로 전환 (동일 트랜잭션)
- upload_session과 verification은 별도 API로 분리

### 2.4 마감 시간 기준

| 상황 | 처리 |
|------|------|
| 9:59 요청, 10:01 업로드 완료 | ✅ 인증 성공 (upload_session.requested_at 기준) |
| 9:59 요청, 업로드 안 함 | ⏰ EXPIRED 처리 |
| 10:01 요청 | ❌ 서버에서 마감 지남 → URL 발급 거부 |

- 인증 시간 기준: upload_session.requested_at (서버가 기록, 조작 불가)
- Grace Period: challenge.deadline + 버퍼 시간

---

## 3. 비기능 요구사항 (NFR)

### 3.1 인증 업로드 성공률 99% 이상 (Reliability)

- 사용자의 노력(인증)이 시스템 문제로 무효화되면 안 된다
- S3 Direct Upload(Pre-signed URL) 방식으로 업로드 경로 단순화
- 클라이언트에서 이미지 압축 (최대 해상도 1080px, 품질 60~75%, 목표 1MB 이하)
- 허용 확장자: jpg, jpeg, png, webp

### 3.2 피드 조회 응답 시간 300ms 이내 (Performance)

- DB 병목 방지를 위해 인덱스 최적화 + 페이지네이션 (20건)
- Phase 2에서 Redis 캐시 확장 가능하도록 사전 설계

### 3.3 인증 중복 0건 보장 (Consistency)

- 인증 데이터는 통계/랭킹/신뢰도 기반
- UNIQUE 제약 조건 + 멱등성 키 + 분산 락으로 보장

### 3.4 시스템 가용성 99% 이상 (Availability)

- 마감 시간대에 장애는 곧 인증 실패 → 서비스 신뢰도 하락
- Phase 1: 단일 서버, stateless 구조 유지

---

## 4. 엣지케이스

### 4.1 크루 정원 초과 참여

- **영향:** 공정성 붕괴
- **대응:** DB Constraint + SELECT FOR UPDATE + 트랜잭션 처리

### 4.2 마감 직전 동시 인증 폭주

- **시나리오:** 마감 5초 전 인증 폭풍으로 DB Connection Pool 부족
- **대응:**
  - 마감시간 1시간 전 알람
  - Grace Period: 마감 넘어도 5분간 인증 처리
  - Phase 2: Write Queue 도입 검토

### 4.3 S3 업로드 성공, DB 저장 실패

- **시나리오:** 클라이언트는 S3 업로드 성공했으나 /verifications 요청이 실패
- **대응:**
  - /verifications는 verification INSERT 성공 후에만 upload_session을 COMPLETED로 전환 (동일 트랜잭션)
  - 실패 시 upload_session은 PENDING 유지, 클라이언트는 Idempotency-Key로 재시도 가능
  - UNIQUE 충돌 등 영구적 오류는 즉시 실패 처리

### 4.4 신고 시스템 악용

- **시나리오:** 악의적 신고 (3건 이상)로 정상 인증이 REPORTED 전환
- **대응:**
  - Phase 1: 신고 접수 + 크루장 검토
  - 신고자 중복 체크 (1인 1신고)
  - 신고 이력 추적

---

## 5. Fallback 등급 (S3 장애 시 대응)

Circuit OPEN 시 단계별 기능 축소 전략.
조건: 사진 필수 규칙 유지

### Level 1 (Best Effort) — "잠깐 흔들림, 금방 회복"

**상황:** S3 일시 오류/지연

**흐름:**
1. POST /upload-sessions → upload_session = PENDING, presignedUrl 반환
2. Client → S3 PUT 일시 실패
3. UX: "인증 접수 완료! 이미지를 업로드 중이에요. 잠시만 기다려주세요"
4. Client가 앱 레벨에서 자동 재시도 (Exponential Backoff + Jitter, 3~5회)
5. S3 업로드 성공 → POST /verifications → verification 생성 (APPROVED)

**상태 흐름:** PENDING → COMPLETED

### Level 2 (Reduced Function) — "유예시간 제공"

**상황:** S3 장애 지속

**흐름:**
1. POST /upload-sessions → upload_session = PENDING
2. S3 업로드 계속 실패, 클라이언트 재시도 모두 실패
3. UX: "⚠️ 업로드가 지연되고 있어요. 오후 11시까지 사진을 추가해주세요"
4. 유예시간 내 재업로드 (필요하면 새 presignedUrl 재발급)
5. S3 성공 → POST /verifications → verification 생성 (APPROVED)

**유예 기준:** challenge.deadline + 1시간

**상태 흐름:** PENDING → COMPLETED

### Level 3 (Minimal) — "최소 안내 + 재시도 버튼"

**상황:** Circuit OPEN 장기 지속, S3 심각한 장애

**흐름:**
1. POST /upload-sessions → upload_session = PENDING
2. S3 업로드 계속 실패 (장애 지속)
3. 마감 이후에도 유예시간까지 재시도 가능하지만 계속 실패
4. 유예시간까지도 실패 → upload_session = EXPIRED, verification 생성 안 됨

**UX:** "❌ 지금 서버 문제로 사진 업로드가 안 되고 있습니다. 유예 시간까지 재시도 가능합니다 [지금 다시 업로드]"

**상태 흐름:** PENDING → EXPIRED

### Fallback 상태 요약

| Level | 상황 | upload_session | verification |
|-------|------|---------------|-------------|
| Level 1 | S3 일시 오류 | PENDING → COMPLETED | ✅ 생성 |
| Level 2 | S3 장애 지속 | PENDING → COMPLETED | ✅ 유예시간 내 생성 |
| Level 3 | S3 심각한 장애 | PENDING → EXPIRED | ❌ 생성 안 됨 |
