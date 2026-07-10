# HANDOFF — 이력서 장점 카드 다듬기용 부하테스트 백그라운드

> 이 문서를 받은 에이전트에게: 사용자 이력서의 "메인 장점(40초)", "백업 장점(25초)", "6-1 락 후속 카드(conn_reset)" 문구를 다듬는 작업을 한다. 사실관계의 정본은 `load-test/results/` 아래 측정 리포트다. 추론·과장 금지, 모르는 수치는 절대 만들지 말 것.

## 0. 이력서 카드 요지 (검토 대상)

사용자가 작성한 메인 장점 카드의 핵심 서사:
1. TriAgain에서 처음에 **비관적 락**으로 동시성 처리
2. 부하테스트에서 **한계** 발견 (VU 200~300에서 connection reset)
3. **낙관적 락**도 구현해 Before/After 비교
4. **VU 높으면 낙관락, VU 낮으면 비관락**이 적합
5. **yml 플래그**로 운영 중 전환 가능하게 설계
6. 한 면만 보지 않고 끝까지 비교한 게 다음 작업의 자산

후속 카드(6-1)의 핵심:
- 비관락 = 행 락 + 트랜잭션 끝까지 점유 → 동시 요청 몰리면 HikariCP 풀 고갈 → conn_reset
- 낙관락 = version 체크 + 즉시 실패 + 재시도 → 커넥션 짧게 점유 → conn_reset 0

이 서사가 측정 데이터와 정확히 일치하는지, 표현이 모호하지 않은지가 검토 포인트다.

---

## 1. 환경 (실측, t3.micro 정정 완료)

| 항목 | 값 |
|------|-----|
| EC2 | **t3.micro** (2 vCPU burstable, 916 MiB) — IMDS 실측. 이전 "t2.micro" 기록은 오류 |
| RDS | db.t4g.micro (추정), PostgreSQL 17.6, max_connections=79 |
| JVM | OpenJDK Corretto 17.0.18, `-Xmx512m` |
| Spring Boot | 3.4, profile `prod,loadtest` |
| HikariCP | maximum-pool-size **10** (기본값) |
| Tomcat | threads.max 200, accept-count 100 (기본값) |
| 부하 도구 | k6 + Prometheus + Grafana |
| 측정 기간 | 8~9일, 시나리오 7종 |

출처: `load-test/results/00_environment.md`

---

## 2. 핵심 수치 (인용 시 그대로 써도 됨)

### 2-1. 처리량 (Day 1~7)
- **읽기 throughput**: 912 req/s (포화점 VU 50)
- **쓰기 TPS**: 490 TPS (POST /verifications 단독, 포화점 VU 30~50)
- **읽기 Breaking Point**: VU 250 (p95 639ms)
- **쓰기 Breaking Point**: VU 150 (p95 594ms)
- **서버 에러율**: 전 구간 **0%** (5xx 없음)
- Phase 1 목표(50 TPS) 대비 여유: 읽기 18배, 쓰기 10배

⚠️ "920 TPS"는 혼합 부하 `http_reqs/s`로, 99.7%가 GET 읽기였다. **쓰기 TPS로 인용하면 거짓**. 읽기/쓰기 이원화 보고가 정본 (Day 7 측정).

### 2-2. 비관적 락 단독 (Day 8, `crew-rush.js`, 정원 10명 크루)

| VU | join_success | join_full | conn_reset | p95 | 판정 |
|----|--------------|-----------|------------|-----|------|
| 50 | 10 | 40 | 0 | 175ms | PASS |
| 100 | 10 | 90 | 0 | 339ms | PASS |
| 200 | 10 | 171 | **19 (9.5%)** | 492ms | PARTIAL |
| 300 | 10 | 190 | **100 (33.3%)** | 683ms | FAIL |

핵심: 정합성은 VU 300까지 100% (정원 초과 0건). 그러나 VU 200부터 Tomcat connection reset 발생.

출처: `load-test/results/07_crew-rush.md`

### 2-3. 낙관적 락 Before/After (Day 9, 2026-04-20)

| VU | Before p95 (비관) | After p95 (낙관) | Before conn_reset | After conn_reset |
|----|-------------------|------------------|-------------------|------------------|
| 50 | 175ms | 478ms (+303ms) | 0 | 0 |
| 100 | 339ms | 670ms (+331ms) | 0 | 0 |
| 200 | 492ms | 849ms (+357ms) | **19** | **0** |
| 300 | 683ms | 1,260ms (+577ms) | **100** | **0** |

핵심:
- **정합성**: 양쪽 100%, join_success=10 정확 일치
- **응답 시간**: 낙관락이 전 구간 느림 (재시도 비용)
- **connection reset**: 낙관락 전 구간 0 (해소)
- VU 300 낙관락 p95 1,260ms → 1초 threshold FAIL (이력서에서 언급 안 해도 되지만, 면접관 깊은 질문 시 인정해야 함)

출처: `load-test/results/09_optimistic-lock-comparison.md`

### 2-4. 쓰기 부하의 진짜 병목 (Day 7)
- HikariCP **Pending max 140** (pool=10)
- **GC Pause max 4.6초** (Stop-the-World)
- CPU 85% (의외로 여유)
- 결론: pool이 아니라 **GC가 1순위 병목**. 흔한 "pool 늘리자"가 정답 아님

출처: `load-test/results/06_write-heavy.md`

---

## 3. 절대 헷갈리지 말 것 (사실관계 함정)

