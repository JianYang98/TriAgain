# 크루 가입 러시 부하테스트 — 자동 측정일 작업지시서 (재사용)

> **목적**: 측정 당일, 에이전트가 **자동 러너**로 k6 스윕 블록을 무인 실행하고(리셋→발사→기록→중단조건), 세션 종료 시 **등식 검증 세트**로 데이터 무결성을 마감하는 절차.
> **포지셔닝**: 환경 셋업 = `CREW-RUSH-DOCKER-환경-셋업-런북.md` → **[이 문서 = 측정 당일 러너 운영]** → 사후 분석 3종 = `CREW-RUSH-분석-작업지시서.md`. 그 두 문서와 겹치는 내용은 참조로 대체한다.
> **실증 사례(포맷·수준 정본)**: 2026-07-13 Docker 프로드재현 측정일 — C15/C16(PESSIMISTIC, PRE_GC off/on 블록쌍) + A9/A10(CONDITIONAL, off/on 블록쌍), 두 세션 모두 세션 등식 0오차 마감. 산출물 = `results/0713/`.
> **담당**: 👤 사용자(컨테이너 기동·전략 결정·박스 3채널 가동·회수) · 🤖 에이전트(러너 작성/발사/모니터링·등식 검증·Prometheus 회수·문서)

---

## 0. 사용자가 새 세션에서 붙여넣을 메시지 (템플릿)

```
자동 측정일이야. CREW-RUSH-자동측정-작업지시서.md 따라줘.
- 박스: <public IP> (컨테이너 triagain-loadtest, 전략=<PESSIMISTIC|CONDITIONAL> · accept-count=<256>)
- 세션명: <예: C17(off)/C18(on)> · 블록 설계: PRE_GC <off→on 블록쌍 | 단일 arm>
- 실행순 셔플: 20→400→50→300→100→500→200→700 (시리즈 표준)
- 워밍업: <새 JVM=3런 | 재개=브릿지 N런> · arm=<on/off 조합>
- TEST_DB는 내가 env로 줄게 (파일·stdout 금지)
러너는 네가 만들어서 백그라운드로 돌리고, 런마다 보고해줘.
```

- 전략 라벨·accept-count는 **👤 확정 게이트** — 서버로그에 안 찍히므로(0713 실측: boot 로그에 전략 배너 없음) `docker inspect -f '{{.Args}}'` 캡처 또는 사용자 확답 없이 세션 라벨을 못 박는다.

---

## 1. 블록 실험 설계 (0713 실증 프로토콜)

### 셔플 실행순 (고정)

```
20 → 400 → 50 → 300 → 100 → 500 → 200 → 700
```
- 시리즈 표준 셔플 — 드롭의 "순서무관·VU수준 결정" 검증축 유지 + `*_max` 워터마크 이월 캐비앗 통제.
- 블록쌍 비교(off↔on, 전략↔전략)는 **같은 셔플**이어야 런 위치 교란이 상쇄된다.

### PRE_GC off/on 블록쌍 (게이트 오염제거 증명 설계)

- **off 블록 8런 → on 블록 8런** 순서로 한 세션(같은 JVM) 안에서 연속 실행.
- 검증 논리: off 블록에서 자연 Major GC(`MarkSweepCompact/Allocation Failure`)가 런 중 발생하는지 vs on 블록(매 런 시작 게이트 `System.gc()`)에서 0인지.
- 0713 실증: C15(off) vu700 정중앙 192ms · A9(off) vu500 정중앙 236ms 각 1회 vs C16·A10(on) 자연GC 0 — **전략 교차 2쌍 완성**.
- ⚠️ off/on 블록은 힙 초기조건이 달라 **락 전략 비교 통제쌍으로 섞지 말 것**(분석지시서 §3과 동일 규칙). 전략 비교는 같은 arm끼리(C15↔A9, C16↔A10).

### 워밍업 규칙

