# 쓰기 TPS 병목 튜닝 계획

> **목표**: GC Pause 4.6초 → 1초 미만, 쓰기 TPS 490 → 개선 측정
> **기반 데이터**: `results/06_write-heavy.md`, `results/08_insights.md` 인사이트 5·6
> **환경**: EC2 t3.micro (2 vCPU burstable, 916 MiB) + RDS PostgreSQL 17.6 (db.t4g.micro)
> **상태**: 미착수

---

## 관측된 병목 (Day 7 쓰기 부하 기준)

| 메트릭 | 읽기 부하 (Day 2) | 쓰기 부하 (Day 7) | 비고 |
|--------|-----------------|-----------------|------|
| **TPS** | 912 req/s | **490 TPS** | 쓰기 = 읽기의 54% |
| **HikariCP Pending max** | 76 | **140** | 쓰기 시 2배 — 1 POST = 6-7 DB ops |
| **GC Pause max** | 6.8초 | **4.6초** | STW 동안 커넥션 점유 → Pending 급등 |
| **CPU max** | 100% | **85%** | 쓰기는 CPU보다 DB I/O 바운드 |
| **HikariCP timeout** | 0 | **0** | 전 구간 timeout 없음 |

**핵심 해석**: 쓰기 throughput의 병목은 CPU가 아니라 GC Pause + 커넥션 점유 시간.

---

## Step 0. 사전 작업 — fixedRate → fixedDelay

> 소요: 10분 | Go/No-go: 없음 (무조건 실행)

### 무엇을

| 파일 | 라인 | Before | After |
|------|------|--------|-------|
| `FailExpiredChallengesScheduler.java` | :40 | `@Scheduled(fixedRate = 300_000)` | `@Scheduled(fixedDelay = 300_000)` |
| `ExpireUploadSessionScheduler.java` | :36 | `@Scheduled(fixedRate = 300_000)` | `@Scheduled(fixedDelay = 300_000)` |

나머지 4개 스케줄러는 `cron` 기반이라 해당 없음.

### 왜

- `08_insights.md` 인사이트 4: 적체 시 2연속 트리거 발생, 2,400건 중복 처리
- `fixedRate` = 시작 기준 5분 → 처리가 5분 넘으면 즉시 재실행
- `fixedDelay` = 종료 기준 5분 → 겹침 원천 차단
- 현재는 멱등 UPDATE라 무해하지만, 알림/사이클 생성 붙으면 2중 실행 = 버그

### 검증

- `./gradlew compileJava` 통과
- 배포 후 스케줄러 로그에서 실행 간격 확인

---

## Step 1. GC 튜닝 — MaxGCPauseMillis (최우선)

> 소요: 1시간 (변경 + 재측정) | **가장 명백한 병목**

### Before

```bash
java -Xmx512m -jar ~/triagain-0.0.1-SNAPSHOT.jar \
  --spring.profiles.active=prod,loadtest
```

- GC: JDK 17 기본 = G1GC
- GC Pause max: **4.6초** (Day 7), **6.8초** (Day 2)
- Heap 사용: ~500MB 중 대부분 소진 → 빈번한 Full GC 추정

### After

```bash
java -Xmx640m \
  -XX:MaxGCPauseMillis=200 \
  -Xlog:gc*:file=/tmp/gc.log:time,uptime,level,tags \
  -jar ~/triagain-0.0.1-SNAPSHOT.jar \
  --spring.profiles.active=prod,loadtest
```

| 파라미터 | 값 | 근거 |
|---------|-----|------|
| `-Xmx640m` | 512 → 640 | t3.micro 916Mi - non-heap ~200MB = 안전선. 768m은 OOM 위험 |
| `-XX:MaxGCPauseMillis=200` | 200ms 목표 | G1GC 기본 200ms이지만 명시적 선언. Pause 4.6초 → 대폭 감소 기대 |
| `-Xlog:gc*` | GC 로그 | Pause 분포 확인용 — 측정 후 제거 가능 |

### 기대 효과

- GC Pause: 4.6초 → **200ms 이하** (목표)
- HikariCP Pending: GC STW 감소 → 커넥션 점유 시간 단축 → Pending 감소
- 쓰기 p95: 180ms → 개선 예상

### 리스크

- `MaxGCPauseMillis`는 "목표"이지 **보장이 아님** — 실제 분포는 GC 로그로 확인
- Xmx640m에서도 Heap 부족 시 Full GC 빈도 증가 가능 → Grafana JVM Heap 패널 감시

### Go/No-go

- **Go**: GC Pause max < **1초**이면 성공 → Step 2 조건부 평가
- **No-go**: OOM 발생 → Xmx 축소 후 재시도. Pause 변화 없으면 → GC 로그 분석 후 튜닝 조정

---

## Step 2. HikariCP Pool 확장 (조건부)

