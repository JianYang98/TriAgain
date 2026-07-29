# 부하테스트 Day 1~8 회고 & 다음 계획

> 최종 갱신: 2026-04-20
> 대상 브랜치: `feat/load-test`
> 인사이트 정리: `load-test/results/08_insights.md`
> 결과 문서: `load-test/results/00~07_*.md`

---

# Part 1. Day 1~8 회고

## 1.1 이력서 한 줄 문구 (Day 8 최종 → **2026-07-29 재정정**)

> "EC2 t3.micro 단일 노드에서 읽기 **912 req/s**(GET 전용, 2분 지속) + 쓰기 **포화 처리량 약 480~490/s**(15초 구간 실측, VU 30~150 평탄) 실측, 동시 사용자 250명까지 서버 에러 0%. SELECT FOR UPDATE 비관적 락으로 300명 동시 가입에서도 정원 초과 0건 — 데이터 정합성 100% 증명"

<details>
<summary>구 문구 (인용 금지)</summary>

> ~~"…읽기 912 req/s + 쓰기 490 TPS 실측…"~~ — 쓰기 수치의 측정 창(15초) 조건이 빠져
> 지속 처리량으로 오독될 수 있음. 수치 자체는 유효. 상세: `results/구테스트/06_write-heavy.md` 정정 배너.

</details>

면접 대비:
- **(B) 방법론**: `http_req_failed` vs `checks_failed` 판정 기준 재정의 스토리
- **(C) 병목 분석**: HikariCP Pending 140, GC Pause 4.6초, CPU 85% 분리
- **(D) 동시성**: 비관적 락 VU 300까지 정합성 + 스케줄러 중복 실행 발견

---

## 1.2 블로그 글감

### 메인 (확정) — 다른 세션에서 초안 작성 예정
**"부하테스트가 FAIL인데 서버는 PASS였다 — `http_req_failed` vs `checks_failed`"**
- 구조/뽑힌 근거는 메모리 `project_blog_ideas.md` 확정본 참조
- 핵심 서사: Day 1 FAIL 6.82% → Day 2 판정 기준 재정의 → 서버 에러 실제 0%
- 부록으로 "JVM warm-up 전후 p95 3배 차이" 사례 편입

### 후보 1 — "기본값의 역설 (HikariCP pool=10)"
> "Spring Boot 기본값 HikariCP pool=10이 충분했다 — VU 300 + 스케줄러 동시 부하에서도 timeout 0회"

- **훅**: 흔한 "성능 튜닝 = 기본값 바꾸기" 통념 깨기
- **스토리**: Day 1~6 전 구간 `hikaricp_connections_timeout_total = 0`. pool을 늘리는 대신 CPU/쿼리 튜닝이 실제 병목 해결책
- **독자 포지셔닝**: "DB 커넥션 풀부터 손대는" 백엔드 개발자. Phase별 튜닝 우선순위 판단 도움
- **단점**: 메인보다 임팩트 약함 (드라마틱한 사건 없음). 단, 병목 개선 재측정(Part 2.2) 이후 "Before/After" 수치 확보되면 강해짐

### 후보 2 — "환경을 내가 몰랐다 (t2 vs t3)"
> "부하테스트 6일을 t2.micro로 돌리고 있다고 믿었다 — 실제는 t3.micro, 해석이 뒤집혔다"

- **훅**: "측정 결과보다 측정 환경을 먼저 의심하라"
- **스토리**: 핸드오프 문서에 `t2.micro / 1 vCPU`로 박제 → Day 6에 IMDS 실측 `t3.micro / 2 vCPU burstable`. TPS 920 해석 자체가 달라짐 (1 vCPU 포화 한계 → 2 vCPU + 크레딧 영역)
- **독자 포지셔닝**: 측정 문화/엔지니어링 규율에 관심 있는 독자. 문서화의 함정
- **편입 가능성**: 메인 글 부록으로 넣거나 별도 짧은 글

---

## 1.3 "부하테스트는 X다" — 6일간 배운 5가지

