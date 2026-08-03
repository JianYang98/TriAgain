# 크루 가입 전략 A/B/C 부하테스트 — 실행 런북 & 체크리스트

> **목표**: 크루 가입 정원 경합을 3가지 동시성 전략으로 측정·비교 → `results/11_conditional-update-comparison.md` 작성
> **전략**: A=비관락(PESSIMISTIC) / B=낙관락(OPTIMISTIC) / C=조건부 UPDATE(CONDITIONAL)
> **담당**: 👤 사용자(인프라/SSH/k6 실행/배포결정) · 🤖 오케스트레이터(스크립트수정 위임·명령제공·결과해석·문서)
> 진행하며 `[ ]` → `[x]`. 상세 근거는 plan: `~/.claude/plans/load-test-async-otter.md`

---

## ⏯️ 다음 세션 시작점 (READ FIRST)

- **현재까지**: Phase 1~5 완료. **21 측정 모두 실측 완료**(2026-06-19, `results/raw/k6-report_{A,B,C}_*.summary.json` + raw 21종). EC2 `15.164.69.243` **UP**.
- **Phase 6 진행**: 🤖 비교 문서 `results/11_conditional-update-comparison.md` **작성 완료**(실측 반영, 측정/파생/추정 라벨). A·C 정합성 OK / **B는 vu200·max100서 정원미달(64/100) 결함** / **C가 p95 전 구간 최저** → C 채택 권장.
- **남은 할 일**:
  1. 👤 **DB 정합성 확인** — 정원초과 0건(문서 §6 SQL, DB 자격증명 필요). k6 succ==정원은 확인됨, DB 행수는 미확인.
  2. 👤 EC2 **중지**(과금 방지) + 모니터링 종료(`./scripts/stop-monitoring.sh`).
  3. (추후) dup-join 동시성 실증(문서 §3, 미측정).
- **⚠️ 주의**:
  - **prod 배포 금지** — 전략 C·정원 100은 `feat/load-test` 한정.
  - `join_dropped`(status 0)는 재실행 편차 큰 연결계층 지표 → 전략 판별 1차 지표 아님(문서 §측정신뢰성 참조).
  - 재실행 폐기 런 5건 식별·제외 완료(문서 표 참조). 추측 금지.
- **참고**: plan `~/.claude/plans/load-test-async-otter.md`, 메모리 `crew_rush_abc_loadtest_inflight`·`project_loadtest_data_provenance`

---

> **⚠️ 프로토콜 변경 (20260710+)**: pre-GC 게이트 적용 — 매 런 직전 k6 `setup()`이 `POST /internal/gc` 강제 Full GC 후 5초 대기. 이전 차수(~C13/A8)와 힙 초기조건 상이. accept-count 256 상향(07-03)과 동급의 프로토콜 변경.

## 📊 진행 현황 (한눈에)

- [x] **Phase 0** 사전 확인 (EC2/RDS/도구) 👤
- [x] **Phase 1** k6 측정 정밀화 + raw 보존 (스크립트 4개) 🤖 ← 적용·검증 완료
- [x] **Phase 2** 100명 벤치 선결 (SQL 정원100 + 토큰/유저 800) 🤖+👤
- [x] **Phase 3** 빌드 & EC2 기동 👤
- [x] **Phase 4** 모니터링 시작 (Prometheus+Grafana) 👤
- [x] **Phase 5** 측정 21회 (A/B/C × VU) 👤 ← raw 21종 + summary 보존(재실행 폐기 5건 제외)
- [~] **Phase 6** 결과 문서화 + 정리 🤖+👤 ← 🤖 비교문서 작성완료 / 👤 DB정합성·EC2중지 남음

---

## Phase 0 — 사전 확인 👤

- [ ] EC2 `15.164.69.243` 생존 확인 (AWS 콘솔/SSH). 죽었으면 동일 스펙(t3.micro)으로 재기동
- [ ] RDS `triagain-db.cxis2q422sto.ap-northeast-2.rds.amazonaws.com:5432` 접속 확인
- [ ] 로컬에 `k6`, `docker` 설치 확인 (`k6 version`, `docker ps`)
- [ ] `git -C triagain-back status` → `feat/load-test` 브랜치 / HEAD `8edef88` 확인
- [ ] (참고) 07/09 측정과 동일 환경이어야 비교 유효 — 인스턴스 스펙 바뀌지 않았는지