| 상황 | 워밍업 | 근거(0713 실증) |
|---|---|---|
| 새 JVM (기동 직후) | **3런** (예: vu10/50/200, 버림) | JIT·경로 데우기. C15/C16 세션 = 웜업 4런, A밤 = 3런 |
| 같은 JVM, 장기 휴지(~1.5h) 후 재개 | **브릿지 1~3런** (버림) | JIT는 유휴로 안 사라짐(재워밍업 3런 불필요). 단 ①Hikari 커넥션 maxLifetime(30분)으로 전량 세대교체 ②vu20 첫 런의 콜드 민감성 ③블록쌍 간 갭 비대칭 — 을 1~3런으로 브릿지. A10 재개 시 vu10/50/200 3런 사용 |
| 휴지 없음(블록 연속) | 불필요 | C15→C16, A9→A10 모두 워밍업 없이 연속 |

- **워밍업 arm 선택이 System.gc() 등식을 바꾼다**: on 워밍업 1런 = 기대값 +1. 발사 전에 세션 기대값을 먼저 계산해 적어둘 것 (§6). on 블록 직전 워밍업은 힙시계에 영향 없음(본블록 매 런 게이트가 청소).

### 케이던스 (균일 고정)

```
런 종료 → +2분(sleep 110 + 러너 내부 sleep 10) → 07_rush_reset → 발사
```
- 전 구간(워밍업·블록 내·블록 간) 동일 — t3/t4g CPU 크레딧·TIME_WAIT 배수 등 런 간 갭 교란 통제.
- 8런 블록 ≈ 15~17분, 워밍업 3 + 8런 ≈ 25~30분.

---

## 2. 러너 스크립트 패턴 (부록 A가 전문 — 0713 A10 실전판)

핵심 구조 (전문은 부록 A, 세션마다 경로·IP·세션명만 갈아끼움):

