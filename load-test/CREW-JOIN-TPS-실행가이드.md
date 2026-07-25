# CREW-JOIN TPS 실행가이드 — 락 전략 3종 × 경합 2케이스 (6런)

> 지시서: `CREW-JOIN-TPS-락전략3종-작업지시서.md` (v3) | 작성: 2026-07-20
> 환경 정본: `CREW-RUSH-DOCKER-환경-셋업-런북.md` (이하 "런북")
> 산출물: `k6/tps-join.js`, `sql/10_tps_crews.sql`, `sql/10_tps_reset.sql` (전부 신규 — 기존 파일 무수정)

---

## 실험 목적 (07-21 👤 확정 — 순수 TPS 비교)

> **동일한 도착률과 동일한 경합 조건에서 비관락·낙관락·조건부 업데이트가 각각 어느 정도의
> 처리량을 내고, 어느 구간부터 목표 TPS를 감당하지 못하는가?**

- 정합성·락 경합 동작 자체는 이전 VU 시리즈에서 검증 완료 — 이 시리즈에서 재검증하지 않는다.
  (별도 정합성 시나리오·라운드로빈/균형 셔플·락 충돌 재현 시나리오 추가 금지)
- CASE A = 크루당 **평균 12건의 랜덤 분산 워크로드** / CASE B = 크루당 **평균 100건의 랜덤 분산
  워크로드** ("정확히 K명 경합"이 아님). 일부 크루가 정원(10)을 못 채우는 것은 이 워크로드에서
  정상이며, 성공 수를 `사용 크루 수 × 10`으로 환산하지 않는다 — 실측값만 사용.
- `dup=0`·`bucket_overflow=0`·초과가입 0은 정합성 실험이 아니라 **TPS 결과 오염 방지용
  최소 유효성 가드**다. 반면 달성 rate 미달·dropped·p95 초과는 가드 실패가 아니라
  **서버 한계 측정 결과** — 중단하지 않고 §10 규칙으로 해석한다.

---

## 0. 전제 (어기면 결과가 정본 자격을 잃는다 — 지시서 §3)

- **Docker 프로드 재현 환경 전용.** 격리된 부하테스트 트윈(EC2 t3.micro + 테스트 RDS db.t4g.micro)
- 이미지: `devjian/triagain:loadtest` (feat/load-test 직접 빌드, ⛔ latest push 금지)
- ⛔ `docker run`에 `--memory` 금지 (ergonomic heap 재현이 명분) / ⛔ bare `java -jar` 금지
- 프로파일: **`prod,loadtest`** (런북 정본 :140. 지시서 §5-1 예시의 `loadtest` 단독은 런북과 다름 — 런북을 따른다)
  - loadtest 프로파일 의존: 토큰 24h 만료, `internal.api-key`(pre-GC 게이트)
- 운영 환경/운영 DB 접근 절대 금지. `$TEST_DB`·`BASE_URL`은 환경변수 주입, 하드코딩 금지
- k6는 **`load-test/` 디렉토리에서 실행** (`cd triagain-back/load-test`) — handleSummary가 상대경로
  `results/raw/`에 쓰므로 cwd가 어긋나면 raw가 엉뚱한 곳에 생긴다
- GC는 6런 내내 동일(현재 기본 = Serial GC 에르고노믹스). **G1 실험과 같은 밤에 섞지 말 것**

---

## 1. 사전 준비 (세션 시작 시 1회)

### 1-1. 이미지 빌드 (feat/load-test 최신 반영 필요 시)

```bash
# triagain-back/ (feat/load-test 체크아웃 상태)에서 — 런북 :124 정본
docker buildx build --platform linux/amd64 -t devjian/triagain:loadtest --push .
```

### 1-2. 크루 + 유저 시드 — B(800) 먼저, A(6000) 나중

```bash
cd triagain-back/load-test
psql "$TEST_DB" -v ON_ERROR_STOP=1 -c "SET app.tps_case='B';" -f sql/10_tps_crews.sql   # 800개 상태 — DoD 검증쿼리 출력 기록
psql "$TEST_DB" -v ON_ERROR_STOP=1 -c "SET app.tps_case='A';" -f sql/10_tps_crews.sql   # 6,000개로 확장 — DoD 검증쿼리 출력 기록
# (app.case가 아닌 이유: `case`는 SQL 예약어 — SET에서 문법 오류. 로컬 실증으로 확인)
```