> 전제: Step 1 완료 후 Pending max > 50이면 실행

### 진입 조건

Step 1 GC 튜닝 후에도 `HikariCP Pending max > 50` → Pool이 실제 병목일 가능성

### Before

- `maximum-pool-size`: **10** (Spring Boot 기본, application.yml 미명시)
- `hikaricp_connections_timeout_total`: **0** (Day 1~8 전 구간)

### After

`application-prod.yml` 에 추가:

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
```

| 체크 | 값 |
|------|-----|
| RDS max_connections | 79 |
| pool=20 시 수평 확장 | 3 인스턴스까지 RDS 변경 없이 가능 |
| pool=30이면 | 2 인스턴스 한계 — 20으로 시작 |

### 기대 효과

- Pending: 140 → **50 이하** (기대)
- 쓰기 TPS: 490 → 550~600 (가설, 커넥션 대기 감소분)

### 리스크

- timeout=0이므로 pool이 **진짜 병목이 아닐 수 있음** (Pending이 높아도 대기 시간은 짧다)
- GC 튜닝 없이 pool만 올리면 GC pause 중 더 많은 커넥션이 잠기기만 함 → **반드시 Step 1 먼저**

### Go/No-go

- **Go**: Step 1 후 Pending max > 50 & p95 미개선
- **No-go**: Step 1만으로 Pending < 50이면 pool 유지 → **"기본값이 충분했다" = 블로그 후보 1 강화**

### 변수 격리 실험

```
pool=20 측정 → 효과 있으면 → pool=30도 시도
한 번에 하나만 변경
```

---

## Step 3. 인스턴스 스케일업 (최후 수단)

> 전제: Step 1+2 후에도 CPU 100% 지속

### 진입 조건

Step 1(GC) + Step 2(Pool) 적용 후에도 CPU 100%가 목표 시나리오에서 지속

### Before → After

| 항목 | t3.micro (현재) | t3.small | t3.medium |
|------|---------------|----------|-----------|
| vCPU | 2 (burstable) | 2 (burstable) | 2 (burstable) |
| RAM | 1 GB | **2 GB** | **4 GB** |
| 월 비용 | ~$8 | ~$15 | ~$30 |
| Xmx 가능 | 640m | **1.5g** | **3g** |

### 기대 효과

- RAM 여유 → Xmx 확대 → GC 빈도 감소 → TPS 향상
- Phase 1 (500명) 규모에서는 **과투자 가능성 높음**

### Go/No-go

- **Go**: Step 1+2 후에도 CPU 100% 지속 & TPS 개선 정체
- **No-go**: Step 1+2에서 목표 달성 시 불필요. Phase 1은 10배 여유 이미 확보

---

## 재측정 프로토콜

### 원칙

1. **변수 격리** — 한 번에 하나만 변경
2. **warm-up 필수** — 10 VU, 1분 선행 (인사이트 7: JVM cold start 3배 차이)
3. **동일 시나리오** — Before/After 비교 가능하도록

### 측정 시나리오 (3종 고정)

| # | 시나리오 | 스크립트 | 설정 |
|---|---------|---------|------|
| A | 쓰기 포화점 | `load-write-heavy.js` | VU 50, SCALE=XXL |
| B | 마감 피크 | `load-peak.js` | VU 50, 2분 |
| C | 동시성 | `crew-rush.js` | VU 100 |

### Before/After 비교 표 (템플릿)

| 튜닝 단계 | TPS (write) | p95 | GC Pause max | Pending max | CPU max |
|-----------|------------|-----|-------------|-------------|---------|
| **Before (현재)** | 490 | 180ms | 4.6초 | 140 | 85% |
| After Step 1 (GC) | ? | ? | ? | ? | ? |
| After Step 2 (Pool) | ? | ? | ? | ? | ? |
| After Step 3 (Scale) | ? | ? | ? | ? | ? |

### 결과 기록

- 이 문서의 Before/After 표를 직접 갱신
- 상세 로그: `results/raw/` 에 `tuning_step{N}_*.log` 패턴으로 저장
- Grafana 스크린샷: `results/screenshots/tuning_step{N}_*.png`

---

## 타임라인 요약

```
Day 9 시작 시:
  ├─ Step 0: fixedDelay 변경 (10분) ─── 무조건
  ├─ Step 1: GC 튜닝 + 재측정 (1시간) ─── 무조건
  │    └─ Go/No-go 평가: Pause < 1초?
  ├─ Step 2: Pool 확장 + 재측정 (1시간) ─── 조건부 (Pending > 50)
  │    └─ Go/No-go 평가: Pending 감소?
  └─ Step 3: 인스턴스 변경 (1시간) ─── 최후 수단 (CPU 100% 지속)
```

**예상 총 소요: 2~4시간** (Step 0~1 필수 + Step 2~3 조건부)