---

## Phase 1 — k6 측정 정밀화 + raw 보존 🤖 (서브에이전트 적용)

> 목적: 201/409만 세던 걸 status·에러코드별로 분해. conn_reset을 측정값(status 0)으로 승격. raw 전량 보존.

- [x] `k6/lib/metrics.js` — 카운터 추가: `join_conflict`(CR023)·`join_dup`(CR004)·`join_5xx`·`join_dropped`
- [x] `k6/lib/scenarios.js` — `crewRush()` 교체 (409를 CR002/CR004/CR023로 분기) + `errorCode()` 헬퍼
- [x] `k6/lib/report.js` — HTML + `summary.json` 동시 저장, `RUN_TAG` 파일명 태깅
- [x] `k6/crew-rush.js` — 정원 파라미터화 `parseInt(__ENV.MAX_MEMBERS || '10')`
- [x] 🤖 `git diff`로 변경 검증 완료 (4개 파일 의도대로) — [ ] 👤 최종 확인

**에러코드 매핑 (확정)**: `CREW_FULL=CR002` · `CREW_ALREADY_JOINED=CR004` · `CREW_JOIN_CONFLICT=CR023`

---

## Phase 2 — 데이터·토큰 준비 🤖 지시 / 👤 실행

> 유저·토큰을 800까지 한 번에 확보하면 10명·100명 벤치 모두 커버(VU≤800 고유 토큰).
> ⚠️ `generate-tokens.sh`는 **서버가 떠 있어야** 동작 → 유저 SQL(아래 1·2)은 지금, **토큰 발급(3)은 Phase 3 서버 기동 후**.
> `DB_URL` 예: `postgresql://<user>:<pw>@triagain-db.cxis2q422sto.ap-northeast-2.rds.amazonaws.com:5432/triagain`
> 모든 명령은 `triagain-back/load-test/` 에서 실행.

- [ ] **유저 1000명 적재** (`loadtest-user-1~1000`):
  ```bash
  psql "$DB_URL" -c "SET app.scale='L';" -f sql/01_users.sql
  ```
  (⚠️ 깨끗이 시작하려면 `psql "$DB_URL" -f sql/00_truncate.sql` 선행 — 단 모든 `loadtest-*` 데이터 삭제되니 07도 재생성)
- [ ] **러시 크루 생성** (10개, 정원 10, creator=`loadtest-user-1`):
  ```bash
  psql "$DB_URL" -f sql/07_rush_crews.sql
  ```
- [ ] **토큰 800개 발급** (← Phase 3 서버 기동 후, `jq` 필요):
  ```bash
  ./scripts/generate-tokens.sh http://15.164.69.243:8080 800
  tail -n +2 tokens.csv | wc -l       # → 800 확인
  ```
- [ ] **(100명 벤치 직전에만)** 러시 크루 정원 100으로:
  ```bash
  psql "$DB_URL" -c "UPDATE crews SET max_members=100, current_members=0 WHERE id LIKE 'loadtest-rush-crew-%';"
  ```
  → 10명 벤치로 되돌릴 땐 `max_members=10`. k6는 `--env MAX_MEMBERS=`로 짝맞춤(둘이 일치해야 판정 정상).
- [ ] (확인) 토큰 부족 시 `join_dup`(CR004) > 0 = 오염 신호 → 800 확보로 방지

---

## Phase 3 — 빌드 & EC2 기동 👤