| 잘못된 표현 | 올바른 표현 | 이유 |
|--------------|---------------|------|
| "비관락이 커넥션 못 들고 있어서" | "비관락이 커넥션을 **오래** 들고 있어서" | 의미 정반대. SELECT FOR UPDATE는 트랜잭션 끝까지 행 락 점유 |
| "낙관락은 행을 잠근다" | "낙관락은 version 컬럼으로 충돌 감지" | 낙관락은 락을 잡지 않음. UPDATE 시 `WHERE version=?`로 확인 |
| "낙관락이 더 빠르다" | "낙관락은 p95가 비관락보다 **느리지만**, conn_reset이 0" | p95만 보면 비관락 승. 안정성에서 낙관락 승 |
| "TPS 920" | "읽기 912 req/s + 쓰기 490 TPS" | 920은 혼합 부하 http_reqs/s. 쓰기 TPS는 490이 정본 |
| "t2.micro" | "**t3.micro**" | 이전 기록 오류 정정 완료 (IMDS 실측) |
| "HikariCP가 병목" | "HikariCP **Pending**은 높았지만 timeout은 0. **GC**가 1순위 병목" | pool 자체가 막힌 적 없음. STW가 커넥션 회수를 막은 것 |

---

## 4. yml 플래그 전환 — 어떻게 구현했나

```yaml
triagain:
  crew:
    lock-strategy: PESSIMISTIC  # 또는 OPTIMISTIC
```

- `CrewLockProperties` (config bean)에서 enum 읽음
- `JoinCrewService`가 properties 분기로 `findByIdWithLock()` (비관) vs `findById()` + version save (낙관) 선택
- V20 마이그레이션으로 `crews.version BIGINT NOT NULL DEFAULT 0` 추가
- 낙관락 재시도: **MAX_RETRY = 3**, 소진 시 `CREW_JOIN_CONFLICT(409, CR023)` 반환
- 재배포 없이 config server / yml reload로 전환 가능

면접 깊은 질문 ("yml flag 어떻게 구현?")에 답할 수 있는 수준은 위까지. "Strategy 패턴 + Bean 분기"는 사용자 카드 표현이지만, 실제 코드는 properties 기반 분기에 가깝다. **이 부분은 사용자가 직접 코드와 대조해서 표현 다듬을 필요**.

출처: `feat/optimistic-lock` 브랜치 커밋 `cdd81c5`, `JoinCrewService.java`, `CrewLockProperties.java`

---

## 5. 면접관 후속 질문 대비 (사용자 카드에 이미 정리됨)

검토 시 사용자 카드의 후속 답변이 아래 사실과 일치하는지 확인:

- **Q: "비관락에서 왜 conn_reset?"**
  → 비관락은 트랜잭션 끝까지 행 락 점유 → Tomcat 스레드가 락 대기 중 반환 안 됨 → 200스레드 + accept-queue 100 소진 → VU 300에서 33%가 TCP 레벨 거부.
  주의: 사용자 카드는 "HikariCP 풀 고갈"이라 적었는데, **실측은 HikariCP timeout 0**이다. 정확히는 **Tomcat 스레드/연결 한계**가 직접 원인이다. (다만 비개발자 면접관 대상이면 "커넥션 풀" 표현이 통할 수 있음 — 사용자와 합의 필요)

- **Q: "비관 vs 낙관 어떻게 선택?"**
  → 충돌 빈도. 충돌 잦으면 비관 (재시도 비용 회피), 드물면 낙관 (락 대기 회피).

- **Q: "낙관락 재시도 무한 루프?"**
  → MAX_RETRY=3 제한, 소진 시 409 CREW_JOIN_CONFLICT 반환.

- **Q: "Phase별 적합성?"**
  → Phase 1(500명): 어느 쪽이든 OK. Phase 2(5000명): 낙관락. Phase 3(50000명+): Redis INCR.

---

## 6. 정본 문서 (검토 시 출처 확인)

| 주제 | 파일 |
|------|------|
| 환경 실측 | `load-test/results/00_environment.md` |
| 쓰기 TPS (490) | `load-test/results/06_write-heavy.md` |
| 비관락 VU 50~300 | `load-test/results/07_crew-rush.md` |
| **낙관 vs 비관 Before/After** | `load-test/results/09_optimistic-lock-comparison.md` |
| 통찰/판정 기준 재정의 | `load-test/results/08_insights.md` |
| 이미 정리된 면접 답변 | `load-test/INTERVIEW-PREP.md` (30초/2분/5분 + 숫자 퀵 레퍼런스) |

---

## 7. 검토 시 체크리스트

사용자 카드를 다듬을 때 아래를 검증:

1. [ ] **사실관계**: 모든 수치/표현이 위 정본과 일치하는가
2. [ ] **함정 표현** (§3 표): 6가지 함정 중 하나라도 들어있지 않은가
3. [ ] **시간**: 40초/25초/18초가 실제 읽었을 때 그 시간에 맞는가
4. [ ] **연결성**: "또 다른 장점은?" 트리거에 백업 카드(SQL 12,000줄 → 25모듈)가 자연스럽게 이어지는가
5. [ ] **비개발자 시나리오** 별도 버전이 기술 용어 빼고도 핵심 전달되는가
6. [ ] **약점 인정 라인**: VU 300에서 낙관락 p95 1.26초로 threshold FAIL 한 사실을 면접관이 파고들 때 답할 준비가 됐는가

검토 결과는 (1) "이대로 OK인 카드", (2) "이렇게 수정 권장" 두 묶음으로 정리해서 사용자에게 돌려준다. 사실관계 위반은 P0, 표현 다듬기는 P1로 우선순위 표시.
