# 11. 전략 A/B/C 비교 — 조건부 원자적 UPDATE (CONDITIONAL)

> **상태**: TBD — 결과 셀은 모두 «TBD — 사용자가 실측»  
> **목적**: feat/load-test 브랜치에서 CONDITIONAL 전략의 성능·정합성을 A/B 대비 실측으로 검증

---

## 측정 환경

환경은 `00_environment.md`의 07/09 측정 환경과 동일하게 고정할 것.  
환경이 다른 숫자는 같은 비교표에 섞지 말 것(섞으면 비교 무효).

| 항목 | 값 |
|------|-----|
| 서버 | «TBD — 사용자가 실측» |
| DB | «TBD» |
| JVM | «TBD» |
| k6 실행 위치 | «TBD» |

---

## 1. 정원 경합 (crew-rush) — 정원 10명, VU 50/100/200/300

### 측정 절차

```bash
# 1. CONDITIONAL 전략으로 서버 재기동
java -jar app.jar --triagain.crew.lock-strategy=CONDITIONAL

# 2. 데이터 초기화 (기존 rush reset SQL 재사용)
psql ... -f load-test/sql/07_rush_reset.sql

# 3. VU별 실행
k6 run --env BASE_URL=http://<SERVER> --env TARGET_VUS=50  load-test/k6/crew-rush.js
k6 run --env BASE_URL=http://<SERVER> --env TARGET_VUS=100 load-test/k6/crew-rush.js
k6 run --env BASE_URL=http://<SERVER> --env TARGET_VUS=200 load-test/k6/crew-rush.js
k6 run --env BASE_URL=http://<SERVER> --env TARGET_VUS=300 load-test/k6/crew-rush.js
```

### 결과 (정원 10명)

| VU | 전략 | join_success | join_full | p95 (ms) | conn_reset | 비고 |
|----|------|:---:|:---:|:---:|:---:|------|
| 50 | A (PESSIMISTIC) | 10 | 40 | «07 측정값» | «07 측정값» | 07_crew-rush.md 참조 |
| 50 | B (OPTIMISTIC) | 10 | 40 | «09 측정값» | 0 | 09_optimistic-lock-comparison.md 참조 |
| 50 | **C (CONDITIONAL)** | «TBD» | «TBD» | «TBD» | «TBD» | |
| 100 | A | 10 | 90 | «07» | «07» | |
| 100 | B | 10 | 90 | «09» | 0 | |
| 100 | **C** | «TBD» | «TBD» | «TBD» | «TBD» | |
| 200 | A | 10 | 190 | «07» | «07 conn_reset 발생» | |
| 200 | B | 10 | 190 | «09» | 0 | |
| 200 | **C** | «TBD» | «TBD» | «TBD» | «TBD» | |
| 300 | A | 10 | 290 | «07» | «07 심화» | |
| 300 | B | 10 | 290 | «09» | 0 | |
| 300 | **C** | «TBD» | «TBD» | «TBD» | «TBD» | |

---

## 2. 100명 정원경합 (100-way race) — 정원 100명, VU 200/400/800

> C 채택 명분 실증 구간 — 단일 행 100-way 경합에서 A(락 대기 폭증)/B(CAS 재시도 폭증) 대비 격차 최대 예상.  
> 정원 상한 가드(validateMaxMembers 100)가 feat/load-test 브랜치에 반영되어 있어야 함.

### 측정 절차

```bash
# 데이터 초기화 (maxMembers=100 크루 준비)
psql ... -f load-test/sql/07_rush_reset.sql  # maxMembers 100으로 수정 후 실행

# VU별 실행
k6 run --env BASE_URL=http://<SERVER> --env TARGET_VUS=200 --env RUSH_CREW_COUNT=1 \
  load-test/k6/crew-rush.js
k6 run --env BASE_URL=http://<SERVER> --env TARGET_VUS=400 --env RUSH_CREW_COUNT=1 \
  load-test/k6/crew-rush.js
k6 run --env BASE_URL=http://<SERVER> --env TARGET_VUS=800 --env RUSH_CREW_COUNT=1 \
  load-test/k6/crew-rush.js
```

### 결과 (정원 100명)

| VU | 전략 | join_success | join_full | p95 (ms) | conn_reset | 비고 |
|----|------|:---:|:---:|:---:|:---:|------|
| 200 | A | «TBD» | «TBD» | «TBD» | «TBD» | |
| 200 | B | «TBD» | «TBD» | «TBD» | «TBD» | |
| 200 | **C** | «TBD» | «TBD» | «TBD» | «TBD» | |
| 400 | A | «TBD» | «TBD» | «TBD» | «TBD» | |
| 400 | B | «TBD» | «TBD» | «TBD» | «TBD» | |
| 400 | **C** | «TBD» | «TBD» | «TBD» | «TBD» | |
| 800 | A | «TBD» | «TBD» | «TBD» | «TBD» | |
| 800 | B | «TBD» | «TBD» | «TBD» | «TBD» | |
| 800 | **C** | «TBD» | «TBD» | «TBD» | «TBD» | |

---

## 3. 중복 가입 동시성 (dup-join) — 전략 C 전용

### 측정 절차

```bash
# 1. DUP_CREW_ID, DUP_TOKEN 준비 (사전 생성 필요)
# 2. 실행
k6 run \
  --env BASE_URL=http://<SERVER> \
  --env DUP_CREW_ID=<crewId> \
  --env DUP_TOKEN=<accessToken> \
  --env TARGET_VUS=20 \
  load-test/k6/dup-join.js
```

### 결과

| VU | dup_join_success | dup_join_already | crew_members 행 수 | 비고 |
|----|:---:|:---:|:---:|------|
| 20 | «TBD» | «TBD» | «TBD» | 기대: success=1, already=19, 행 수=1 |

---

## 4. A/B/C 전략 특성 비교

| 항목 | A (PESSIMISTIC) | B (OPTIMISTIC) | C (CONDITIONAL) |
|------|:---:|:---:|:---:|
| 락 구간 | SELECT→앱→UPDATE→COMMIT (김) | SELECT→앱→CAS UPDATE | UPDATE 한 방 |
| 재시도 | 없음 | 최대 3회 | 없음 |
| conn_reset (VU 200+) | «07 측정값» | 0 | «TBD» |
| p95 at VU 200 | «07» | «09» | «TBD» |
| 정합성 (정원 초과) | DB 직렬화 | EvalPlanQual 재검사 | EvalPlanQual 재검사 |
| 중복 가입 보호 | addMember isAlreadyMember | 동일 | addMemberSkipCapacityCheck + 유니크 제약 |
| version 컬럼 의존 | 없음 | 있음 | 없음 |

---

## 5. 채택 판단 기준

- C의 conn_reset이 A보다 낮고 B와 동등하다면: **C 채택 검토**
- C의 p95가 A/B 대비 유의미하게 낮다면: C 채택 시 p95 개선 명분 추가
- 정합성 assert (join_success == maxMembers) 실패 시: EvalPlanQual 재검토 필요

> ⚠️ 채택 결정 시 prod 적용 전 `dedup 선확인` 필수  
> (SELECT crew_id, user_id, COUNT(*) FROM crew_members GROUP BY crew_id, user_id HAVING COUNT(*) > 1)