### 1) 부하테스트는 **"재현 가능한 환경 준비"가 8할**이다
- Day 1~2 통틀어 실측보다 데이터 세팅/정리/판정 기준 확립에 더 많은 시간 씀
- 오염 사례 2개: PHOTO 20% 섞임, duplicate 409 누적
- 재측정 가능하게 리셋 스크립트(`08_reset_api_verifications.sql`) 확보하자마자 Day 2 속도 급상승

### 2) 부하테스트는 **"판정 기준 선언"부터**다
- `http_req_failed` vs `checks_failed` — 같은 측정 데이터가 판정 기준에 따라 FAIL/PASS 뒤집힘
- 도구(k6)가 주는 기본값을 그대로 쓰면 비즈니스 맥락(409 정상 거절)이 반영 안 됨
- 측정 전에 "우리 도메인에서 실패가 뭔가"를 먼저 정의

### 3) 부하테스트는 **"도구가 아니라 해석"**이다
- p95 154ms 하나로 끝나는 게 아님. 같은 수치여도:
  - VU 고정 측정인지 ramp-up 포함 측정인지
  - warm-up 한 서버인지 콜드 스타트인지
  - 오염 데이터 포함인지 깨끗한지
- "숫자를 낳는 과정"을 설명할 수 있어야 그 숫자에 권위가 붙음

### 4) 부하테스트는 **"경영 의사결정의 근거"**다
- HikariCP 기본값 OK → 이 규모에선 **튜닝 공수 아끼고 기능에 쓰자** 라는 의사결정 가능
- max_connections=79 vs pool=10 → 수평 확장 시 7 인스턴스까지 RDS 변경 없이 가능
- 수치가 팀 리소스 배분을 바꾼다

### 5) 부하테스트는 **"내가 뭘 모르는지"** 를 알려준다
- Day 6 전까지 EC2 타입을 6일 내내 오인하고 있었음
- Pending 76, GC Pause 6.8초 같은 관측값은 "왜?"를 쫓아가다 보면 **JVM/HikariCP/burstable 크레딧** 공부 구멍이 드러남 (→ Part 2.1)
- 측정은 끝이 아니라 학습 로드맵의 시작점

---

# Part 2. 다음에 해야 할 것 (우선순위 순)

## 2.1 Top-down 공부 계획 — 측정 결과를 **이해**하기 위한 학습 로드맵

측정은 끝났다. 이제 **측정값이 왜 그렇게 나왔는지 설명 가능해지는 것**이 목표. 우선순위는 "병목 해석에 직접 연관된 주제 > 주변 지식" 순.

### ① HikariCP 동작 원리 — Pending이 76까지 간 이유
- **궁금증**: pool=10인데 Pending이 76까지 튄 순간은 어떻게 생겼나? connection-timeout 30초 안에 어떻게 흡수됐나? acquireRetry 내부 로직?
- **읽을 자료**:
  - **공식 wiki — "About Pool Sizing"** by Brett Wooldridge (HikariCP 저자): <https://github.com/brettwooldridge/HikariCP/wiki/About-Pool-Sizing>
    - 핵심 공식: `connections = ((core_count * 2) + effective_spindle_count)` — 왜 pool을 크게 잡는 게 오히려 느려지는가
  - **HikariCP "Down the Rabbit Hole"** 기술 분석 (공식 wiki) — 내부 구조
  - 한국어 보조: 우아한형제들 기술블로그의 HikariCP 글 (`https://techblog.woowahan.com`)
- **예상 소요 시간**: **2~3시간** (위 두 문서 정독 + JMX MBean 실습)
- **배치**: 가장 먼저 할 것. Part 2.2 튜닝 판단 근거가 여기서 나옴

