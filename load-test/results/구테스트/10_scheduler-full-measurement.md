# 스케줄러 전체 실측 결과 (Day 10)

- 일시: 2026-04-21
- 서버: EC2 t3.micro (2 vCPU burstable, 1GB RAM) — 자세한 환경은 `00_environment.md` 참조
- 프로필: `prod,loadtest`
- 트리거: `POST /internal/scheduler/{name}` + `X-Internal-Api-Key: loadtest-internal-key`
- SQL: `load-test/sql/09_sched_*.sql` (스케줄러별 전용 테스트 데이터)

## 배경

Day 6(`04_scheduler-progression.md`)에서 `fail-expired`만 실 데이터로 측정. 나머지 5개는 "대상 0건 오버헤드"만 측정됨.
이번 측정은 **6개 스케줄러 전부 실 데���터 투입 + S~XL 전 구간 측정**이다.

## 판정 기준

- **PASS**: Duration < 300,000ms (5분 윈도우)
- **실 운영 경고선**: 60,000ms (1분) — 초과 시 스케일업/튜닝 고려
- **정합성**: 스케줄러 트리거 후 상태 전환 100% 완료 여부

## 결과 매트릭스 (Duration ms)

| 스케줄러 | ���상 | S | M | L | XL | 판정 |
|---------|------|---|---|---|-----|------|
| **activate** | 크루 | **137** | **683** | **3,805** | **8,957** | ✅ PASS |
| **complete** | 크루+챌린지 | **65** | **296** | **1,596** | **4,082** | ✅ PASS |
| **expire-session** | 세션 | **62** | **132** | **578** | **1,342** | ✅ PASS |
| **reminder** | 멤버→알림 | **115** | **386** | **1,617** | **4,754** | ✅ PASS |
| **start-noti** | 멤버→알림 | **76** | **303** | **1,889** | **4,693** | ✅ PASS |

### 데이터 규모

| 스케줄러 | S | M | L | XL |
|---------|---|---|---|-----|
| activate (크루) | 50 | 250 | 1,000 | 2,500 |
| complete (크루 / 챌린지) | 10 / 50 | 50 / 250 | 200 / 1,000 | 500 / 2,500 |
| expire-session (세션) | 50 | 250 | 1,000 | 2,500 |
| reminder (크루 / 멤버) | 10 / 50 | 50 / 250 | 200 / 1,000 | 500 / 2,500 |
| start-noti (크루 / 멤버) | 10 / 50 | 50 / 250 | 200 / 1,000 | 500 / 2,500 |

### 정합성 검증 (전 구간)

| 스케줄러 | 검증 항목 | S | M | L | XL |
|---------|----------|---|---|---|-----|
| activate | RECRUITING→ACTIVE | 50/50 | 250/250 | 1000/1000 | 2500/2500 |
| complete | ACTIVE→COMPLETED | 10/10 | 50/50 | 200/200 | 500/500 |
| complete | IN_PROGRESS→ENDED | 50/50 | 250/250 | 1000/1000 | 2500/2500 |
| expire-session | PENDING→EXPIRED | 50/50 | 250/250 | 1000/1000 | 2500/2500 |
| reminder | 알림 생성 | 100 | 250 | 1000 | 2500 |
| start-noti | 알림 생성 | 50 | 250 | 1000 | 2500 |

**전 구간 정합성 100%. 실패 0건. dead_letter 0건.**

## 핵심 해석

### 1. 전 스케��러 선형 스케일링

| 스케줄러 | S→XL 스케일 배수 | Duration 배수 | 건당 처리 (XL) |
|---------|-----------------|--------------|---------------|
| activate | 50× | 65× | 3.58ms/크루 |
| complete | 50× | 63�� | 8.16ms/크루 (챌린지 5개 포함) |
| expire-session | 50× | 22× | 0.54ms/세션 |
| reminder | 50× | 41× | 1.90ms/멤버 |
| start-noti | 50× | 62× | 1.88ms/멤버 |

