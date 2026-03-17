# 부하 테스트 타겟 API 추천

> Phase 1 목표 TPS 50 기준, 실사용 시 병목이 드러날 가능성이 높은 API 3개를 선정한다.
> 선정 기준: 트래픽 집중도, DB write 부하, 동시성/외부연동 병목 가능성.

---

## 추천 1: POST /verifications (인증 생성)

**왜 이 API인가:** 매일 deadlineTime(기본 23:59:59) 직전 5~10분에 전체 유저 트래픽이 집중되는 핵심 쓰기 API. DB write + 동시성 경합 + 복합 검증 로직이 모두 존재.

| 항목 | 내용 |
|------|------|
| 엔드포인트 | `POST /verifications` |
| 인증 | JWT 필수 (Bearer token) |
| 요청 바디 | `{ "crewId": "crew-uuid", "textContent": "오늘 운동 완료!" }` (텍스트 인증) |
|  | `{ "crewId": "crew-uuid", "uploadSessionId": 123 }` (사진 인증) |

**부하 취약 포인트:**

1. **DB write 집중** — verification INSERT + challenge UPDATE (completedDays 증가)가 매 요청마다 발생
2. **Unique constraint 경합** — `idx_verification_unique(user_id, crew_id, target_date)`로 중복 방지. 동시 요청 시 constraint violation 처리 부하
3. **복합 검증 로직** — 멤버십 확인 → challenge 조회/생성 → deadline 검증 → 중복 확인 → 인증 타입 검증 → write. 한 트랜잭션 안에서 여러 테이블 read + write
4. **마감시간 스파이크** — 500명 × 평균 3개 크루 = ~1,500건이 10분 안에 집중 → 순간 TPS 2.5 → 스파이크 시 5~10x

---

## 추천 2: POST /crews/join (크루 참여)

**왜 이 API인가:** `SELECT FOR UPDATE` 비관적 락을 사용하는 유일한 API. 동시 참여 시 lock 대기 시간, 정원 초과 방지 정확성을 검증할 수 있다.

| 항목 | 내용 |
|------|------|
| 엔드포인트 | `POST /crews/join` |
| 인증 | JWT 필수 (Bearer token) |
| 요청 바디 | `{ "inviteCode": "ABC123" }` |

**부하 취약 포인트:**

1. **PESSIMISTIC_WRITE 락 경합** — 같은 크루에 동시 참여 시 `SELECT FOR UPDATE`로 row lock 획득 대기. 10명이 동시에 요청하면 순차 처리 → 응답 시간 선형 증가
2. **Lock holding time** — inviteCode 조회(락 없음) → ID로 재조회(락 획득) → validate → addMember → save → commit(락 해제). 트랜잭션이 길수록 lock 대기 증가
3. **정원 초과 정확성** — maxMembers=10인 크루에 100명 동시 요청 → 정확히 10명만 성공해야 함. 부하 테스트로 데이터 무결성 검증 가능
4. **DB Connection Pool 점유** — 락 대기 중인 커넥션이 pool을 점유 → 다른 API 응답 지연 파급 효과

---

## 추천 3: POST /upload-sessions (업로드 세션 생성)

**왜 이 API인가:** 유일하게 외부 서비스(S3) 연동이 있는 쓰기 API. presignedUrl 생성 시 S3 SDK 호출이 포함되어 외부 의존성 병목을 발견할 수 있다.

| 항목 | 내용 |
|------|------|
| 엔드포인트 | `POST /upload-sessions` |
| 인증 | JWT 필수 (Bearer token) |
| 요청 바디 | `{ "crewId": "crew-uuid", "fileName": "photo.jpg", "fileType": "image/jpeg", "fileSize": 2048000 }` |

**부하 취약 포인트:**

1. **S3 Presigner 호출** — presignedUrl 생성은 네트워크 호출은 아니지만 S3Presigner 인스턴스의 서명 생성 연산이 동시에 몰리면 CPU 부하 발생 가능
2. **복합 검증 로직** — 크루 멤버십 + 크루 상태(ACTIVE) + 시작/종료일 + 마감시간 + 파일타입/크기 검증 → 여러 테이블 조회
3. **DB write** — upload_session INSERT + 상태 관리
4. **POST /verifications와 연쇄** — 사진 인증 시 upload-session → S3 업로드 → Lambda 콜백 → SSE → verification 생성의 전체 파이프라인 시작점. 이 API가 느려지면 전체 사진 인증 플로우가 지연

---

## 테스트 시나리오 요약

| 시나리오 | API | 부하 패턴 | 검증 포인트 |
|---------|-----|----------|------------|
| 마감시간 스파이크 | POST /verifications | 10분간 150~250 TPS ramp-up | p99 응답시간 < 1s, 중복 인증 0건 |
| 동시 크루 참여 | POST /crews/join | 100명 동시 요청 (같은 크루) | 정원 초과 0건, lock 대기시간 측정 |
| S3 연동 부하 | POST /upload-sessions | 50~100 TPS 지속 | S3 서명 생성 지연, Connection Pool 사용률 |

---

## 테스트 전 준비 사항

- **JWT 토큰 발급**: `POST /auth/test-login` (dev 환경 전용)으로 대량 유저 토큰 생성
- **테스트 데이터**: 크루 + 멤버 사전 세팅 필요 (크루 생성 → 멤버 가입)
- **DB Connection Pool**: HikariCP 기본값 확인 (default: 10)