- **`set -u` (⛔ `-e` 아님)** — k6 exit 99(드롭 threshold 실패)는 정상 진행이어야 하므로 `-e`면 러너가 죽는다. 중단조건은 명시적으로만.
- **자격증명**: `: "${TEST_DB:?TEST_DB env 필요}"` — 실행 시점 env 주입만. **파일에 기록 절대 금지** (§8).
- **`run_one <ARM> <VU> <TAG> <MD> <RUNLOG> <RAW>` 함수** — 런 1회의 전 사이클:
  1. (첫 런 제외) `sleep 110` — 케이던스 갭
  2. **reset**: 박스 경유 psql (§4 함정 참조) — 실패 시 즉시 ABORT
  3. **GC 스냅샷(전)**: 로컬 Prometheus `jvm_gc_pause_seconds_count`를 gc/cause 태그별로 1줄 캡처
  4. **k6 발사**: `k6 run --env BASE_URL=… --env TARGET_VUS=$VU --env MAX_MEMBERS=10 --env PRE_GC=$ARM --env RUN_TAG=$TAG --out json=$RAW k6/crew-rush-jian.js` → stdout을 임시파일로
  5. `sleep 10` — Prometheus 5s 스크레이프 반영 대기 → **GC 스냅샷(후)**
  6. **K6-클라 md append**: ANSI 스트립(`perl -pe 's/\e\[[0-9;]*m//g'`) 후 전문을 ```` ``` ````블록으로
  7. **박스라인 파싱**: `성공_success:` `5xx_err5xx:` `드롭_dropped_연결실패:` `^  p95:` awk 추출
  8. **arm 증빙 grep** (§4의 logrus 이스케이프 패턴 필수):
     - on → `grep -m1 -oE '\[pre-GC\].*success(\\)?":true[^"]*'`
     - off → `grep -m1 -o '\[pre-GC\] SKIPPED[^"]*'`
  9. **run-log append**: 1런 = 4줄(요약/arm증빙/GC전/GC후)
  10. **중단조건 검사** (하나라도 걸리면 `exit 1` + run-log에 `!! ABORT` 기록):

| 중단조건 | 판정 |
|---|---|
| arm 증빙 누락 | GATE 변수 빈 문자열 (on인데 success 라인 없음 / off인데 SKIPPED 없음 = 오설정 런) |
| health 다운 | `curl -sf -m 5 $BASE/actuator/health` 실패 |
| 정합성 실패 | `success≠10` 또는 `5xx≠0` |
| reset 실패 | ssh/psql 비정상 종료 |

- **k6 exit 99 = join_dropped>0 threshold = 정상 진행** (드롭은 커널층 노이즈, 고VU에서 기대됨). exit 코드는 기록만.
- **발사**: 러너를 백그라운드 태스크로 + **`caffeinate -i -w <러너PID>`** 부착(러너 종료 시 자동 해제, Mac 덮개는 열어둠). 런별 이벤트는 러너 stdout의 `[runner] … 완료:` 라인을 tail로 모니터링.
- **재개/부분 실행**: 본블록 도중 정지했다 재개할 땐 false-start 헤더에 정정 주석을 달고 재개 헤더를 새로 append (0713 A10 run-log 참조). 이미 돈 런은 유지, ORDER 오버라이드로 남은 VU만.

---

## 3. 측정일 체크리스트 (시간순)

### 발사 전

- [ ] 박스 생존: health UP + `process_start_time` (재기동 여부 — 같은 JVM인지가 워밍업 규칙을 결정)
- [ ] 전략·accept-count **👤 확정** + `docker inspect -f '{{.Args}}'` 캡처 (세션 라벨 증빙)
- [ ] 박스 3채널(nstat·ss·tcpdump) 가동 확인 — 로그 파일이 **현재 시각까지 실시간으로 자라는지** (tcpdump pcap은 무트래픽이면 안 자람 — 프로세스 생존으로 판정)
- [ ] 컨테이너 재생성 있었으면 **CPID 갱신 + 3채널 재기동** (§5)
- [ ] 로컬 Prometheus up=1 (target = 박스 public IP)
- [ ] TEST_DB 연결 검증: 박스 경유 `SELECT 1` (자격증명 stdout 미출력으로)
- [ ] tokens.csv 유효 (JWT_SECRET 동일 서버면 재발급 불필요)
- [ ] System.gc()·201 등 **세션 기대값 계산** 해두기 (§6)

### 실행 중 (런마다)

- [ ] success=10 · 5xx=0 · arm증빙 OK · drop/p95 기록 — 러너가 자동 판정, 에이전트는 보고만
- [ ] 자연 Major GC 발생 여부: run-log의 GC before/after에서 `MarkSweepCompact/Allocation Failure` 증가분 (cause 태그로 게이트GC와 완벽 분리 — "런 초입 +1" 휴리스틱 불필요)

### 종료 후

- [ ] §6 등식 검증 세트 전부
- [ ] Prometheus 회수 (§5) + README
- [ ] 👤 박스 3채널 정리·회수: **`docker rm` 전 `docker logs > boot-<전략>-<세션>-<날짜>.log` 필수** (json-file 로그는 rm과 함께 소멸 — 런북 Phase C② 캐비앗)
- [ ] 커널 채널 파일 세션명 접미사 리네임 → `results/<MMDD>/` (§7)

---

## 4. 실측된 함정 목록 (0713 전부 실제로 밟았거나 직전 회피)

| 함정 | 증상 | 정답 |
|---|---|---|
| **k6 파이프 실행 시 logrus 이스케이프** | TTY에선 `[pre-GC] {"success":true…}`인데 파이프(러너)에선 `msg="[pre-GC] {\"success\"…`로 바뀜 → TTY 패턴 grep이 실패, **게이트가 실제로 발사됐는데 증빙누락 ABORT** (C16 첫 런 false abort 실사례) | on 증빙 grep을 `'\[pre-GC\].*success(\\)?":true[^"]*'`로 (이스케이프 유무 모두 매치) |
| **Mac에 psql 없음** | 로컬에서 reset 불가 | 박스 경유: `ssh -i <KEY> <BOX> "psql …" < sql/07_rush_reset.sql` (로컬 SQL을 stdin 파이프) |
| **ssh 원격 env 미전파** | `TEST_DB=… ssh "psql \"\$TEST_DB\""` → 원격에서 빈 값(로컬 소켓 시도) | 원격 커맨드 문자열 안에 인라인: `ssh "$BOX" "TEST_DB='$TEST_DB'; psql \"\$TEST_DB\" -q"` |
| **zsh `echo ===`** | `=word` 해석으로 "== not found" | 구분선은 `printf '%s\n' ---` |
| **Flyway future 상태** | jar 최고 버전 < DB 이력 버전 → `*:missing`만으론 validate 실패 ("applied migration not resolved locally" 문구는 missing/future 공유라 구분 안 됨) | `'--spring.flyway.ignore-migration-patterns=*:missing,*:future'` (glob이라 따옴표 필수) — 런북 Phase C② [정정 07-13] 참조 |
| **다른 박스에서 docker 명령** | `docker logs \| grep`이 조용히 빈 출력 (grep이 "no such container" 에러를 삼킴) — 0713 실사례: 프롬프트가 `ip-172-31-2-204`(부하박스 아님)였음 | 명령 치기 전 **프롬프트 호스트명 확인**. 미확인 박스(프로드 가능성)에선 docker 명령 금지 |
| **Mac 잠들면 러너 중단** | 자리 비움 → 슬립 → k6/모니터링 정지 | `caffeinate -i -w <러너PID>` (러너에 묶어 자동 해제, 덮개는 열어둠) |
| **raw json 덮어쓰기** | 같은 TAG 재실행 시 무타임스탬프 json 덮임 | 분석지시서 §3 동일 — 재실행 전 TAG 확인, false start는 provenance 기록 |

---

## 5. 관측 채널 — 측정일 관점 (셋업 절차는 런북 Phase D가 정본)

- **박스 3채널은 컨테이너 netns 필수**: `sudo nsenter -t $CPID -n` 경유 (host 8080은 docker-proxy 가짜 큐). **컨테이너 재생성(전략 전환 등) = CPID 변경 = 3채널 전부 재기동** — 죽은 CPID로 돌던 채널은 조용히 무의미한 데이터를 쌓는다.
  - ⚠️ host `-i any` tcpdump는 DNAT 전/후 이중 캡처로 ISN 불변식이 깨진다 — 반드시 netns 안에서.
- **ss 커스텀 포맷** (0713 실전): 초당 `HH:MM:SS acceptQ=X/256 | LISTEN=1 ESTAB=N` — acceptQ 워터마크·ESTAB=도달+1~2 등식용 (+2는 on-arm 게이트 커넥션).
- **앱로그 GcTrigger 라인 = 게이트 GC 증빙 채널**: `[GcTrigger] 완료: <pause>ms, heap <before>MB → <after>MB` — 건수=System.gc() 기대값, 시각=런 시작 정렬 (0713: 12건, 12/12 정렬). Prometheus 카운터와 이중 증빙.
- **로컬 Prometheus 회수**: 기존 scrape/merge 스크립트(31종 세트) 복제 사용. ⚠️ **시간 창이 scrape 스크립트와 merge 스크립트 두 파일 모두에 하드코딩** — 재회수(창 확장) 시 **둘 다** 갱신해야 한다 (0713 실사례: scrape만 고쳐서 CSV가 구창 그대로 나옴). 회수 후 README 필수 (§7).
- **TSDB는 로컬 컨테이너 수명** — start-monitoring 재실행(타겟 변경)은 TSDB 초기화. 세션 중간(블록 사이) 회수를 먼저 해두면 안전 (0713: A9까지 1차 회수 → A10 후 전체창 2차).

---

## 6. 세션 마감 등식 검증 세트 (하나라도 안 맞으면 원인 규명 전 세션 확정 금지)

0713 A9+A10 세션(같은 JVM 22런) 실증치를 예시로:

| 등식 | 계산 | 0713 실증 |
|---|---|---|
| **201 = 런수×10** | 이 JVM에서 돈 모든 런(워밍업 포함)×정원 | 220 = 22×10 ✅ |
| **클린 409 = Σ(도달−10)** | 도달 = VU − drop, 런별 합산 | 4,023 ✅ (Prometheus 409 총계 4,024 − 유령 1) |
| **System.gc() = arm 설계 기대값** | on 런 수 (워밍업 arm 포함해 사전 계산) | 12 = w1 1 + A10웜업 3 + A10 8 ✅ |
| **GcTrigger 로그 건수·시각 = 런 시작 정렬** | 앱로그 grep | 12건, 12/12 정렬 (런 시작 +1초 내) ✅ |
| **drop = ΔTcpAttemptFails (+ΔEstabResets)** | nstat 런 창 델타 (창 = 시작−2s ~ 종료+15s) | 15/16 단독 일치 + A9 vu500 확장등식 119+1=120 (EstabResets +1 = 유령409 플로우) ✅ |
| **pcap In-RST = drop** | 드롭 런 창별 방향 카운트 | 7/7 ✅ |
| **pcap Out-RST = nstat SyncookiesFailed = nstat OutRst** | 3채널 교차 | 7/7 ✅ (잉여 = 이중RST 후보) |
| **ListenOverflows·ListenDrops** | 전 창 | 0 (쿠키실패 즉사형, backlog 무포화) ✅ |
| **5xx = 0** | Prometheus status=~"5.." | 0 ✅ |
| **유령409** | Micrometer `409+IOException` = 서버 CR002 − 클라 409 = EstabResets = pcap established RST | 1건 4중 관측 정합 ✅ |

- **서버측 버킷 per-run 산출**: `acquire_perrun_compute.py`(0709/A8/prometheus 원본, 0713/prometheus-a9a10에 복제본) 재사용 — 창 = 런시작−15s ~ 종료+22s, chk = acquire Δcount=도달×2 · join완료 Δ=도달. 러너 health check가 창 안에 들어 acq ±1~2 상수 오프셋 가능(퍼센타일 무영향, 정직하게 기록).
- **전략/arm 비교는 서버측 완료 p95만** — 클라 p95는 SYN 수립층 오염 (분석지시서 §5 동일 규칙).

---

## 7. 산출물 컨벤션

```
results/<MMDD>/
├── <세션명>/                      # 예: A10/
│   ├── K6-클라-<세션명>.md         # k6 stdout 전문 (런마다 ## TAG 섹션, ANSI 스트립)
│   ├── run-log-<세션명>.md         # 러너 자동 기록 (런 4줄: 요약/arm증빙/GC전/GC후)
│   └── raw/                       # crew-rush-jian_<TAG>.json ×8
├── <MMDD>_<블록명>-워밍업.md        # 워밍업 stdout (버림 런도 전문 보존)
├── raw/                           # 워밍업 raw + handleSummary HTML/summary.json(타임스탬프)
├── prometheus*/                   # 기동(컨테이너) 단위 폴더 — 31종 JSON + merged CSV + README 필수
│   └── acquire_perrun_compute.py  # 복제·창 수정본 (재현용 폴더 보존)
├── boot-<전략>-<세션쌍>-<날짜>.log   # docker logs 덤프 (rm 전 필수)
├── nstat-ts-<HHMMSS>-<세션쌍>.log   # 커널 채널 — 회수 시 세션명 접미사 리네임
├── ss-state-<HHMMSS>-<세션쌍>.log
└── tcpdump-<HHMMSS>-<세션쌍>.pcap
```

- prometheus README에 반드시: 윈도우(UTC)·기동 epoch/KST·세션 정체성(전략·accept-count·증빙 방식)·런 창·핵심 사실·캐비앗(5s 과소표집=`*_max`만 신뢰, 셔플 워터마크 이월).
- 결과서·분석 3종은 이 문서 범위 밖 — `CREW-RUSH-분석-작업지시서.md`로.

---

## 8. 금지선 (전 세션 공통, 0713 기준 유효)

- ⛔ **prod 배포 금지** — 전략/정원/설정 변경은 부하테스트 한정
- ⛔ **`:latest` push 금지** — 부하테스트 이미지는 `:loadtest` 태그만 (다음 prod 배포 오염 방지)
- ⛔ **`load-test/` git restore/checkout 금지** — 미커밋 개선분 상존 (되돌릴 땐 Edit 역적용만)
- ⛔ **운영 RDS 접속·부하 금지** — test RDS만
- ⛔ **자격증명(TEST_DB·JWT 등) stdout/파일/커밋 금지** — 실행 시점 env 주입만, 추출·검증도 화면 미출력으로
- 🛑 **전략 라벨·accept-count = 👤 사용자 확정 게이트** — 미확정이면 세션 라벨을 못 박고 진행 금지 (분석지시서 §3 동일)

---

## 부록 A — 러너 전문 (0713 A10 실전판 `run-a10.sh`)

> 세션마다 갈아끼울 것: BOX/BASE IP · 세션명(A10) · 날짜 폴더(0713) · TMP(작업용 임시 디렉토리) · ORDER · 워밍업 구성 · run-log 헤더.
> 이 판은 "같은 JVM 재개 + 워밍업 3런(전부 on) + on 블록 8런" 구성. off 블록이면 `run_one off …`로.

```bash
#!/bin/bash
# A10 단독 러너 — Docker·CONDITIONAL·accept-count256·PRE_GC=on
#   A9 종료 후 ~1.5h 휴지 재개판: 워밍업 3런(vu10/50/200 전부 on, 버림) → A10(on 8런)
#   순서(본블록): 20→400→50→300→100→500→200→700 (C15/C16/A9 동일 셔플)
#   케이던스: 런 종료 → +2분 → reset → 발사 (전 구간 균일)
# ⚠️ TEST_DB는 실행 시점 환경변수로만 주입 — 이 파일에 자격증명 기록 금지.
set -u
cd /Users/jian/Projects/triagain/triagain-back/load-test

: "${TEST_DB:?TEST_DB env 필요}"
KEY="$HOME/Downloads/triagain-key.pem"
BOX=ec2-user@13.125.7.72          # ← 세션별: 박스 public IP
BASE=http://13.125.7.72:8080      # ← 세션별
TMP=<작업용 임시 디렉토리>            # ← 세션별: k6 stdout·reset 로그 임시 저장소
WMD=results/0713/0713_A-워밍업.md
RUNLOG=results/0713/A10/run-log-A10.md
ORDER=(20 400 50 300 100 500 200 700)

mkdir -p results/0713/A10/raw

strip() { perl -pe 's/\e\[[0-9;]*m//g'; }

gc_snap() {
  curl -s --max-time 5 'http://localhost:9090/api/v1/query?query=jvm_gc_pause_seconds_count' \
    | jq -r '.data.result[] | "\(.metric.gc)/\(.metric.cause)=\(.value[1])"' | sort | paste -sd' ' -
}

reset_db() {
  ssh -i "$KEY" -o ConnectTimeout=10 "$BOX" "TEST_DB='$TEST_DB'; psql \"\$TEST_DB\" -q" < sql/07_rush_reset.sql
}

FIRST=1
# run_one <ARM on|off> <VU> <TAG> <MD경로> <RUNLOG경로> <RAWJSON경로>
run_one() {
  local ARM=$1 VU=$2 TAG=$3 MD=$4 RLOG=$5 RAW=$6

  if [ $FIRST -eq 0 ]; then
    echo "[runner] 다음 런까지 대기 (~2분 갭)..."
    sleep 110
  fi
  FIRST=0

  if ! reset_db > "$TMP/reset-last.out" 2>&1; then
    echo "!! ABORT: reset 실패 ($TAG 전)" | tee -a "$RLOG"
    tail -5 "$TMP/reset-last.out" >> "$RLOG"
    exit 1
  fi

  local GC_BEFORE START END RC GC_AFTER
  GC_BEFORE=$(gc_snap)
  START=$(date '+%T')
  k6 run --env BASE_URL=$BASE --env TARGET_VUS=$VU --env MAX_MEMBERS=10 \
    --env PRE_GC=$ARM --env RUN_TAG=$TAG \
    --out json="$RAW" \
    k6/crew-rush-jian.js > "$TMP/k6-$TAG.out" 2>&1
  RC=$?
  END=$(date '+%T')
  sleep 10   # Prometheus 5s 스크레이프 반영 대기
  GC_AFTER=$(gc_snap)

  {
    echo ""
    echo "## $TAG — $START ~ $END (k6 exit=$RC, PRE_GC=$ARM)"
    echo ""
    echo '```'
    strip < "$TMP/k6-$TAG.out"
    echo '```'
  } >> "$MD"

  local S E5 DR P95 GATE
  S=$(strip < "$TMP/k6-$TAG.out" | awk '/성공_success:/{print $2}')
  E5=$(strip < "$TMP/k6-$TAG.out" | awk '/5xx_err5xx:/{print $2}')
  DR=$(strip < "$TMP/k6-$TAG.out" | awk '/드롭_dropped_연결실패:/{print $2}')
  P95=$(strip < "$TMP/k6-$TAG.out" | awk '/^  p95:/{print $2}')
  if [ "$ARM" = "on" ]; then
    GATE=$(strip < "$TMP/k6-$TAG.out" | grep -m1 -oE '\[pre-GC\].*success(\\)?":true[^"]*' | head -1)
  else
    GATE=$(strip < "$TMP/k6-$TAG.out" | grep -m1 -o '\[pre-GC\] SKIPPED[^"]*' | head -1)
  fi
  {
    echo "- **$TAG** $START~$END exit=$RC arm=$ARM | success=$S 5xx=$E5 drop=$DR p95=${P95}ms"
    echo "  - arm증빙: ${GATE:-'!! 증빙 누락'}"
    echo "  - GC before: $GC_BEFORE"
    echo "  - GC after : $GC_AFTER"
  } >> "$RLOG"

  echo "[runner] $TAG 완료: success=$S 5xx=$E5 drop=$DR p95=${P95}ms exit=$RC arm=$ARM(${GATE:+증빙OK}${GATE:-증빙누락})"

  if [ -z "$GATE" ]; then
    echo "!! ABORT: arm 증빙 누락 ($TAG, ARM=$ARM)" | tee -a "$RLOG"; exit 1
  fi
  if ! curl -sf -m 5 $BASE/actuator/health > /dev/null; then
    echo "!! ABORT: health 다운 ($TAG 후)" | tee -a "$RLOG"; exit 1
  fi
  if [ "${S:-x}" != "10" ] || [ "${E5:-x}" != "0" ]; then
    echo "!! ABORT: 정합성 실패 $TAG (success=$S, 5xx=$E5)" | tee -a "$RLOG"; exit 1
  fi
}