- `ON CONFLICT DO UPDATE`라 순증만 함: B→A 순서로 실행하면 최종 6,000개 슈퍼셋.
- **이후 케이스 전환 때 재시드 불필요** — 리셋(`10_tps_reset.sql`)이 `loadtest-tps-crew-%` 전체를 되돌린다.
  CASE B 런은 앞쪽 ~640개 크루만 건드리며, PK 단일 조회라 6,000행 vs 800행 차이는 측정 공정성에 무의미.
- DoD "각각 생성 확인"은 B 시드 직후(800)·A 시드 직후(6,000)의 검증 쿼리 출력으로 충족.

### 1-3. 최초 컨테이너 기동 — 스모크 1용 CONDITIONAL

**§2-1 명령에서 `--triagain.crew.lock-strategy=CONDITIONAL`로 치환해 기동**한다.
(토큰 발급·스모크 1이 이 컨테이너를 쓴다. 기동 후 §2-2 부팅시각 기록 + §2-3 GC 증빙까지 수행.)

> 기동 횟수 전체 그림: 최초 1회(스모크 1, CONDITIONAL) + 스모크 2 재기동 1회(PESSIMISTIC)
> + 본측정 6회(§7) = **총 8회**. 매 기동마다 §2-2·§2-3 기록.

### 1-4. 토큰 3,000개 발급 (24h 유효 — 6런 전체 1회로 충분, 서버 기동(§1-3) 후 실행)

```bash
cd triagain-back/load-test
./scripts/generate-tokens.sh http://<HOST>:8080 3000
tail -n +2 tokens.csv | wc -l    # == 3000 확인 (DoD)
```

⚠️ 순차 curl 3,000회 — 소요시간 실측 전례 없음(수 분~십수 분 예상). 세션 시작 직후 돌려둘 것.
⛔ `users-tokens.json` 등 신규 포맷 금지 — `k6/lib/config.js`가 `tokens.csv`(헤더 `token`)를 읽는다.

---

## 2. 전략별 컨테이너 기동 (전략 전환 = 재기동 — 런타임 변경 불가)
docker rm -f triagain-loadtest 2>/dev/null || true // 정리

### 2-1. 기동 (런북 :133-143 정본 그대로)

```bash
docker rm -f triagain-loadtest 2>/dev/null || true
docker run -d --name triagain-loadtest \
  -p 8080:8080 \
  -e DB_URL="jdbc:postgresql://<TEST_RDS_HOST>:5432/triagain" \
  -e DB_USERNAME="<test>" -e DB_PASSWORD="<test>" \
  -e JWT_SECRET="<jwt>" \
  devjian/triagain:loadtest \
  --spring.profiles.active=prod,loadtest \
  --triagain.crew.lock-strategy=<PESSIMISTIC|OPTIMISTIC|CONDITIONAL> \
  '--spring.flyway.ignore-migration-patterns=*:missing,*:future'
```

<!-- ⛔⛔ 커밋 금지 블록 (👤 07-21 결정): 실비번·시크릿 포함 — 커밋 전 이 블록 통째로 삭제 ⛔⛔ -->
docker run -d --name triagain-loadtest \
  -p 8080:8080 \
  -e DB_URL="jdbc:postgresql://triagain-test-db.cxis2q422sto.ap-northeast-2.rds.amazonaws.com:5432/triagain" \
  -e DB_USERNAME="triagain" \
  -e DB_PASSWORD="da6412^^Adb" \
  -e JWT_SECRET="dGVzdC1zZWNyZXQta2V5LWZvci1sb2NhbC1kZXZlbG9wbWVudC1vbmx5LXRyaWFnYWlu" \
  devjian/triagain:loadtest \
  --spring.profiles.active=prod,loadtest \
  --triagain.crew.lock-strategy=CONDITIONAL \
  '--spring.flyway.ignore-migration-patterns=*:missing,*:future'
<!-- ⛔⛔ 커밋 금지 블록 끝 ⛔⛔ -->

