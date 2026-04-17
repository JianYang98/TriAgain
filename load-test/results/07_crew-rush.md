# 크루 참가 러시 — 동시성/락 검증 (Day 8)

- 일시: 2026-04-17
- 서버: EC2 t3.micro (2 vCPU burstable, 1GB RAM)
- DB: RDS PostgreSQL 16, HikariCP pool 10
- 프로필: prod,loadtest
- 스크립트: `crew-rush.js` (POST /crews/{crewId}/join, per-vu-iterations)
- 대상 크루: `loadtest-rush-crew-1` (max_members=10, current_members=0)
- 동시성 제어: `@Lock(PESSIMISTIC_WRITE)` — `SELECT ... FOR UPDATE`

## 목표

50명이 정원 10명 크루에 **동시 참가** 시도.
- 정확히 **10명 성공(201)**, **40명 거절(409)**
- race condition으로 인한 정원 초과 **0건**
- `SELECT FOR UPDATE` 비관적 락이 동시 접근을 정확히 직렬화하는지 증명

## 판정 기준

| 메트릭 | PASS 조건 |
|--------|-----------|
| join_success | count == 10 |
| join_full | count == 40 |
| checks | 100% (201 or 409만) |
| 5xx | 0건 |
| p95 | <1000ms |

## 테스트 설계

```
executor: per-vu-iterations
vus: 50
iterations: 1 (VU당 딱 1번)
→ 총 50건 요청, 전부 동시 발사
```

**락 경합 흐름:**
1. 50 VU가 동시에 `POST /crews/loadtest-rush-crew-1/join` 호출
2. `JoinCrewService.joinCrew()` → `crewRepository.findByIdWithLock()` (SELECT FOR UPDATE)
3. 첫 번째 VU가 락 획득 → `crew.addMember()` → currentMembers 1→2→...→10
4. 11번째부터 `crew.isFull()` = true → `BusinessException(CREW_FULL)` → 409

## 결과 — 2회 실행

| 항목 | 1차 (12:59:31) | 2차 (13:00:13) |
|------|---------------|---------------|
| **join_success** | **10** | **10** |
| **join_full** | **40** | **40** |
| checks | 100% (50/50) | 100% (50/50) |
| 5xx | 0 | 0 |
| http_reqs | 50 | 50 |
| p50 | 164ms | 128ms |
| p90 | 211ms | 174ms |
| **p95** | **217ms** | **175ms** |
| max | 221ms | 180ms |
| 성공 응답 p95 | 118ms | 93ms |
| 총 소요 | 0.2s | 0.2s |
| DB current_members | 10/10 | 10/10 |

### 판정: PASS

- join_success == 10, join_full == 40 **2회 연속 정확히 일치**
- 정원 초과(race condition) **0건** — 비관적 락 정상 작동
- 5xx 0건, checks 100%
- p95 217ms (1차) / 175ms (2차) — threshold 1000ms 대비 여유

### 응답 시간 분석

- 성공(201) 응답 p95: ~118ms — 락 획득 + INSERT + UPDATE
- 실패(409) 응답: ~164ms — 락 대기 후 `isFull()` 체크만
- 실패가 성공보다 느린 이유: 락 대기 큐에서 순서를 기다린 후 거절당하기 때문
- 50 VU 동시 → 최대 대기 221ms = 약 4~5ms/건 × 50 = 직렬화 비용

## VU 스케일업 실험 (50 → 100 → 200 → 300)

VU 50에서 정합성 확인 후, 비관적 락의 한계점을 탐색하기 위해 VU를 증가시켰다.

| VU | join_success | join_full | conn_reset | checks | p50 | p95 | max | DB 정합성 | 판정 |
|----|-------------|-----------|------------|--------|-----|-----|-----|----------|------|
| 50 | **10** | 40 | 0 | 100% | 128ms | 175ms | 180ms | 10/10 | PASS |
| 100 | **10** | 90 | 0 | 100% | 237ms | 339ms | 348ms | 10/10 | PASS |
| 200 | **10** | 171 | **19** (9.5%) | 90.5% | 304ms | 492ms | 517ms | 10/10 | PARTIAL |
| 300 | **10** | 190 | **100** (33.3%) | 66.7% | 416ms | 683ms | 734ms | 10/10 | FAIL |

### 핵심 발견

1. **데이터 정합성 100%** — VU 300에서도 `join_success` 정확히 10, `current_members` 정확히 10. **비관적 락은 어떤 동시성에서도 정원 초과를 허용하지 않는다.**

2. **p95 선형 증가** — VU에 비례하여 직렬화 대기 시간 증가:
   - VU 50: 175ms → VU 100: 339ms → VU 200: 492ms → VU 300: 683ms
   - 약 **VU 당 ~2ms 추가** (직렬화 비용)

3. **VU 200부터 connection reset 발생** — 19건(9.5%). Tomcat 동시 연결 수용 한계.
   - VU 300에서는 100건(33.3%)으로 급증
   - 락까지 도달하기 전에 TCP 레벨에서 탈락 → 데이터 정합성에 영향 없음
   - 원인: Tomcat `server.tomcat.threads.max=200` + `accept-count=100` 기본값