# ── (재개판 전용) 기존 false-start 헤더 정정 + 재개 헤더
{
  echo "> ⚠️ 위 19:19:35 헤더는 false start(사용자 지시로 발사 전 정지, 런 0개) — 아래 재개분이 정본."
  echo ""
  echo "# A10 스윕(재개) — $(date '+%F %T') 시작 (순서: ${ORDER[*]}) | CONDITIONAL·256·PRE_GC=on | 같은 JVM(18:22:49 기동)·A9 종료 후 ~1.5h 휴지 → 워밍업 3런(vu10/50/200 전부 on) 선행"
} >> "$RUNLOG"

# ── 워밍업 3런 (버림, 전부 on — 사용자 지정. System.gc() 등식 12 = w1 1 + 워밍업 3 + A10 8)
echo "" >> "$WMD"
echo "# A10 워밍업 — $(date '+%F %T') (~1.5h 휴지 후, vu10/50/200 전부 PRE_GC=on, 버림)" >> "$WMD"
run_one on 10  A10_warmup1_vu10  "$WMD" "$WMD" results/0713/raw/A10_warmup1_vu10.json
run_one on 50  A10_warmup2_vu50  "$WMD" "$WMD" results/0713/raw/A10_warmup2_vu50.json
run_one on 200 A10_warmup3_vu200 "$WMD" "$WMD" results/0713/raw/A10_warmup3_vu200.json
echo "[runner] ── 워밍업 3런 종료, A10(on) 진입 ──"

# ── A10 (PRE_GC=on)
for VU in "${ORDER[@]}"; do
  run_one on "$VU" "A10_max10_vu$VU" results/0713/A10/K6-클라-A10.md "$RUNLOG" \
    "results/0713/A10/raw/crew-rush-jian_A10_max10_vu$VU.json"
done

echo "[runner] ✅ A10 완료 (워밍업3+A10 8) — $(date '+%F %T')" | tee -a "$RUNLOG"
```

발사 패턴 (자격증명 화면 미출력):
```bash
TEST_DB='<사용자가 제공>' bash run-<세션>.sh   # 백그라운드 태스크로
RPID=$(pgrep -f 'run-<세션>.sh' | head -1) && nohup caffeinate -i -w "$RPID" >/dev/null 2>&1 &
```

---

_생성: 2026-07-13 · 실증 = C15/C16(PESSIMISTIC off/on)·A9/A10(CONDITIONAL off/on) 4블록 + 워밍업, 세션 등식 0오차 2세션 · 관계 문서 = 셋업 런북(전)·분석 지시서(후)_