> **[결정 07-21 👤] accept-count 무지정 = Tomcat 기본 100.** 런북 :133-143의 `--server.tomcat.accept-count=256`은
> 이 시리즈에서 의도적으로 제거 — 8회 기동(스모크 2+본 6런) 전부 무지정으로 통일한다(섞이면 비교 오염).
> 256 효과는 본 6런 후 케이스 B × 전략 1개 스팟 런(1~2회)으로 별도 확인. `docker inspect {{.Args}}`에
> accept-count 플래그가 **없어야** 정상이며, boot-times.md에 "미지정=기본 100" 명기.

기동 확인:

```bash
curl -sf http://<HOST>:8080/actuator/health          # {"status":"UP"}
docker logs triagain-loadtest 2>&1 | grep -i 'Started TriagainApplication'
```

### 2-2. 🔴 JVM 부팅시각 기록 — 라벨 확정의 정본 (런마다 필수)

RUN_TAG·파일명 접두어는 사람이 넣는 값이라 증빙이 못 된다(접두어↔전략 반전 전례).
**부팅시각 기록이 없으면 6런이 어느 전략인지 사후 증명 불가.**

```bash
curl -s http://<HOST>:8080/actuator/prometheus | grep '^process_start_time_seconds'
# 사람이 읽는 시각으로 변환: (EC2/Linux) date -d @<정수부>  /  (맥) date -r <정수부>
```

기록 항목(런 로그에 함께): `부팅시각 epoch | 전략 플래그 | docker logs의 lock-strategy 라인`

```bash
docker logs triagain-loadtest 2>&1 | grep -i 'lock'   # 기동 인자/전략 로그 라인 확보
docker inspect triagain-loadtest --format '{{.Args}}' # 트레일링 인자(전략 플래그) 원문 증빙
```

### 2-3. GC 종류 증빙 (런마다 결과 파일에 기록 — 지시서 §5-3)

> **[실측 07-21] 방법 1 불가** — 이 이미지(JRE 슬림)에 `jcmd` 없음(OCI exec: not found).
> **방법 3(프로메테우스)이 이 시리즈의 정본**: Serial이면 `gc="Copy"`/`gc="MarkSweepCompact"`, G1이면 `gc="G1 Young Generation"`.

```bash
# 방법 1(정본): 러닝 JVM에서 직접 — ⛔ 07-21 실측: 이 이미지엔 jcmd 없음 (위 배너)
docker exec triagain-loadtest jcmd 1 VM.flags | tr ' ' '\n' | grep -E 'Use(Serial|G1|Parallel)GC'
# 방법 2(폴백): 동일 컨테이너 가시 RAM의 에르고노믹스 재현
docker exec triagain-loadtest java -XX:+PrintFlagsFinal -version | grep -E 'Use(Serial|G1|Parallel)GC'
# 방법 3(원격 증빙): pre-GC 게이트 이후 컬렉터 라벨이 붙음
curl -s http://<HOST>:8080/actuator/prometheus | grep 'jvm_gc_pause_seconds_count'
```

### 2-4. 워밍업 런 1회 — 본측정용 재기동 직후 필수, 결과 버림 (JIT 워밍업)

```bash
cd triagain-back/load-test
# ⚠️ 워밍업 전 리셋 필수 — 직전 런 잔재(만석 크루) 위에서 돌면 전량 CR002로
#    성공 쓰기(INSERT·UPDATE) 경로가 워밍업되지 않는다 (적대적 리뷰 발견)
#    (§3 절차로 진행 중이면 ①에서 이미 수행 — 이 줄은 §2-4를 단독 참조할 때용, 중복 실행해도 무해)
psql "$TEST_DB" -v ON_ERROR_STOP=1 -f sql/10_tps_reset.sql

k6 run --env BASE_URL=http://<HOST>:8080 --env RUN_TAG=<전략>_A_r50 \
  --env CASE=A --env MODE=const --env RATE=50 --env CONST_DURATION=60s \
  --env PRE_ALLOC_VUS=50 --env MAX_VUS=100 --env PRE_GC=off \
  k6/tps-join.js
```

- `PRE_GC=off`: 워밍업엔 게이트 불필요(본 런 직전에 게이트가 돈다)
- 워밍업 raw도 저장되지만 **결과 집계에서 제외** (r50 태그로 구분됨)
- 워밍업은 **본측정 런의 기동에만 필수** — 스모크(§6)는 저부하 기능 확인이라 워밍업 생략

---