4. **가설 검증 완료** — "인기 크루 오픈 러시" 시나리오에서:
   - 200명 동시: p95 492ms (Phase 2 전환 기준 근접)
   - 300명 동시: p95 683ms + 33% 연결 거부 (Phase 2 전환 필요)
   - 500명 동시 시 예상: p95 1초+ 및 연결 거부 50%+

### connection reset 분석

```
VU 200: 19/200 = 9.5% 연결 거부
VU 300: 100/300 = 33.3% 연결 거부

원인: Tomcat 동시 스레드 한계 (기본 200)
- 300 VU 동시 → 200 스레드 소진 → 나머지 100 거절(accept-queue 초과)
- 락 경합으로 스레드가 SELECT FOR UPDATE 대기 중 반환 못 함
- 결과: 서버는 죽지 않지만 클라이언트는 "연결 실패" 체험
```

## Grafana 스크린샷

| 패널 | 파일 |
|------|------|
| 전체 개요 | `screenshots/day8_crew-rush-full.png` |
| TPS | `screenshots/day8_crew-rush-tps.png` |
| HikariCP | `screenshots/day8_crew-rush-hikaricp.png` |
| GC | `screenshots/day8_crew-rush-gc.png` |

## 가설 시나리오 분석 — "인기 크루 오픈 러시"

### 가설

> 인플루언서가 정원 10명 크루를 개설하고, SNS 공지 후 **500명이 동시에 가입 시도**.

### 현재 구조에서 예상되는 문제

```
500명 → 같은 crew row에 SELECT FOR UPDATE
     → 완전 직렬화
     → 1명당 ~2ms × 499 = 약 1초 대기 (마지막 사용자)
     → 체감: "앱이 멈췄다"
```

**실측 기반 추정** (VU 스케일업 실험에서 확인):
- VU 200: p95 492ms + 9.5% 연결 거부
- VU 300: p95 683ms + 33.3% 연결 거부
- VU 500 추정: p95 **1초+** + 연결 거부 **50%+**
- Tomcat 스레드 200개 + 락 대기로 스레드 반환 지연 → 대량 연결 거부
- 데이터 정합성은 보장되지만, 사용자 이탈 발생

### 대응 전략 — Phase별 진화

#### Phase 1 (현재, 500명 규모): 비관적 락 유지

- 이 규모에서 "인기 크루 오픈 러시"는 아직 먼 시나리오
- 발생해도 10초 대기 수준 → **불편하지만 데이터는 안전**
- Day 8 실측: VU 50 p95 175ms, VU 100 p95 339ms — Phase 1 규모에서 충분히 빠름
- **현 단계 적정 기술**

#### Phase 2 (5,000명 규모): 낙관적 락 (`@Version` + 재시도)

```java
// Crew 엔티티에 @Version 추가
@Version
private Long version;

// Service에서 OptimisticLockException 시 재시도
@Retryable(maxAttempts = 5, backoff = @Backoff(delay = 50))
public JoinCrewResult joinCrew(JoinCrewCommand command) {
    Crew crew = crewRepository.findById(crewId); // 락 없이 조회
    crew.addMember(userId);
    crewRepository.save(crew); // version 충돌 시 예외 → 재시도
}
```

- 대기 없이 즉시 시도 → 충돌 시 재시도
- 정원 10명이면 충돌 확률 높아 재시도 3~5회 예상
- 평균 응답 **~100ms**로 개선 가능
- 단점: 재시도 폭주 시 DB 부하 증가

#### Phase 3 (50,000명+ 규모): Redis 선착순 카운팅

```
1. Redis INCR crew:{id}:count → 1ms 안에 성공/실패 판정
2. count ≤ 10 → 성공 응답 + DB INSERT 비동기 (SQS/이벤트)
3. count > 10 → 즉시 409 응답 (DB 접근 없음)
```

- 500명 동시 접근해도 전원 **1ms 안에 응답**
- DB 부하: 성공한 10명의 INSERT만 (비동기)
- 단점: Redis 장애 시 fallback 필요, 최종 일관성(eventual consistency)

### 전환 기준

| 트리거 | 조치 |
|--------|------|
| 동시 가입 VU **100+** 관측 (운영 메트릭) | Phase 2 검토 시작 |
| p95 **500ms 초과** | Phase 2 즉시 전환 |
| 동시 가입 VU **1,000+** 관측 | Phase 3 검토 시작 |
| p95 **2초 초과** 또는 timeout 발생 | Phase 3 즉시 전환 |

### 핵심 원칙

> **"Phase 1에서 오버엔지니어링하지 않는다."**
> 비관적 락은 VU 100까지 p95 339ms, 정합성 100% — Phase 1 목표(500명, 50 TPS)에 충분하다.
> VU 200에서 connection reset 시작, VU 300에서 33% 거부 — 이것이 전환 시점의 실측 근거.
> 전환은 운영 메트릭이 기준을 넘었을 때, 데이터 기반으로 결정한다.

