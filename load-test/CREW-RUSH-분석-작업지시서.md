# 크루 가입 러시 부하테스트 — 세션 분석 작업지시서 (재사용)

> 목적: 한 세션(예: C13 비관락 13차, A6 조건부 6차)을 측정한 뒤, **결과서·로그상태 분석·tcpdump 분석 3종**을 사실적시로 만들 때 쓰는 지시서.
> 이 문서 하나면 이 대화 맥락이 없는 새 세션에서도 동일 품질로 재현된다. 런 실행 자체는 `CREW-RUSH-ABC-RUNBOOK.md` 참조.
> 실증 사례(포맷·수준 정본): C13 3종 = `results/0709/0709_비관락13차-결과.md`·`0709_로그상태분석_nstat-ss-C13.md`·`0709_tcpdump분석-C13.md` / A6 3종(nstat 부재 케이스) = `results/0709/0709_원자적update6-결과.md`·`0709_로그상태분석_ss-A6.md`·`0709_tcpdump분석-A6.md`.

---

## 0. 사용자가 새 세션에서 붙여넣을 메시지 (템플릿)

아래를 채워서 그대로 붙여넣으면 된다:

```
{태그}(예: C14) 부하테스트 분석해줘. results/{날짜}/ 폴더에 있어.
- {태그}.md = k6 클라 로그
- server-{태그}.log · ss-state-*-{태그}.log · nstat-*-{태그}.log · tcpdump-*-{태그}.pcap
- raw json = results/raw/crew-rush-jian_{태그}_max10_vu*.json (+ .summary.json)
- prometheus-{태그}/ 폴더 있으면 서버측 정본으로 참조
전략 = {비관락 N차 PESSIMISTIC / 조건부 원자적UPDATE N차 CONDITIONAL} · accept-count {256}

방식: 네가 직접 분석 → 검증 facts sheet 고정 → 서브에이전트 3개 병렬 작성 → 네가 대조 검토.
「분석은 너가, 작성은 서브에이전트, 검토는 너가」 — C13/A6 했던 그 방식.
⛔ 다른 세션/차수 수치·비교 절대 넣지 마(이 문서는 {태그} 단독).
애매하면 짐작하지 말고 물어봐.
```

- 전략 라벨을 모르면 비워두고, 에이전트가 물어보게 둔다(§3 참조).
- "다른 세션 섞지 마"가 중요하면 반드시 명시(같은 밤 통제쌍 측정 시 폴더에 두 세션 파일이 섞여 있음).

---

## 1. 산출물 3종 & 네이밍

| 문서 | 파일명 | 내용 |
|---|---|---|
| 결과서 | `{날짜}_{전략}{N}차-결과.md`<br>(비관락13차 / 원자적update6) | k6 클라 결과 + 드롭 등식 퀵체크 + 유령409 |
| 로그상태 | `{날짜}_로그상태분석_nstat-ss-{태그}.md`<br>(nstat 없으면 `_ss-{태그}.md`) | 커널 카운터(nstat) + 소켓 상태(ss) |
| tcpdump | `{날짜}_tcpdump분석-{태그}.md` | 패킷 레벨 드롭 추적·ISN₂·유령409 seq 증거 |

- **포맷 템플릿**: 직전 같은 전략 차수 문서를 미러링 —
  - 비관락(PESSIMISTIC) → C12/C13 문서
  - 조건부(CONDITIONAL) → A5/A6 문서
- 서브에이전트에겐 "구조·라벨·톤만 미러, 템플릿의 교차세션 비교 내용은 가져오지 말 것"을 명시.

---

## 2. 방법 (에이전트가 따르는 절차)

1. **오케스트레이터가 직접 분석** (서브에이전트 아님 — 수치 정확성이 핵심):
   - raw json 파싱: success/409/drop, 완료 p95/avg/p50(201+409만), connecting, 실행시각, checks
   - nstat: 런별 버스트 Δ로 드롭 등식 `AttemptFails+EstabResets=k6드롭`
   - ss: acceptQ, 런별 상태 피크(ESTAB·SYN-RECV·LAST-ACK·CLOSE-WAIT·TIME-WAIT)
   - pcap: `tcpdump -r ... -nn -tt` 덤프 → 플로우별(클라 포트) In RST·Out RST·ISN₂·reSYN·첫RST 지연
2. **검증 facts sheet 1장을 scratchpad에 고정** — 모든 수치 다채널 교차검증(§4). 여기 없는 숫자는 문서에 못 씀.
3. **서브에이전트 3개 병렬**(Sonnet) — 각자 facts sheet만 사용 + 참조 템플릿 미러. `agent()` 병렬 호출.
4. **오케스트레이터가 대조 검토** — 수치 일치, 타 세션 누출 0, stale claim 0, 상호 참조 정합. grep 스윕.

---

## 3. 반드시 확인/질문할 것 (짐작 금지 — ask-don't-guess)