## 3. 매 런 절차 (본측정 1런 = 아래 순서 고정)

```
⓪ 재기동         §2-1 → §2-2 부팅시각 → §2-3 GC 증빙 (6런 전부 — §7 표)
① 리셋(워밍업 전) psql "$TEST_DB" -v ON_ERROR_STOP=1 -f sql/10_tps_reset.sql
                 → 직전 런 잔재 제거 — 워밍업이 성공 쓰기 경로를 실제로 타게 함
② 워밍업         §2-4 (결과 버림)
③ 리셋(본런 전)   psql "$TEST_DB" -v ON_ERROR_STOP=1 -f sql/10_tps_reset.sql
                 → before/after_vacuum의 n_dead_tup 출력을 런 로그에 저장 (블로트 증빙)
④ nstat 시작     (EC2 호스트 셸 — §4, 재기동했으므로 CPID 재조회 필수)
⑤ 부팅시각 재확인 (§2-2와 같은 값이어야 함 = 도중 재시작 없음 증빙)
⑥ 본 런          (§5)
⑦ nstat 종료     kill $NSTAT_PID
⑧ 회수           k6 stdout 박스 + raw 2종 + nstat 로그 + n_dead_tup 기록 (§8)
```

- **리셋이 런당 2회(①③)인 이유**: ①이 없으면 워밍업이 만석 크루만 때려 JIT 워밍업 무효,
  ③이 없으면 워밍업 잔재(~수백 멤버)가 본런 초반 CR004/CR002를 오염시킨다.

---

## 4. nstat 병행 수집 (6런 전부 필수 + 스모크에서 파이프라인 가동 확인 — 지시서 §5-2)

**컨테이너 netns가 정본.** 호스트에서 보면 도커 프록시 backlog가 잡힌다(기존 확인 사항).

```bash
# EC2 호스트 셸
CPID=$(docker inspect -f '{{.State.Pid}}' triagain-loadtest)
LOG="$HOME/nstat-ts-$(date +%H%M%S).log"; echo "logging to: $LOG"
( while true; do
    ts=$(date '+%H:%M:%S')
    sudo nsenter -t $CPID -n nstat -asz 2>/dev/null | awk -v t="$ts" '
      /ListenOverflow|ListenDrop|SynRetrans|ReqQFull|Syncookie|BacklogDrop|OutRsts|AttemptFails|EstabResets|AbortOn|MemoryPressure|TcpPassiveOpens|TcpActiveOpens/ {
        print t, $1, $2; fflush()
      }'
    sleep 1
  done ) >> "$LOG" 2>&1 &
NSTAT_PID=$!
```

- 런북 :194-206 필터에 지시서 §5-2 요구 항목(`TcpPassiveOpens/TcpActiveOpens`)을 추가한 버전
- 런 종료 후: `kill $NSTAT_PID`, 로그 파일을 `results/<MMDD>/`로 회수
- ⚠️ 컨테이너를 재기동하면 CPID가 바뀐다 — **재기동 때마다 CPID 재조회**

---

## 5. 본 런 실행 (기본 프로파일: 50→500 램핑 2m + 500 유지 1m)

```bash
cd triagain-back/load-test
TAG=<전략>_<A|B>_r500
k6 run --env BASE_URL=http://<HOST>:8080 \
  --env RUN_TAG=$TAG \
  --env CASE=<A|B> --env MODE=ramp --env RATE=500 \
  --out json=results/raw/tps-join_$TAG.json.gz \
  k6/tps-join.js
```

- RUN_TAG 형식 `{전략}_{A|B}_r{rate}` 강제 + CASE·RATE 교차검사 — 불일치 시 init throw(raw 보존)
- `--out json=…​.json.gz` (07-21 👤 추가): 요청 단위 raw 시계열 — `.gz` 확장자면 k6가 자동 압축
  (본런 ~9만 iteration이라 비압축 수백 MB → 압축 필수. 초당 TPS·상태코드별 시계열 사후분석용)