- [ ] 로컬(`feat/load-test`)에서 빌드: `./gradlew bootJar`
- [ ] EC2로 업로드: `scp build/libs/triagain-0.0.1-SNAPSHOT.jar ec2-user@15.164.69.243:~/`
- [ ] (전략 전환 시마다) EC2에서 기동 — `lock-strategy`만 바꿔 재기동:
  ```bash
  java -Xmx512m -jar ~/triagain-0.0.1-SNAPSHOT.jar \
    --spring.profiles.active=prod,loadtest --triagain.crew.lock-strategy=<PESSIMISTIC|OPTIMISTIC|CONDITIONAL> \
    --spring.flyway.ignore-migration-patterns=*:missing
  ```
  > `flyway.ignore-migration-patterns=*:missing` 는 기존 기동에 포함돼 있던 인자(마이그레이션 missing 무시). 재기동 시 유지.
  > 앱 종료: `kill <PID>` (graceful) / `pkill -f 'triagain-0.0.1-SNAPSHOT.jar'`. 인스턴스 중지 시 Elastic IP 아니면 IP 변경 주의.
- [ ] 콜렉터 명기 — 기본 Serial(`Copy`/`MarkSweepCompact`). G1 채택 시 `-XX:+UseG1GC`를 `-jar` **앞**에 + 기동 명령 전문 기록에 포함(Phase 4.5 기동명령 기록 규율과 동일)
- [ ] ⚠️ 기동 로그로 적용된 전략 확인 (인자 없으면 기본 PESSIMISTIC)
- [ ] `curl http://15.164.69.243:8080/actuator/health` → UP, `/actuator/prometheus` 노출 확인

---

## Phase 4 — 모니터링 시작 👤 (로컬 Docker)

- [ ] `cd triagain-back/load-test/scripts && ./start-monitoring.sh 15.164.69.243`
- [ ] Grafana `http://localhost:3000` (admin / `loadtest`) → 대시보드 `triagain.json` 로드 확인
- [ ] Prometheus `http://localhost:9090` → target `triagain` UP 확인
- [ ] (선택) Tomcat busy-thread/accept-queue 패널 부재 → 연결병목 "측정"하려면 패널 추가, 아니면 "추정" 라벨

---

## Phase 4.5 — 서버측 관측 풀셋 👤 (드롭/메커니즘 분석 표준 — C11부터 필수)

> C10(07-05)이 미완으로 남은 원인 2개를 막는다: ① 기동 명령 미기록 → backlog 실효값 미상, ② nstat 3종만 수집 → 쿠키 계열 부재로 드롭 등식 마감 불가. A2(07-06, `nstat-ts-012017.log` 20종)가 등식을 닫은 세트가 아래다.

- [ ] **기동 명령 전문 기록**: Phase 3 java 명령을 그대로 복사해 세션 노트/결과 문서에 붙여넣기 — `--server.tomcat.accept-count` 등 튜닝 인자 포함 여부를 반드시 명기
- [ ] **backlog 실측** (기동 직후 1회): `ss -lnt 'sport = :8080'` → Send-Q 열 = 실효 backlog 기록
- [ ] **full nstat 1초 시계열** (k6 시작 전 백그라운드 기동):
  ```bash
  while true; do date '+%F %T'; nstat -asz | grep -E 'Tcp(AttemptFails|OutRsts|EstabResets|ActiveOpens|PassiveOpens)|Syncookies|Listen(Overflows|Drops)|TCPSynRetrans|TCPBacklog|TCPReqQFull|AbortOn'; sleep 1; done > nstat-ts-$(date +%H%M%S).log 2>&1 &
  ```
- [ ] **ss 상태 1초 시계열** (acceptQ + ESTAB):
  ```bash
  while true; do echo "$(date '+%T') acceptQ=$(ss -lnt 'sport = :8080' | awk 'NR==2{print $2"/"$3}') ESTAB=$(ss -Hnt state established '( sport = :8080 )' | wc -l)"; sleep 1; done > ss-state-$(date +%H%M%S).log 2>&1 &
  ```
- [ ] **앱 로그 파일 저장**: 기동 시 `> server-<TAG>.log 2>&1` 리다이렉트
- [ ] 측정 후 회수: nstat/ss/server 로그 3종 → `results/<MMDD>/` 로 내려받기 + Prometheus 창 회수(로컬 TSDB 보존 활용)

---

## Phase 5 — 측정 21회 👤 (전략 루프 A → B → C)