- **전략 라벨**: 파일 접두어(C/A) ≠ 전략. 세션마다 뒤집힌 이력 있음. **👤 확인 필수**(서버로그에 락전략 안 찍힘). prometheus-{태그}/README에 명시돼 있으면 그걸 정본으로.
- **accept-count**: ss backlog 분모(`/256`)는 **관측치**. Tomcat 설정값 자체는 👤 확인(서버로그·기동명령에 안 남을 수 있음). C13/A6 = 256(👤 확인).
- **false start 탐지**: 서버 기동 시각 **전**에 돈 런은 폐기. 지문 = `testRunDurationMs`가 매우 짧음(~12ms)·http 메트릭 0·구 JVM에 맞아 전량 드롭. 실측 첫 런은 기동 이후.
- **raw json 덮어쓰기**: 같은 TAG 재실행 시 `crew-rush-jian_{TAG}.json`(무타임스탬프)은 **덮임**. `.summary.json`·`.html`은 타임스탬프별 보존. 재실행/오태그 있으면 provenance 확인.
- **⚠️ 캡처 커버리지 확인(필수)**: nstat/ss/pcap 각각의 **실제 시각 범위**가 런 구간(k6 raw 첫/끝 시각)을 담는지 확인. **안 담으면 질문**(A6 사례: nstat가 런보다 9분 일찍 끝남 → nstat 제외, pcap In RST로 드롭 검증 대체, 로그상태 문서를 ss 전용으로).
- **prometheus-{태그}/ 폴더**: 있으면 서버측 정본. acquire 지문(비관=상승 사다리 / 조건부=무사다리 p50<1.5ms)이 전략 보강, GC 겹침·Micrometer 409·유령409(http_join_ioerror) 확인. 폴더 없으면 서버측 없음으로 명기.
- **pre-GC 게이트 세션**(20260710+): 런 창 초입 major GC +1은 기본값(pre-GC 귀속, k6 stdout `[pre-GC]` 줄과 대조) — **런겹침 판정은 초입 이후 증가분만**. 게이트 적용 여부는 👤 확인. **무게이트 arm**(`PRE_GC=off`)은 stdout `[pre-GC] SKIPPED`로 식별 — 이 arm은 런초입 GC +1 기대 없음(과거 무게이트 세션과 동일 취급). 게이트 온/오프 세션은 힙 초기조건이 달라 락 전략 비교 통제쌍으로 섞지 말 것.
- **콜렉터 지문**: jvm_gc_pause `gc` 태그 — Serial=`Copy`/`MarkSweepCompact`, G1=`G1 Young Generation`/`G1 Old Generation`. 세션 비교 전 콜렉터 동일성부터 확인.
- **폴더에 여러 세션 섞임**: 같은 날 통제쌍(비관↔조건부) 측정 시 한 폴더에 두 세션 파일 공존. 문서 작성 전 어느 파일이 이 태그 것인지 확정.

---

## 4. 교차검증 채널

**드롭 (락 무관 — TCP 커넥션 수립 층, t3.micro 천장):**
- k6 join_dropped = nstat AttemptFails(+EstabResets) = pcap In RST = pcap 드롭플로우 = pcap ISN₂ 플로우 = ss SYN-RECV 피크(예측 채널)
- Out RST = nstat SyncookiesFailed = pcap Out RST (드롭 + 이중/삼중 RST 잉여, 히스토그램 검산)

**409:** k6 full 합 = 서버 CR002(server log grep) = Micrometer(prometheus, status=409,error=none)

**유령409(있을 때):** k6 409 < 서버 CR002 그 **차** = Micrometer `409+IOException`(http_join_ioerror 누계) = **pcap 드롭플로우 − ISN₂ 플로우**(쿠키 없이 수립됐다 리셋) = pcap 클라 RST **seq=SYN+385**(≈384B HTTP 요청 실제 전송 증거, 쿠키-드롭은 seq=SYN+1 데이터0)

**검산:** success+full+dropped = 목표 VU (전 런). 완료 p95 ≥ 드롭포함 p95(드롭=빠른 죽음).

---

## 5. 서술 규칙

- **사실적시만**. 해석·추정은 "확정하지 않는 것/시사하는 것" §에 격리, 〔측정〕/〔파생〕/〔분석〕 라벨.
- **락 전략 비교는 완료요청(201+409) p95만** 사용(드롭 제외 재계산). 드롭·커널층은 전략 무관.
- **드롭 개시 VU는 VU 수준으로 결정**(실행순 무관) — 셔플 실행에서 낮은 VU가 뒤에 돌아도 드롭 없으면 그 VU는 개시 아래.
- vu400 등 이상치 인용 시 캐비앗(Major GC 겹침·풀 고갈 등, prometheus 근거). GC 데이터 없으면 지어내지 말 것. pre-GC 게이트 세션(20260710+)은 런초입 major GC +1이 게이트 귀속 — 겹침 캐비앗에서 제외(§3 참조).
- **⛔ 다른 세션 수치·비교 절대 금지**(요청 시). facts sheet·서브에이전트 프롬프트·최종 grep에서 3중으로 막는다.
- 각 문서 끝 `## 참조한 실제 파일` 필수. 미사용/제외 파일(예: 커버리지 밖 nstat)도 명기.

---

_생성: 2026-07-09 · 실증 = C13(비관 13차)·A6(조건부 6차) 각 3종 · 방법 = 오케스트레이터 직접분석 + facts sheet 고정 + 서브에이전트 병렬 + 대조검토_