- `preAllocatedVUs=250 / maxVUs=500` 기본값 — 리틀의 법칙 산정(500 TPS × 0.5s = 250, 여유 2배). 스크립트 주석 참조
- pre-GC 게이트 기본 on — stdout `[pre-GC] {"success":true,...heapUsedBeforeMb...}` 라인이 증빙
- 조정 가능 env: `W`(20) `RATE`(500) `START_RATE`(50) `RAMP_DURATION`(2m) `HOLD_DURATION`(1m) `P95_MS`(500)
  - ⚠️ RATE·duration을 키우면 총 시도수가 늘어 시드 크루가 부족해질 수 있다.
    필요 크루 ≈ `ceil(총 시도수 / K)`. 초과분은 `join_bucket_overflow`(threshold `count==0`)로 자동 검출됨

---

## 6. 저부하 스모크 (본측정 전 필수) + 🔴 승인 게이트

```bash
cd triagain-back/load-test

# nstat 파이프라인 가동 확인용 수집 시작 (§4 — DoD "nstat 값 수집 확인"은 여기서 충족)
# → EC2 호스트 셸에서 §4 블록 실행 (스모크 동안 값이 찍히는지 확인, 종료는 kill $NSTAT_PID)

# ⚠️ 스모크 1 전 리셋 필수 — 워밍업/이전 실행 잔재가 남아 있으면 같은 유저가 같은 크루를
#    재타격해 join_rejected_dup이 구조적으로 발생(기대 ~11건) → DoD 기준 확정 실패 (적대적 리뷰 발견)
psql "$TEST_DB" -v ON_ERROR_STOP=1 -f sql/10_tps_reset.sql

# 스모크 1: CASE A × CONDITIONAL (§1-3에서 기동한 컨테이너)
k6 run --env BASE_URL=http://<HOST>:8080 --env RUN_TAG=CONDITIONAL_A_r10 \
  --env CASE=A --env MODE=const --env RATE=10 --env CONST_DURATION=30s \
  --env PRE_ALLOC_VUS=10 --env MAX_VUS=30 k6/tps-join.js

# 리셋 후 — 스모크 2: CASE B × PESSIMISTIC (재기동으로 전략 전환 동작까지 확인)
psql "$TEST_DB" -v ON_ERROR_STOP=1 -f sql/10_tps_reset.sql
# §2-1 재기동(--triagain.crew.lock-strategy=PESSIMISTIC) + §2-2 부팅시각 기록
# (재기동했으므로 nstat CPID 재조회 후 수집 재시작 — §4)
k6 run --env BASE_URL=http://<HOST>:8080 --env RUN_TAG=PESSIMISTIC_B_r10 \
  --env CASE=B --env MODE=const --env RATE=10 --env CONST_DURATION=30s \
  --env PRE_ALLOC_VUS=10 --env MAX_VUS=30 k6/tps-join.js
```

스모크 통과 기준 (DoD): 카운터 정상 집계, `join_rejected_dup == 0`, 버킷 진행 확인(성공이 여러 크루에 분산),
전략 전환 동작 확인(부팅시각 변경 + 기동 인자), nstat 파이프라인에서 값 수집 확인, pre-GC 게이트 stdout 로그 확인.

> 🔴 **여기서 정지. 스모크 결과 보고 → 사용자 승인 1회 → 그 후에만 6런 본측정.**
> 승인 전 고부하 실행 절대 금지 (지시서 §7).

⚠️ **본측정 개시 직전 토큰 잔여 유효시간 확인**: 토큰은 발급 후 24h 유효.
스모크→승인 대기가 길어졌다면 `발급시각 + 24h − 현재시각 > 6런 예상 소요(~2h)` 를 확인하고,
부족하면 `generate-tokens.sh` 재발급 후 진행 (도중 만료 시 전량 401로 6런이 무효가 된다).

---

## 7. 6런 실행 순서 (순서 교락 완화 — A: P→O→C / B: C→O→P)

| # | 전략 | 케이스 | 재기동 | 워밍업 | 비고 |
|---|---|---|---|---|---|
| 1 | PESSIMISTIC | A | ✅ (기동1) | ✅ | |
| 2 | OPTIMISTIC | A | ✅ (기동2) | ✅ | |
| 3 | CONDITIONAL | A | ✅ (기동3) | ✅ | |
| 4 | CONDITIONAL | B | ✅ (기동4) | ✅ | 전략은 동일하지만 재기동 — 하단 이유 참조 |
| 5 | OPTIMISTIC | B | ✅ (기동5) | ✅ | |
| 6 | PESSIMISTIC | B | ✅ (기동6) | ✅ | |