**측정 1회 절차** (매번 reset 선행, raw 3종 자동 저장):
```bash
DB_URL="postgresql://<user>:<pw>@<rds-host>:5432/triagain"
psql "$DB_URL" -f sql/07_rush_reset.sql                       # ← 매 측정 직전
TAG=<전략>_max<정원>_vu<VU>                                    # 예: A_max10_vu50
k6 run --env BASE_URL=http://15.164.69.243:8080 \
  --env TARGET_VUS=<VU> --env MAX_MEMBERS=<정원> \
  --env RUN_TAG=$TAG --out json=results/raw/crew-rush-jian_$TAG.json \
  k6/crew-rush-jian.js
# 산출: results/raw/crew-rush-jian_<TAG>_<ts>.html + .summary.json + crew-rush-jian_<TAG>.json
```
> k6 `setup()`이 자동으로 `POST /internal/gc`(pre-GC 게이트) 후 5초 대기 — 실패 시 런 시작 전 중단(throw). stdout `[pre-GC]` 줄을 세션 노트에 보존. 무게이트 arm은 `--env PRE_GC=off`(stdout `[pre-GC] SKIPPED`).
> 정원 100 측정 전: `psql "$DB_URL" -f sql/07_rush_crews.sql` 로 크루 정원 100 세팅 + 토큰 800 확보(Phase 2)

### 5-A — PESSIMISTIC (서버 재기동 1회)
- [ ] 서버 `--lock-strategy=PESSIMISTIC` 기동 + 로그 확인
- 정원 10: [ ] `A_max10_vu50` · [ ] `A_max10_vu100` · [ ] `A_max10_vu200` · [ ] `A_max10_vu300`
- 정원 100: [ ] `A_max100_vu200` · [ ] `A_max100_vu400` · [ ] `A_max100_vu800`

### 5-B — OPTIMISTIC (서버 재기동 1회)
- [ ] 서버 `--lock-strategy=OPTIMISTIC` 기동 + 로그 확인
- 정원 10: [ ] `B_max10_vu50` · [ ] `B_max10_vu100` · [ ] `B_max10_vu200` · [ ] `B_max10_vu300`
- 정원 100: [ ] `B_max100_vu200` · [ ] `B_max100_vu400` · [ ] `B_max100_vu800`

### 5-C — CONDITIONAL (서버 재기동 1회) ← 이번 신규 전략
- [ ] 서버 `--lock-strategy=CONDITIONAL` 기동 + 로그 확인
- 정원 10: [ ] `C_max10_vu50` · [ ] `C_max10_vu100` · [ ] `C_max10_vu200` · [ ] `C_max10_vu300`
- 정원 100: [ ] `C_max100_vu200` · [ ] `C_max100_vu400` · [ ] `C_max100_vu800`

**측정마다 확인할 값**: `join_success`(=정원?) · `join_full`/`join_dup`/`join_conflict`/`join_5xx`/`join_dropped` · p95(`scenario_d_duration`) · Grafana(HikariCP pending·GC·CPU)

---

## Phase 6 — 결과 문서화 + 정리 🤖+👤

- [ ] DB 정합성 확인: 측정별 `current_members == 정원` & `COUNT(crew_members) == 정원` (정원초과 0건)
- [ ] `results/11_conditional-update-comparison.md` 작성 (🤖): A/B/C 비교표 — 정합성·p95·`join_dropped`·`join_5xx`·`join_dup`, 측정/파생/추정 3단 라벨
- [ ] 가설 검증: C가 비관락 conn_reset(0)도, 낙관락 p95·재시도도 없애는가
- [ ] raw 21세트(`results/raw/`) 보존 확인
- [ ] `./scripts/stop-monitoring.sh` 로 모니터링 종료
- [ ] EC2 **중지** (과금 방지) 👤
- [ ] ⚠️ **prod 배포 금지** 재확인 — 전략 C·정원100은 `feat/load-test` 한정

---

## 검증 기준 (신뢰성)
- 정합성: 모든 측정 `join_success == 정원`, DB 정원초과 0건. `join_dup>0`이면 토큰 부족 오염(100명 벤치 점검)
- 안정성: `join_5xx == 0`. `join_dropped`는 측정값(전략별 연결 실패)
- 비교 유효성: A/B/C 동일 환경·데이터·스크립트 → 표 대칭. raw 보존으로 재현 가능