### ② JVM GC 튜닝 기초 — Pause 6.8초의 의미
- **궁금증**: `-Xmx512m` 에서 Pause 6.8초가 정말 최악 값인가? 평균 pause는? G1GC vs ParallelGC 선택 기준? Eden/Tenured 비율 조정으로 튜닝 가능한가?
- **읽을 자료**:
  - **책: "Java Performance" (Scott Oaks, O'Reilly 2nd ed)** 5~7장 (GC 기초 + G1)
    - 한국어 대안: 이상민 저 "자바 성능 튜닝 이야기" (입문용)
  - Oracle 공식 **"HotSpot Virtual Machine Garbage Collection Tuning Guide"** (Java 17 기준)
- **예상 소요 시간**: **반나절~하루 (4~6시간)** — 책 1~2 챕터 정독, 실습 포함
- **배치**: ①HikariCP 다음. Heap 튜닝 판단 근거

### ③ t3/t4g Burstable Credit 시스템
- **궁금증**: Day 6 측정 중 크레딧이 언제 얼마나 소진됐나? baseline 10% CPU로 떨어지면 TPS가 어떻게 바뀌나? Unlimited 모드로 전환해야 하나?
- **읽을 자료**:
  - AWS 공식 "**Burstable performance instances**" 가이드 (CPU credits and baseline utilization)
  - AWS 공식 "**CPUCreditBalance**" CloudWatch 지표 설명
- **예상 소요 시간**: **1시간** (공식 docs 정독)
- **배치**: ② 이후. 장기 부하 테스트(Future Work) 근거

### ④ PostgreSQL max_connections × HikariCP Pool 관계
- **궁금증**: `max_connections=79` 인데 단일 앱이 pool=10, 수평 확장 N개면 어디까지 안전한가? superuser_reserved_connections도 감안해야?
- **읽을 자료**:
  - PostgreSQL 공식 docs "**Connections and Authentication**" (`max_connections`, `superuser_reserved_connections`)
  - HikariCP wiki "About Pool Sizing"과 교차 (①에서 이미 읽음)
  - 블로그: Percona — "**Scaling PostgreSQL with PgBouncer**" (pool 앞단 pooler 언제 필요한가)
- **예상 소요 시간**: **1~2시간**
- **배치**: ①,② 이후. 실무에서 수평 확장 검토 시 바로 쓰임

### 공부 총 소요 시간 합계: **약 8~12시간 (2~3 세션)**

---

## 2.2 병목 개선 후 재측정 — **진짜 이력서 골드 자료**

### 관측된 병목 3개 (Day 1~6 수치)

| 병목 | 관측 수치 | 발생 시점 |
|------|----------|----------|
| HikariCP Pending | **최대 76** | Day 1 피크 구간(VU ramp-up 시) 관측 |
| GC Pause | **최대 6.8초** | Day 1~2 장시간 구간 관측 |
| CPU 사용률 | **100%** | Day 1~2 포화점(VU 50+) 이후 지속 |

> 주의: Pending 76 / Pause 6.8초는 로컬 Grafana 관측값. 수치 근거는 해당 세션 메모/기억 기반. 재측정 전에 **각 수치를 raw 로그/Grafana CSV 내보내기로 재확보**하는 게 좋음.

### 튜닝 계획 (가설 + 기대 효과)

| # | 대상 | Before | After (가설) | 기대 효과 | 리스크 |
|---|------|--------|-------------|-----------|-------|
| 1 | **HikariCP pool** | 10 (Boot 기본) | **20 or 30** (`spring.datasource.hikari.maximum-pool-size`) | Pending 감소. RDS `max_connections=79` 대비 여유 있음 | 이미 timeout=0 → 효과 제한적일 가능성. 실제 병목은 CPU/GC일 수도 |
| 2 | **JVM Heap** | `-Xmx512m` | **`-Xmx768m` + `-XX:+UseG1GC -XX:MaxGCPauseMillis=200`** | Pause 6.8초 → 1초 미만 기대 | t3.micro RAM 916Mi → 768m + non-heap 고려하면 OOM 리스크. 모니터링 필수 |
| 3 | **인스턴스 스케일업** | t3.micro (2 vCPU burstable) | **t3.small (2 vCPU, 2GB) 또는 t3.medium** | CPU 100% 상한 해소 + 크레딧 여유 | 비용 ↑ (월 약 15~30 USD). Phase 1 규모에선 과투자 가능 |

### 재측정 프로토콜 (Before/After 비교 표 목표)

1. **Before 수치 확보** (재측정 전):
   - 같은 환경 (t3.micro, pool=10, Xmx512m) 에서 동일 시나리오 3개 다시 돌려 **Before 로그를 깨끗하게** 확보 — 필요 시
   - 시나리오: (a) 포화점 VU-고정 50, (b) 마감 피크(load-peak.js), (c) 동시 부하(concurrent-test.sh L)
2. **튜닝 적용**: 한 번에 하나씩 (변수 격리!). 추천 순서:
   - 먼저 **Heap + G1GC** (GC Pause가 가장 명백한 병목)
   - 다음 **HikariCP pool ↑** (GC 개선 후 Pending이 여전히 높으면)
   - 마지막 **인스턴스 스케일업** (앞선 튜닝으로도 CPU 100%가 안 풀리면)
3. **After 측정**: 동일 시나리오 3개 재실행
4. **문서 신설**: `load-test/results/07_before-after-tuning.md`
   - 각 튜닝 단계별 표 (p95, TPS, Pending max, Pause max, CPU avg)
   - Before vs After 증감율
5. **블로그 후보 1 완성** — "기본값 vs 튜닝" 스토리에 Before/After 수치 붙여서 설득력 급상승

### 예상 소요 시간: **반나절~하루 (4~6시간)**
- Before 재확보 1시간 (이전 데이터 신뢰 시 스킵 가능)
- 튜닝 1개씩 적용 + 재측정 각 1시간 × 3 = 3시간
- 문서 작성 1~2시간

---

## 2.3 Future Work (안 한 것들)

우선순위는 낮지만 언젠가 할 것들. **Part 2.2 튜닝 재측정이 먼저**.

### ① PHOTO 인증 파이프라인 벤치마크
- **배경**: Day 2에서 `02_crews.sql` PHOTO 20% → 전부 TEXT로 통일. PHOTO 경로(S3 presigned + UploadSession + Lambda + SSE)는 **지금까지 한 번도 측정 안 됨**
- **왜 중요**: 실 사용자 행동상 사진 인증이 상당 비중. write path 중 가장 I/O 무거운 경로
- **할 것**:
  - 별도 k6 시나리오 `write-photo.js` 신설 (presigned URL 발급 → S3 PUT → `/internal/upload-session/{id}/complete` 콜백 → SSE 수신)
  - 또는 S3 mock 어댑터로 loadtest 프로필에서 실 S3 비용 없이 경로만 검증
- **예상 소요 시간**: **하루 (6~8시간)** — 시나리오 설계 + S3 mock 고민 + 실행 + 문서화

### ② 5개 스케줄러 실제 데이터 추가 후 재측정
- **배경**: Day 6 `04_scheduler-progression.md` 에 한계로 명시됨. `activate-crews`, `complete-crews`, `send-reminders`, `crew-start-notifications`, `expire-upload-sessions` 는 테스트 데이터가 자기 대상 조건에 맞지 않아 "대상 0건 오버헤드"만 측정됨
- **할 것**:
  - 각 스케줄러별 대상 조건 맞는 SQL 파일 신설: `07_sched_activate_data.sql`, `07_sched_complete_data.sql`, `07_sched_reminder_data.sql` 등
  - `scheduler-progression.sh` 에 스케줄러별 pre-data 분기 추가 (또는 SQL 여러 개 선택 실행하도록 arg 추가)
  - 6 × 4 매트릭스 완전한 실측치로 채우기
- **예상 소요 시간**: **반나절 (3~4시간)** — SQL 설계가 대부분

### ③ t3 Burst Credit 장기 측정
- **배경**: Day 6 최장 피크가 7~8분. t3.micro 크레딧 소진은 지속 100% 시 30분~수 시간 단위. **장기 부하에서 TPS가 떨어지는 지점을 실측 못 함**
- **할 것**:
  - CloudWatch `CPUCreditBalance` + 앱 메트릭 TPS 타임라인 같이 캡처
  - load-peak 시나리오 30분 지속 + CPU 크레딧 소진 이후 동일 부하에서 TPS 몇까지 떨어지는지 측정
  - Unlimited 모드 ON/OFF 양쪽 비교 (비용 비교까지)
- **예상 소요 시간**: **2시간** (측정 대기 30~60분 포함, 실제 액티브 작업은 1시간 미만)

### ④ ~~시나리오 D (크루 참가 러시) — 동시성 검증~~ ✅ Day 8 완료
- VU 50~300 전 구간 `join_success=10` 정확 일치, 정합성 100%
- VU 200부터 Tomcat connection reset 발생 (락이 아닌 서버 연결 한계)
- 결과: `07_crew-rush.md`

### ⑤ 스케줄러 적체(backlog) 실험 — ✅ Day 8 완료
- fail-expired 2연속 트리거: 1차 2,500건 / 2차 2,400건 동시 처리
- 겹침 방지 없음 + @Version 없음 → 중복 처리 감지 불가 (last-write-wins)
- 현재 무해하나 부수 효과 추가 시 버그 → `fixedDelay` 전환 필요 (다음 세션)
- 결과: `07_crew-rush.md` 스케줄러 적체 실험 섹션

---

## 2.4 우선순위 요약 (시간 배분)

| # | 작업 | 예상 시간 | 카테고리 |
|---|------|----------|---------|
| 1 | 공부 ①HikariCP 동작 원리 | 2~3h | 학습 |
| 2 | 공부 ②JVM GC 튜닝 기초 | 4~6h | 학습 |
| 1 | ~~시나리오 D 실행~~ | ~~1h~~ | ✅ Day 8 완료 |
| 2 | ~~스케줄러 적체 실험~~ | ~~1h~~ | ✅ Day 8 완료 |
| 3 | `fixedRate` → `fixedDelay` 변경 | **10min** | 다음 세션 즉시 |
| 4 | 공부 ①HikariCP ②GC ③t3 ④Postgres | 8~12h | 학습 |
| 5 | **병목 개선 + 재측정 (Part 2.2)** | 4~6h | **최우선 실험** |
| 6 | 5개 스케줄러 데이터 추가 재측정 | 3~4h | Future work |
| 7 | t3 burst credit 장기 측정 | 2h | Future work |
| 8 | PHOTO 파이프라인 벤치마크 | 6~8h | Future work (가장 큼) |

### 추천 진행 순서
1. **다음 세션 시작 시** → `fixedDelay` 변경 (10분, 빠른 성취)
2. **반나절~하루** 집중 가능 시 → 공부 ①②③ → Part 2.2 튜닝 재측정
3. **블로그** → Part 2.2 끝나야 "Before/After" 수치로 임팩트 완성
4. 학기/마일스톤 끝 여유 시간 → Future Work

---

## 맺는 말

**8일간 쌓은 것**:
- 정량 수치: 읽기 912/s(2분 지속) + 쓰기 포화 약 480~490/s(15초 구간) + 동시성 정합성 100% — 이력서 자산
- 판정 기준 사건 (`http_req_failed` vs `checks_failed`) — 블로그 메인 스토리
- 동시성 검증: 비관적 락 VU 300까지 + 스케줄러 중복 실행 발견 — 실무 인사이트
- 환경 오인/정정 (t2 → t3) + TPS 이원화 (읽기/쓰기 분리) — 측정 규율
- 도구 체인 (k6 + Prometheus + Grafana + EC2 + RDS) — 실전 경험

**아직 없는 것**:
- `fixedDelay` 코드 수정 (10분, 다음 세션)
- Before/After 튜닝 수치 (Part 2.2)
- 장기/주변 시나리오 (Part 2.3)
- 학습 로드맵 이론적 뒷받침 (Part 2.1)

**인사이트 전체 정리: `load-test/results/08_insights.md` 참조.**
**다음 세션 시작할 때 이 문서 맨 위부터 다시 읽기**. 우선순위 표만 보면 됨.