- **6런 전부 재기동으로 통일** (지시서 "최소 3회"의 상위 이행): 런 4만 무재기동이면 CASE B 3자 비교에서
  CONDITIONAL만 성숙 JVM(런 3의 63k회 처리 후) 이점을 갖는 계통 편향이 생긴다 (적대적 리뷰 발견).
  전략 라벨 증빙은 재기동해도 새 부팅시각 + `docker inspect` 기동 인자로 동등하게 확보된다
- 본측정 재기동 6회. 스모크용 기동 2회(§1-3 최초 CONDITIONAL·§6 스모크2 PESSIMISTIC)는 별도 — 총 8회(§1-3 참조).
  매 본측정 기동마다 §3 절차(⓪~⑧) 전체 수행
- 런 사이 간격은 일정하게(리셋+VACUUM 완료 후 즉시) — 밤 시간대 이동에 따른 환경 드리프트 최소화

---

## 8. 결과 회수

| 항목 | 위치 | 비고 |
|---|---|---|
| k6 raw (.html/.summary.json) | `results/raw/tps-join_{RUN_TAG}_{ts}.*` | handleSummary 자동 (cwd=load-test/ 필수) |
| k6 stdout 박스 | 런 로그로 저장 | TPS 분리·카운터·분모 출처 포함 |
| nstat 로그 | `$HOME/nstat-ts-*.log` → `results/<MMDD>/` | 런당 1파일 |
| 부팅시각·전략·GC 증빙 | `results/<MMDD>/boot-times.md` (신규 작성) | §2-2·§2-3 출력 원문 |
| n_dead_tup 전/후 | 리셋 출력 캡처 → `results/<MMDD>/` | 첫 런·마지막 런 직전 값 필수(지시서 §4-1) |

## 9. 6칸 비교표 템플릿 (지시서 §9)

| 전략 | 케이스 | 목표 rate | 달성 rate | **성공 쓰기 TPS** | 전체 처리 TPS | durSec(분모·출처) | p95 | p99 | join_conflict | join_rejected_full | dropped_iterations | join_dropped(status0) | join_error(5xx) | ListenOverflows | JVM 부팅시각 | 한계 TPS 판정 근거 |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| PESSIMISTIC | A | | | | | | | | | | | | | | | |
| OPTIMISTIC | A | | | | | | | | | | | | | | | |
| CONDITIONAL | A | | | | | | | | | | | | | | | |
| CONDITIONAL | B | | | | | | | | | | | | | | | |
| OPTIMISTIC | B | | | | | | | | | | | | | | | |
| PESSIMISTIC | B | | | | | | | | | | | | | | | |

> `durSec(분모·출처)` 컬럼은 지시서 §9 원형에 §10-5 규칙(분모 병기)을 반영해 추가한 것 —
> k6 stdout 박스의 "분모 출처: ... N s" 줄을 그대로 옮겨 적는다.

## 10. 해석 규칙 (리포트에 반드시 반영 — 지시서 §0·§4-2·§5-2·§9)

1. **CASE B 성공 TPS 구조적 상한 = `rate × 10/K`** (300 TPS 발사 → 성공 최대 30 TPS).
   명시 안 하면 "B에서 성공 TPS가 낮다"를 전략 열위로 오독한다.
2. **`random(W)` 분포**: 크루당 시도 기댓값 12(A), 표준편차 ≈3.4 → 상당수 크루가 정원 미달 종료.
   `join_success ≠ 크루수×10`. 성공 TPS는 실측 `join_success ÷ 실측 소요시간`만 인정.
   세 전략이 같은 분포를 받으므로 전략 간 비교 공정성은 유지된다 — 분포 절대값을 결론에 넣지 않는다.
3. **커널 천장 귀속**: `dropped_iterations > 0`(k6 스케줄 실패)이면서 같은 구간 `ListenOverflows` 증가
   → 그 구간은 앱 포화가 아니라 TCP 수립층 천장. 전략 비교에서 제외하고 명시.
   - ⚠️ `dropped_iterations`(요청이 네트워크에 안 나감 — VU 부족)와 `join_dropped`(status 0 —
     나갔다가 연결 실패/리셋)는 **다른 레이어**다. 커널 accept 큐와 직접 연관되는 건 `join_dropped` 쪽이며,
     귀속 판정 시 **두 지표 모두** ListenOverflows 시계열과 대조할 것.