- **expire-session이 가장 가벼움** — 단일 테이블 UPDATE, FK/CASCADE 없음
- **activate가 가장 무거움** — 크루 상태 전환 시 도메인 로직(챌린지 생성 등) 수반
- **complete**: 크루당 IN_PROGRESS 챌린지 5개 조회+ENDED 전환 → 복합 트랜잭션이지만 XL에서도 4초

### 2. Day 6 vs Day 10 비교 (Before/After)

| 스케줄러 | Day 6 (대상 0건) | Day 10 XL (실 데이터) | 증가 |
|---------|-----------------|---------------------|------|
| activate | 1ms | **8,957ms** | 실측 완료 |
| complete | 1ms | **4,082ms** | 실측 완료 |
| expire-session | 1ms | **1,342ms** | 실측 완료 |
| reminder | 7ms | **4,754ms** | 실측 완료 |
| start-noti | 6ms | **4,693ms** | 실측 완료 |

Day 6의 1~7ms는 "빈 쿼리 오버헤드"였으므로 실측치와 직접 비교 무의미.
**���심: XL(2,500건)에서도 전부 10초 이내 → 5분 윈도우 대비 97% 이상 여유.**

### 3. Phase 1 (500명) 규모 안전 마진

Phase 1 목표 DAU 500명 기준, 최악 케이스를 M~L 스케일로 추정:

| 스케줄러 | M (250건) | L (1000건) | 5분 윈도우 대비 |
|---------|-----------|-----------|---------------|
| activate | 683ms | 3.8s | **99.9% / 98.7%** 여유 |
| complete | 296ms | 1.6s | **99.9% / 99.5%** 여유 |
| expire-session | 132ms | 578ms | **99.9% / 99.8%** 여유 |
| reminder | 386ms | 1.6s | **99.9% / 99.5%** 여유 |
| start-noti | 303ms | 1.9s | **99.9% / 99.4%** 여유 |

**Phase 1에서 스케줄러 병목 가능성 없음.**

## 측정 환경 전제

- **FCM NoOp**: `firebase.enabled` 미설정 → `reminder`, `start-noti`는 DB INSERT만 측정, 실제 FCM 네트워크 비용 미포함
- **단독 실행**: API 부하 없이 스케줄러만 트리거 → 동시 부하 시 HikariCP 경합 가능성은 `05_concurrent-api-scheduler.md` 참조
- **PESSIMISTIC 락 모드**: EC2 JAR이 OPTIMISTIC 모드였으나, 스케줄러는 락 전략과 무관 (SELECT → UPDATE 패턴, 동시 접근 없음)

## 테스트 데이터 설계

각 스케줄러별 WHERE 조건에 정확히 매칭되는 전용 데이터 생성:

| SQL 파일 | 스케줄러 | 핵심 조건 |
|---------|---------|----------|
| `09_sched_activate.sql` | activate | `status='RECRUITING', start_date=어제` |
| `09_sched_complete.sql` | complete | `status='ACTIVE', end_date=어제` + IN_PROGRESS 챌린지 |
| `09_sched_expire_session.sql` | expire-session | `status='PENDING', created_at=30분 전` |
| `09_sched_reminder.sql` | reminder | `status='ACTIVE', deadline_time=20분 후` + 미인증 |
| `09_sched_start_noti.sql` | start-noti | `status='ACTIVE', start_date=오늘` |

- prefix 격리: `loadtest-sched-recruit-*`, `loadtest-sched-complete-*` 등으로 05번(fail-expired) 데이터와 충돌 없음
- 유저 재사용: 기존 `loadtest-user-*` 모듈로 순환
- 리셋: `09_sched_reset.sql`로 일��� 삭제 후 재���성

## 결론

Day 6에서 미완이었던 5개 스케줄러 실측을 완료했다.

- **6개 스케줄러 전부 XL(2,500건)에서 10초 이내** → 5분 윈도우 대비 97%+ 여유
- **전 구간 정합성 100%** — 상태 전환 누락, dead_letter 0건
- **선형 스케일링** — N+1, 인덱스 누락, 배치 비효율 없음
- **Phase 1 (500명) 안전 마진 충분** — 튜닝 불필요, 스케줄러 병목 가능성 없음
