# CREW-RUSH — Serial↔G1 GC 전환 통제쌍 실험계획서

> **상태: 설계 확정(2026-07-13) — §2 착수 조건 판정 + 기준선 재사용(G1-only) 개정 반영. 측정일 실행 절차 = `CREW-RUSH-자동측정-작업지시서.md`.**
> 07-10 사용자 결정([Issue #97](https://github.com/JianYang98/TriAgain/issues/97))의 실행 레퍼런스.
> Docker 전환(`CREW-RUSH-DOCKER-환경-셋업-런북.md`) 이후 환경 기준으로 작성 —
> **Docker 시대 G1 기동 방법은 이 문서 §3이 정본** (`04-loadtest-pre-gc-gate.md` §G1의 bare 명령은 Docker에선 무효).

---

## §0 TL;DR

- 목적: "런에 겹치던 150~280ms Serial Full GC pause"가 G1으로 완화되는지 실증.
  **[07-13 개정]** Serial 기준선 = 0713 4블록(C15/C16 PESS·A9/A10 COND) **재사용** — 새 측정일은
  **G1 세션 2개만**(①C17/C18 PESS → ②A11/A12 COND, off→on 블록쌍 0713 완전 미러). 비교는 **교차밤** — §5 캐비앗·2단 구조 필수.
- 판정: 완료요청 p95(서버측 버킷) + jvm_gc_pause 분포에서 개선 실증 → prod 반영 검토 정당화. 아니면 **무변경(Serial 유지)**.
- 원가설(자연GC pause 완화) 검증 축 = **off 블록** — 0713 실측: 게이트 on이면 자연GC 0/0 (§2 판정).

---

## §1 가설·판정 프레임 (정직하게)

- 기존 15차수 데이터상 병목은 GC가 아니다 — **TCP 수립 천장(드롭, 커널층) + HikariCP acquire(DB 직렬화)**.
  드롭 개시 VU·드롭 수는 전략무관·콜렉터무관 커널층 현상으로 이미 확정 (07-04 A1↔C7 3점 등).
- G1이 건드릴 수 있는 유일한 것: **런 창에 겹치던 Serial Full GC(MarkSweepCompact) 150~280ms STW pause**
  (예: C11 vu700 185ms, A5 vu700 3연속 겹침, A8 vu700 200ms).
- 측정 지표 (판정은 이 둘로만):
  1. **완료요청 p95 — 서버측 버킷** (C12/A5부터 산출 가능해진 채널. 클라측은 connecting 오염)
  2. **jvm_gc_pause 분포** — 런 창별 `*_max` 워터마크 + count/sum 카운터 델타.
     ⚠️ 5s 스크레이프는 <1s 버스트 과소표집 — 게이지 말고 워터마크·카운터 델타만 신뢰.
- 판정 규칙:
  - G1이 런겹침 pause를 실측으로 줄이고 해당 런 p95가 개선 → prod 반영 검토를 정당화할 근거 확보.
  - 차이 없음 또는 악화 → **무변경** (JVM 에르고노믹스의 Serial 선택 존중). "해봤는데 아니었다"도 유효한 결과.
- ⛔ **인스턴스 사이즈 스윕과 같은 세션에 섞지 않는다** — 둘 다 "개선 실증 시에만 반영" 프레임의 별개 실험. 2변수 금지.

## §2 착수 조건 (둘 다 충족 시)

1. **Docker+Serial 기준선 세션 완료** — bare→Docker 전환 자체가 새 변수다(힙 512m → **ergonomic ~256MB**,
   GC 빈도·pause 기저가 이미 달라짐). 이 기준선이 없으면 G1 쌍의 비교 대상이 없다.
2. 그 기준선에서도 **런중 GC 겹침 잔존** 관측 — 특히 vu700 (런 자체 할당 압력으로 pre-GC 게이트로도 못 막는 구간).

- 분석 규칙 (게이트 세션 공통): 런 창 **초입 major +1은 기본값**(pre-GC 귀속) — 겹침 판정은 초입 이후 증가분만.

> **[판정 2026-07-13]** 조건① **충족** — 0713 Docker+Serial 4블록(C15/C16 PESS·A9/A10 COND, off/on 블록쌍,
> 세션 등식 0오차 2세션) 완료. 조건② **on-arm 미충족** — on 블록 자연 Major GC **0/0**(C16·A10 각 8런),
> 자연GC는 **off 블록에서만** 각 1회(C15 vu700 정중앙 192ms · A9 vu500 정중앙 236ms). 즉 게이트가 겹침을 이미 제거
> → **원가설 검증 축은 off 블록**이고, on 블록 비교는 "G1 기저 오버헤드" 측정으로 재프레임 (§5 개정).
> 결과서 = `results/0713/0713_preGC게이트-C15C16-결과.md` · `results/0713/0713_CONDITIONAL-A9A10-결과.md`

## §3 Docker에서의 G1 주입 방법 — 이 문서가 정본

bare 시절과 다르다: 이미지명 뒤 트레일링 인자는 **Spring program-arg**로 가서 JVM 플래그가 **조용히 무시**된다
(에러도 없음). JVM 플래그는 **`-e JAVA_TOOL_OPTIONS`** 환경변수로 주입한다 (ENTRYPOINT `java`가 자동 픽업).

```bash
# G1 런 — Docker 런북 Phase C ②에서 -e JAVA_TOOL_OPTIONS 한 줄만 추가, 나머지 동일
docker rm -f triagain-loadtest 2>/dev/null || true
docker run -d --name triagain-loadtest \
  -p 8080:8080 \
  -e JAVA_TOOL_OPTIONS="-XX:+UseG1GC" \
  -e DB_URL="jdbc:postgresql://<TEST_RDS_HOST>:5432/triagain" \
  -e DB_USERNAME="<test>" -e DB_PASSWORD="<test>" \
  -e JWT_SECRET="<jwt>" \
  devjian/triagain:loadtest \
  --spring.profiles.active=prod,loadtest \
  --triagain.crew.lock-strategy=<PESSIMISTIC|CONDITIONAL — 세션별, §5 표> \
  --server.tomcat.accept-count=256 \
  '--spring.flyway.ignore-migration-patterns=*:missing,*:future'
```

- [정정 07-13] flyway 패턴에 `*:future` 추가 — jar 최고 버전 < DB 이력 버전이면 `*:missing`만으론 validate 실패
  (런북 Phase C② [정정 07-13]과 동기화. glob이라 따옴표 필수).

- **Serial 런 = 같은 명령에서 `-e JAVA_TOOL_OPTIONS=...` 줄만 제거.** 전환은 전략 전환과 동일하게 `docker rm` 후 재생성.
- ⛔ **`-Xmx`/`--memory` 금지 유지** — ergonomic ~256MB가 Docker 재현의 핵심 (런북 Phase C ② 규칙 그대로).
- 이미지는 `:loadtest` 동일 (feat/load-test 직접 빌드분, ⛔`:latest` push 금지).

**적용 확인 3채널** (세션 시작 전 필수):

1. `docker logs triagain-loadtest 2>&1 | head -3` → `Picked up JAVA_TOOL_OPTIONS: -XX:+UseG1GC`
2. pre-GC 게이트 발동 직후 Prometheus `jvm_gc_pause` 태그: G1=`gc="G1 Old Generation"` / Serial=`gc="MarkSweepCompact"`
   — **게이트의 System.gc()가 세션 초두에 콜렉터 지문을 강제 생성**해주는 부수효과. 세션 비교 전 콜렉터 동일성 확인 축.
3. (가능 시) `docker exec triagain-loadtest jcmd 1 VM.flags | grep UseG1GC` — jre-alpine에 jcmd 부재 가능, 1·2로 충분.

## §4 알려진 함정 (전부 계획서에 선등재)

- **pre-GC 게이트는 G1에서도 그대로 유효** — `System.gc()`는 G1에서도 기본 동기 Full GC.
  ⛔ **`-XX:+ExplicitGCInvokesConcurrent` 절대 금지** — 게이트가 비동기화돼 무력화된다.
- **~256MB 힙 + G1은 이례 조합** — JVM이 Serial을 고른 이유가 바로 작은 머신(에르고노믹스 G1 기준 ≈1792MB 미달).
  2vCPU에서 동시 마킹·remembered set 오버헤드로 **완료 p95 기저가 오히려 악화될 수 있음** — 결과 해석 가능성에
  미리 등재해둬야 "G1인데 왜 느려짐?"이 아니라 "예상된 갈래 중 하나"로 보고된다.
- **락 전략 비교는 동일 콜렉터 세션끼리만** — G1 세션을 기존 Serial 차수(~C13/A8/C12/A5 등)와 직접 비교 금지.
  콜렉터가 다르면 완료 p95 기저 자체가 이동한다. 기존 "조건부 우세" 결론은 Serial 세션 내부 비교라 유효.
  (Serial↔G1 **콜렉터 비교**만 예외적으로 교차밤 허용 — §5 캐비앗 세트 필수 병기.)
- **RUN_TAG에 콜렉터 명시** [확정형 07-13]: `TAG=<세션명>_<pess|cond>_g1_max10_vu<VU>`
  (예: `C17_pess_g1_max10_vu700`) + 세션 README 배너에 콜렉터 기록
  — raw A/B/C 태그 도치 재앙(전략 라벨 소급 고증 15차수) 재발 방지. 콜렉터는 라벨 없이도 gc 태그로 소급 판별
  가능하지만, 라벨을 처음부터 박는 게 싸다.
- **on-first 순서뒤집기 후속(0713 결과서 "최우선 후속")은 이 설계에 미흡수·잔존** — off→on 순서를 0713과
  맞추는 게 교차밤 비교의 전제라서 여기선 못 섞는다(2변수 금지). 별도 Serial 밤 과제로 유지.
- **기동 명령 전문 기록 규율** (ABC 런북 Phase 4.5, C10 무기록 조치 재발 방지):
  `docker run` 전문 + `docker inspect -f '{{.Config.Env}}' triagain-loadtest` 캡처(JAVA_TOOL_OPTIONS 증빙)를
  세션 폴더에 저장.

## §5 실험 설계 — G1-only 측정일, 0713 Serial 기준선 재사용 미러 [2026-07-13 개정]

> 원안("같은 밤 Serial→G1 2세션")에서 개정: 0713에 Docker+Serial 4블록 기준선(C15/C16·A9/A10, 세션 등식
> 0오차 2세션)이 이미 확보돼 **Serial 재측정 없이 G1 세션만** 돌린다(👤 07-13 결정). 대가 = **교차밤 비교**
> — 아래 캐비앗 세트 선등재 + 2단(타이브레이커) 구조로 보정.

**새 측정일 = G1 세션 2개** (세션 = 컨테이너 기동 1회 = 웜업 3런(on, 버림) + off 8런 + on 8런):

| 순서 | 세션(블록명) | 전략 | 콜렉터 | 0713 대응 기준선 |
|---|---|---|---|---|
| ① | **C17**(off) → **C18**(on) | PESSIMISTIC | G1 | C15(off) / C16(on) |
| ② | **A11**(off) → **A12**(on) | CONDITIONAL | G1 | A9(off) / A10(on) |

- **0713 완전 동형 미러**: 같은 셔플(`20→400→50→300→100→500→200→700`)·케이던스(~2분)·accept-count=256·
  MAX_MEMBERS=10·클라넷 집·매 런 reset(`sql/07_rush_reset.sql`)·**세션 순서까지 미러**(PESS 먼저 → COND 나중
  — 밤 내 성숙 위치 정렬. A9 내부 acquire 사다리 감쇠 = 밤-성숙 교락의 실측 증거).
- ⚠️ **세션 번호**: C15/C16은 0713에서 사용됨 → PESS G1 쌍은 **C17/C18**(다음 빈 번호, 07-13 grep 미사용 확인.
  자동측정 지시서 §0 템플릿의 C17/C18은 예시 자리표시자). 세션명 재사용 절대 금지(07-09 C6 동명충돌 사건).
- 재기동 사이 JVM 새로 뜸 → 세션 첫 GC 교란은 웜업(on) + pre-GC 게이트가 흡수 (§2 분석 규칙 적용).
  웜업 첫 런의 게이트 GC가 **콜렉터 지문(§3 확인 채널 2)을 세션 초두에 강제 생성** — off 본블록 진입 전 확인 완료 가능.
- 측정 절차·관측(nsenter nstat/ss·tcpdump·Prometheus 창 회수)은 자동측정 지시서 + Docker 런북 Phase D~E 그대로.

### 비교축 (전부 같은 위치 블록끼리)

| 축 | 쌍 | 내용 |
|---|---|---|
| **원가설: 자연GC pause 완화** | C15↔C17 · A9↔A11 (off끼리) | off 블록 자연 Major GC 발생 시 Serial(192~236ms Full GC) vs G1 거동. ⚠️자연GC는 세션당 1회 관측된 확률 사건 — G1 off에서 미발생이면 "이번 밤 미검출"로 정직 기재 |
| **기저 완료 p95** | C16↔C18 · A10↔A12 (on끼리) | 서버측 버킷 완료 p95만 (§1 판정 지표 유지). young GC 프로파일(Copy↔G1 Evacuation Pause 빈도·pause 분포)도 병기 |
| **게이트 G1 유효성** | C17↔C18 · A11↔A12 (세션 내) | §4 "System.gc()는 G1에서도 동기 Full GC" 실증 — off에서 자연GC·on에서 0 재현 여부 |

### 교차밤 캐비앗 (선등재 — 결과서·비교문서에 그대로 옮길 것)

1. **Serial↔G1 비교는 전부 교차밤** — 같은 밤 통제쌍 아님(C10↔A2 교차밤 퍼즐 전례). 방향·크기 해석 시
   밤간 노이즈 봉투 병기 필수: 기존 Serial COND 다밤 세션(A5·A8·A9/A10)의 완료 p95 산포가 참고 앵커.
2. **2단 구조**: G1 효과가 밤간 노이즈 봉투 안이면 **판정 유보** → 같은 밤 Serial↔G1 타이브레이커 쌍(원안 구조)으로만 확정.
   봉투를 명확히 벗어나는 결과(양 전략 동일 방향이면 가중)는 이번 설계로 판정 가능.
3. **미러 편차 정직 기재**: 0713 A9→A10 사이 1.5h 휴지+브릿지 3런은 재현 안 함(A11→A12 연속 진행) —
   A10은 추가 성숙 후 측정된 블록이라 A12와 밤 내 위치 미세 불일치. C15→C16은 연속이었으므로 C쌍은 이 편차 없음.
4. 드롭·커널층(개시 VU·AttemptFails 등)은 전략무관·콜렉터무관 확정 사항 — 대조군으로만 기재, 판정 미사용.

### 세션 기대값 (자동측정 지시서 §6 등식 세트 사전 계산)

- 201 = **190**/세션 (19런×10: 웜업 3 + 본 16)
- System.gc() = **11**/세션 (웜업 on 3 + on 블록 8, off 블록 0) · GcTrigger 로그 11건 런 시작 정렬
- 규모: 본런 32 + 웜업 6 = 38런 ≈ 세션당 ~40분 + 전환(컨테이너 재생성 = CPID 갱신 = 3채널 재기동) 10~15분 → **약 2~2.5h**

### 측정일 붙여넣기 템플릿 (자동측정 지시서 §0 형식)

```
자동 측정일이야. CREW-RUSH-자동측정-작업지시서.md 따라줘. G1 세션이니 CREW-RUSH-G1GC-통제쌍-실험계획서.md §3·§5도.
- 박스: <public IP> (컨테이너 triagain-loadtest, 전략=PESSIMISTIC · accept-count=256 · JAVA_TOOL_OPTIONS=-XX:+UseG1GC)
- 세션명: C17(off)/C18(on) · 블록 설계: PRE_GC off→on 블록쌍 (0713 미러)
- 실행순 셔플: 20→400→50→300→100→500→200→700 (시리즈 표준)
- 워밍업: 새 JVM=3런(on, 버림)
- TEST_DB는 내가 env로 줄게 (파일·stdout 금지)
러너는 네가 만들어서 백그라운드로 돌리고, 런마다 보고해줘.
C17/C18 마감 후 CONDITIONAL로 컨테이너 재생성(G1 유지) → A11(off)/A12(on) 동일 진행.
```

- 측정일 사전 특이사항: **EC2·RDS 재기동 필요**(현재 중지) — 새 public IP면 러너 BOX/BASE·Prometheus 타겟 갱신
  (타겟 변경 = TSDB 초기화 주의, 세션 사이 중간 회수 우선)·tokens.csv 재확인(JWT_SECRET 동일 env 주입이면 재발급 불필요).
  **부팅 1순위 `docker ps`** — 0713 프로드컨테이너 클린업 미확인 건 확인부터.

## §6 측정·산출물

- 산출물 구조 = 자동측정 지시서 §7 컨벤션 그대로: `results/<MMDD>/` 아래 세션 폴더(C17·C18·A11·A12) +
  **prometheus 기동 단위 2폴더**(예: `prometheus-c17c18`·`prometheus-a11a12`, README에 콜렉터·전략·기동명령 증빙 필수) +
  boot 로그 2본(`docker rm` 전 덤프) + 커널 채널 세션쌍 접미사 리네임.
- 세션별 기존 3종 분석: **결과서·로그상태 분석서·tcpdump 분석서** — `CREW-RUSH-분석-작업지시서.md` 절차 재사용
  (전략 라벨 👤 게이트에 **콜렉터 라벨 👤 확인** 추가 — `Picked up JAVA_TOOL_OPTIONS` boot 로그 + gc 태그 지문 이중 증빙).
- 최종: **Serial↔G1 비교문서** — §5 비교축 3종(4쌍 교차밤 + 2쌍 세션 내) 버킷 기반 완료 p95 비교표 +
  런 창별 gc_pause max/count 델타 대조 + 겹침 런 유무 + **밤간 노이즈 봉투(A5·A8·A9/A10 산포) 병기**.
  비교는 완료요청 지표로만(드롭·커널층은 전략무관·콜렉터무관 — 대조군으로만 기재).
- 판정 결론 1줄을 Issue #97에 회신하고 닫는다 — 단 §5 캐비앗 2(봉투 안이면 판정 유보·타이브레이커 필요)면
  회신을 "유보 + 후속 쌍 계획"으로 (개선 실증 → prod 반영은 별도 Tier 3 트랙).

## §7 참조

- 결정 기록: [Issue #97](https://github.com/JianYang98/TriAgain/issues/97) · `TODO/TODO-추후-07-10-G1GC-전환-통제쌍.md` (루트 repo)
- **측정일 실행 절차(정본): `CREW-RUSH-자동측정-작업지시서.md`** — 러너 패턴(부록 A)·체크리스트·등식 세트·함정 목록
- Serial 기준선(0713): `results/0713/0713_preGC게이트-C15C16-결과.md` · `results/0713/0713_CONDITIONAL-A9A10-결과.md`
- 트레이드오프·게이트 상세: `docs/fix-instructions/04-loadtest-pre-gc-gate.md` §"G1 GC 전환" (루트 repo — bare 명령은 구식)
- 환경 셋업: `CREW-RUSH-DOCKER-환경-셋업-런북.md` Phase C(기동)·D(관측)·E(측정)
- 측정·기록 규율: `CREW-RUSH-ABC-RUNBOOK.md` Phase 4.5(기동명령 전문)·Phase 5(태깅·리셋)
- Serial GC 증거: `results/0707/prometheus/gc_pause_max.json` 등 (gc="Copy"/"MarkSweepCompact"만, "G1" 0건)