4. **성공 쓰기 TPS vs 전체 처리 TPS 분리 보고** — 고경합에서 409는 빠른 경로라 전체 TPS는 오르면서
   유효 처리량은 떨어질 수 있다. 구분 없이 읽으면 숫자를 거꾸로 읽는다.
5. **TPS 분모(durSec)를 비교표에 병기할 것** — `measurement_window_ms`는 시나리오 종료 후
   in-flight 요청의 gracefulStop 대기(기본 최대 30s)를 포함할 수 있어, 포화 런은 분모가
   비포화 런보다 길어질 수 있다. k6 stdout 박스의 durSec·분모 출처를 그대로 옮겨 적으면
   런 간 분모 차이를 사후 감사할 수 있다.
6. **전략 우열 판정 기준 (07-21 👤 확정)** — 아래 4기준으로만 판단한다:
   ① 목표 rate를 어디까지 유지하는가 (달성 rate가 목표에서 이탈하는 지점)
   ② 성공 쓰기 TPS가 어느 지점에서 꺾이는가
   ③ 지연(p95/p99)과 drop이 언제 급증하는가
   ④ CASE A ↔ CASE B에서 전략별 특성이 어떻게 달라지는가
   달성 rate 미달·dropped_iterations·join_dropped·p95 초과는 **중단 사유가 아니라 측정 결과**다.
   즉시 정지는 실험 오염(dup>0, bucket_overflow>0, 전량 오류 — 예: 07-21 토큰 401 사고)일 때만.

## 11. 트러블슈팅

| 증상 | 원인 | 조치 |
|---|---|---|
| 전량 CR003 | 시드 `allow_late_join` 누락/리셋 안 함 | `10_tps_reset.sql` 실행 (isJoinableStatus 함정) |
| 전량 join_other + http_req_failed≈100% | 401 A003 — 토큰 서명 불일치 (발급 기동과 현재 기동의 JWT_SECRET 상이 — 붙여넣기 손상 등) | 현재 서버에서 토큰 전량 재발급 + 기동은 EC2 `~/boot.sh <전략>` 고정 스크립트로만 (07-21 실제 발생) |
| 401 대량 | loadtest 프로파일 누락(토큰 30분 만료) 또는 토큰 만료 | 프로파일 확인, 토큰 재발급 |
| init에서 "tokens.csv 부족" throw | 토큰 수 < 3000 | `generate-tokens.sh <URL> 3000` 재실행 |
| init에서 RUN_TAG throw | 태그 형식/CASE/RATE 불일치 | 태그 수정 — 가드가 raw 덮어쓰기 사고를 막은 것 |
| pre-GC 실패 throw | internal.api-key 불일치/프로파일 누락 | `GC_API_KEY` env·프로파일 확인 |
| `join_bucket_overflow > 0` | RATE·duration 확대로 시드 크루 부족 | `app.crew_count`로 재시드 + `TOTAL_CREWS` env 일치 |
| VACUUM 에러 "cannot run inside a transaction block" | psql `-1`/`--single-transaction` 사용 | 해당 플래그 제거 (근거: `10_tps_reset.sql` 헤더 주석·§3 ③) |
| http_req_failed가 0이 아님(미미) | 5xx/드롭 외에도 비기대 상태 존재 | summary의 카운터로 원인 분해 |

---

## 부록 A: 지시서 대비 구현이 달라진 점 (전부 검증 근거 있음 — 되돌리지 말 것)