---

## 스케줄러 적체(backlog) 실험

### 배경 — 무엇을 검증했나

fail-expired 스케줄러는 5분 주기(`fixedRate`)로 만료된 챌린지를 FAILED 처리한다.
**질문**: 처리가 5분을 초과하면? 다음 주기와 겹치면 어떻게 되는가?

### 사전 코드 분석

| 항목 | 값 | 위험도 |
|------|-----|--------|
| 타이밍 | `fixedRate = 300,000ms` (시작 기준 5분) | 겹침 가능 |
| 겹침 방지 | **없음** (SchedulerLock, ShedLock 없음) | 위험 |
| `challenge.fail()` | `IN_PROGRESS`가 아니면 예외 throw — **멱등적이지 않음** | 주의 |
| `@Version` | **없음** — 동시 UPDATE 충돌 감지 불가 | 위험 |
| ChunkProcessor | 청크 실패 → 개별 재시도 + dead_letters 저장 | 안전장치 |
| 스레드 풀 | 4개 (`spring.task.scheduling.pool.size=4`) | 제한적 |

### 실험 설정

- 데이터: XL 2,500건 만료 챌린지 (`loadtest-sched-*`, `IN_PROGRESS`, deadline 과거)
- 방법: `POST /internal/scheduler/fail-expired` 수동 트리거 **2연속** (100ms 간격)
- 목적: 1차 처리 중에 2차가 같은 데이터를 잡는지, 중복 처리가 발생하는지 확인

### 결과 (EC2 로그 실측)

```
16:43:24.519 [exec-431] 챌린지 실패 처리: 전체 2500건, 성공 2500건, 실패 0건
16:43:24.548 [exec-428] 챌린지 실패 처리: 전체 2400건, 성공 2400건, 실패 0건
```

| 실행 | 스레드 | 처리 건수 | 소요 시간 | dead_letters |
|------|--------|----------|----------|--------------|
| 1차 | exec-431 | **2,500건** 성공 | 1,532ms | 0 |
| 2차 | exec-428 | **2,400건** 성공 | 1,457ms | 0 |

- 합계: **4,900건** 처리 시도, 실제 데이터 **2,550건**
- 최종 DB: FAILED 2,550건 (정합성 OK)
- dead_letters: **0건** (예외 0)

### 분석 — 왜 이렇게 됐나

**2차가 2,400건을 잡은 이유:**
```
T+0ms:   1차 쿼리 → IN_PROGRESS 2,500건 SELECT
T+100ms: 2차 쿼리 → 1차가 ~100건 이미 FAILED 처리 → 2,400건 SELECT
T+1500ms: 1차 완료 (2,500건 UPDATE)
T+1550ms: 2차 완료 (2,400건 UPDATE)
→ 겹치는 ~2,400건은 두 트랜잭션이 모두 UPDATE SET status='FAILED' 실행
```

**예외가 0건인 이유:**
- `challenge.fail()`은 **자바 객체의 status**를 체크 (`this.status != IN_PROGRESS`)
- 각 트랜잭션이 **자체 영속성 컨텍스트**에서 `IN_PROGRESS`로 읽어온 객체에 `fail()` 호출
- JPA dirty checking → 각각 `UPDATE SET status='FAILED' WHERE id=?` 발행
- `@Version` 없으므로 두 UPDATE 모두 성공 (last-write-wins)
- 결과적으로 같은 row에 `SET status='FAILED'`를 2번 실행 — DB에서는 무해

### 위험도 평가

| 시나리오 | 현재 | 부수 효과 추가 시 |
|---------|------|-----------------|
| status 변경 | 무해 (멱등한 UPDATE) | 무해 |
| 알림 발송 | 비활성화 (TODO 상태) | **2중 알림 위험** |
| 새 사이클 생성 | 별도 스케줄러 | **2중 생성 위험** |
| 외부 API 호출 | 없음 | **2중 호출 위험** |

**현재는 안전하지만, 부수 효과가 붙는 순간 중복 실행이 버그가 된다.**

### 대응 전략

#### Phase 1 (즉시 가능): `fixedRate` → `fixedDelay` 변경

```java
// 변경 전
@Scheduled(fixedRate = 300_000)

// 변경 후
@Scheduled(fixedDelay = 300_000)
```

- `fixedDelay`: 이전 실행 **종료 후** 5분 대기 → 겹침 원천 차단
- 코드 수정 1줄, 다음 세션에서 처리 예정
- 단일 인스턴스에서 완전한 해결

#### Phase 2 (다중 인스턴스): ShedLock 도입

```java
@SchedulerLock(name = "fail-expired", lockAtLeastFor = "PT1M", lockAtMostFor = "PT10M")
@Scheduled(fixedDelay = 300_000)
```

- 분산 환경에서 하나의 인스턴스만 실행
- DB 기반 락 (ShedLock + PostgreSQL)
- EC2 오토스케일링 시 필수
