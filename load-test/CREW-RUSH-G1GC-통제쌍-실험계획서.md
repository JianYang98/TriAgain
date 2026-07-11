# CREW-RUSH — Serial↔G1 GC 전환 통제쌍 실험계획서

> **상태: 작성만 완료(2026-07-11) — 실행은 §2 착수 조건 충족 시.**
> 07-10 사용자 결정([Issue #97](https://github.com/JianYang98/TriAgain/issues/97))의 실행 레퍼런스.
> Docker 전환(`CREW-RUSH-DOCKER-환경-셋업-런북.md`) 이후 환경 기준으로 작성 —
> **Docker 시대 G1 기동 방법은 이 문서 §3이 정본** (`04-loadtest-pre-gc-gate.md` §G1의 bare 명령은 Docker에선 무효).

---

## §0 TL;DR

- 목적: "런에 겹치던 150~280ms Serial Full GC pause"가 G1으로 완화되는지 **같은 밤 Serial↔G1 통제쌍**으로 실증.
- 판정: 완료요청 p95 + jvm_gc_pause 분포에서 개선 실증 → prod 반영 검토 정당화. 아니면 **무변경(Serial 유지)**.
- 착수 조건: **Docker+Serial 기준선 세션**(pre-GC 게이트 on)에서도 런중 GC 겹침이 남을 때만.

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
  --triagain.crew.lock-strategy=<전략 — §5에서 고정> \
  --server.tomcat.accept-count=256 \
  '--spring.flyway.ignore-migration-patterns=*:missing'
```

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
- **RUN_TAG에 콜렉터 명시**: `TAG=<전략>_<serial|g1>_max<정원>_vu<VU>` + 세션 README 배너에 콜렉터 기록
  — raw A/B/C 태그 도치 재앙(전략 라벨 소급 고증 15차수) 재발 방지. 콜렉터는 라벨 없이도 gc 태그로 소급 판별
  가능하지만, 라벨을 처음부터 박는 게 싸다.
- **기동 명령 전문 기록 규율** (ABC 런북 Phase 4.5, C10 무기록 조치 재발 방지):
  `docker run` 전문 + `docker inspect -f '{{.Config.Env}}' triagain-loadtest` 캡처(JAVA_TOOL_OPTIONS 증빙)를
  세션 폴더에 저장.

## §5 실험 설계 — 같은 밤 통제쌍

**같은 밤 2세션**: ① Docker+Serial → ② Docker+G1. 컨테이너 재생성만, 이미지·박스·RDS·클라넷 동일.

| 변수 | 값 | 비고 |
|------|-----|------|
| 콜렉터 | Serial → G1 | **유일한 변경 변수** |
| 락 전략 | 1개 고정 — 권장 **CONDITIONAL**(채택 전략) | 👤 실행 시 확정 |
| VU 스윕 | 8런 완전스윕 vu20~700, 셔플 | 양 세션 동일 순서 권장(순서까지 통제) |
| accept-count | 256 | 양 세션 동일 |
| PRE_GC | on | 양 세션 동일 (락 비교 규칙과 동일 원칙) |
| 클라넷 | 동일 장소(집) | A3/A4 교훈 — 클라넷이 발현형을 바꾼다 |
| DB | 매 런 전 `sql/07_rush_reset.sql` | 런북 Phase E 그대로 |

- 재기동 사이 JVM 새로 뜸 → 세션 첫 GC 교란은 pre-GC 게이트가 흡수 (§2 분석 규칙 적용).
- 측정 절차·관측(nsenter nstat/ss·tcpdump·Prometheus 창 회수)은 Docker 런북 Phase D~E 그대로.

## §6 측정·산출물

- 세션별 기존 3종 분석: **결과서·로그상태 분석서·tcpdump 분석서** — `CREW-RUSH-분석-작업지시서.md` 절차 재사용
  (전략 라벨 👤 게이트에 **콜렉터 라벨 👤 확인** 추가).
- 최종: **Serial↔G1 비교문서** — 버킷 기반 완료 p95 비교표(7~8런) + 런 창별 gc_pause max/count 델타 대조 +
  겹침 런 유무. 비교는 완료요청 지표로만(드롭·커널층은 전략무관·콜렉터무관 — 대조군으로만 기재).
- 판정 결론 1줄을 Issue #97에 회신하고 닫는다 (개선 실증 → prod 반영은 별도 Tier 3 트랙).

## §7 참조

- 결정 기록: [Issue #97](https://github.com/JianYang98/TriAgain/issues/97) · `TODO/TODO-추후-07-10-G1GC-전환-통제쌍.md` (루트 repo)
- 트레이드오프·게이트 상세: `docs/fix-instructions/04-loadtest-pre-gc-gate.md` §"G1 GC 전환" (루트 repo — bare 명령은 구식)
- 환경 셋업: `CREW-RUSH-DOCKER-환경-셋업-런북.md` Phase C(기동)·D(관측)·E(측정)
- 측정·기록 규율: `CREW-RUSH-ABC-RUNBOOK.md` Phase 4.5(기동명령 전문)·Phase 5(태깅·리셋)
- Serial GC 증거: `results/0707/prometheus/gc_pause_max.json` 등 (gc="Copy"/"MarkSweepCompact"만, "G1" 0건)