| # | 지시서 | 구현 | 근거 |
|---|---|---|---|
| 1 | §4-3 `options.responseCallback: http.expectedStatuses(201,409)` | init 컨텍스트 `http.setResponseCallback(http.expectedStatuses(201, 409))` | k6 options에 responseCallback 키 없음 — 공식문서(set-response-callback) 확인. 지시서 문법은 실행 자체가 안 됨 |
| 2 | §5-1 예시 `--spring.profiles.active=loadtest` | `prod,loadtest` | 런북 정본 :140. prod 누락 시 운영 패리티 명분 훼손 |
| 3 | (없음) | `summaryTrendStats`에 `p(99)` 명시 | k6 기본 통계에 p99 없음(공식 options reference) — §9 비교표 p99 컬럼 요건 |
| 4 | (없음) | `join_bucket_overflow` 카운터+threshold | 분배 수식은 그대로 두고, 시드 초과 구간의 404 오염만 차단하는 안전망 |
| 5 | (없음) | `measurement_window_ms` (setup 제외 실측 분모) | testRunDurationMs는 setup(pre-GC+5s) 포함 — 성공 TPS ~3% 과소평가 방지 |
| 6 | §4-3 RUN_TAG 형식 가드 | + CASE·RATE 교차 일치 검사 추가 | 오태그 전례(07-09) — 형식만 맞고 내용이 틀린 태그도 차단 |
| 7 | 런북 nstat = foreground `tee` | 서브셸 백그라운드(`&` + `NSTAT_PID`) 실행 | §3 런 절차의 `kill $NSTAT_PID` 종료 흐름에 필요한 구조 변경 (필터·수집 내용은 동일) |
| 8 | 07_rush의 ON CONFLICT UPDATE 절 | `status='ACTIVE'`·`visibility='PUBLIC'` 추가 | 한번 꼬인 상태(ENDED 등)가 재시드로 안 풀리는 CR003 함정 방어 — 로컬 실증에서 복구 확인 |
| 9 | §4-1 시드 파라미터 `app.case` 형태 예시 없음 | `app.tps_case`로 명명 | `case`는 SQL 예약어 — `SET app.case`는 문법 오류(로컬 postgres:16 실증) |
| 10 | §4-3 `'http_req_duration': ['p(95)<500']` | `'http_req_duration{name:join}'` 태그 스코프 | 지시서 §5-3 자신의 경고(pre-GC가 p95 오염) 이행 — setup 표본을 threshold에서 구조적으로 배제. 스모크(표본 300건)에서 특히 유효 |
| 11 | §5-1 재기동 = 전략 전환 시(최소 3회) | **6런 전부 재기동 + 런당 리셋 2회(워밍업 전·본런 전)** | 적대적 리뷰 발견 2건 반영 — 런 4 무재기동은 CASE B 비교에 JVM 성숙도 비대칭, 워밍업 전 리셋 부재는 JIT 워밍업 무효화·스모크 DoD 구조적 실패(dup 오경보) |

## 부록 B: 그라운딩 조사 결과 (지시서 §2 "남은 조사 항목" 3건 — 실코드 확인)

1. **201 응답 바디 스키마**: `ResponseEntity<ApiResponse<JoinCrewResult>>` (`CrewController.java:177-185`, record는 `JoinCrewUseCase.java:15`)
   ```json
   {"success":true,"data":{"userId":"...","crewId":"...","role":"MEMBER","currentMembers":3,"joinedAt":"..."},"error":null}
   ```
   → `data.currentMembers`로 카운터 교차검증이 가능하나, 응답 파싱 비용 대비 이득이 없어 **카운터 검증에는 미사용** (에러 코드 파싱만 사용).
   에러 바디는 `{"success":false,"data":null,"error":{"code":"CRxxx","message":"..."}}` — `errorCode()`가 읽는 경로 `error.code` 확정.
2. **네임스페이스 충돌 없음**: `crews.id`는 VARCHAR(36) 문자열 PK (`V1__initial_schema.sql:21`). 기존 크루 접두어 전수(`loadtest-crew-`·`loadtest-rush-crew-`·`loadtest-sched-*-crew-`)와 `loadtest-tps-crew-`는 상호 접두 포함관계 없음. invite_code 접두어(LT/RS/SC/SR/SA/CC/SS)와 `TP`도 충돌 없음. 단 `00_truncate.sql`(전역 `loadtest-%` 삭제)은 tps 크루도 지움 — 의도된 전역 클린업.
3. **07_rush 재사용 범위**: `07_rush_crews.sql`의 joinable 패턴(ACTIVE+allow_late_join+PUBLIC+max10, ON CONFLICT DO UPDATE)과 `07_rush_reset.sql`의 삭제 순서(challenges→crew_members→crews UPDATE)를 준용. 크루 수 하드코딩(10)만 케이스 파라미터로 대체. 파일 자체는 무수정 재사용 불가(네임스페이스·수량이 다름)라 신규 10_* 파일로 분리.
