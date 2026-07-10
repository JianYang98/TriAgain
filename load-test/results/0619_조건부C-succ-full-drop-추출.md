# 06-19 조건부(C = CONDITIONAL / 원자적 UPDATE) — succ / full / dropped 추출

> **목적**: `results/11_conditional-update-comparison.md`(06-19 A/B/C 스윕)에서 **C 전략만** 정합성 카운터(succ·full·dropped)를 추출한다. 07-01 CCC(오라벨=실제 비관락)와 달리 **이 세션의 C는 진짜 CONDITIONAL(원자적)** 이므로, 0707 before-after 문서가 못 채운 "원자적 前(backlog 100)" 슬롯의 후보 레퍼런스다.
> **출처 라벨**: 〔측정〕 k6 카운터 직접값(summary.json) · 〔파생〕 status 0(drop) 합산 · 〔추정〕 backlog 값 귀속.

## 전략 라벨 근거 (C = 조건부 확정)

- 원본 §4 특성표: **A=PESSIMISTIC · B=OPTIMISTIC · C=CONDITIONAL**, `--triagain.crew.lock-strategy` 플래그로 직접 전환(원본 §0).
- 거동 검증: B는 CR023 재시도소진 + 정원 미달(64/100)=낙관 CAS 지문, C는 conflict 0·단일 UPDATE·p95 최저 → **A/B/C가 거동으로 구별됨**(07-01 CCC는 "conflict=0이 비관·조건부 공통이라 판별 불가"였던 것과 대비).
- ⚠️ raw 접두어 C↔전략은 **세션마다 다름**: 06-19 C=조건부(✅ 이 문서) vs 07-01 CCC=비관락(오라벨). 직역 금지.

## 1. 정원 10 (MAX_MEMBERS=10) — C 전략 〔측정〕

| VU | succ | full(409·CR002) | dropped(status 0) | 합계=VU |
|---:|---:|---:|---:|:---:|
| 50 | 10 | 40 | 0 | ✓ |
| 100 | 10 | 90 | 0 | ✓ |
| 200 | 10 | 190 | 0 | ✓ |
| 300 | 10 | 211 | 79 | ✓ |

## 2. 정원 100 (MAX_MEMBERS=100) — C 전략 〔측정〕

| VU | succ | full(409·CR002) | dropped(status 0) | 합계=VU |
|---:|---:|---:|---:|:---:|
| 200 | 100 | 95 | 5 | ✓ |
| 400 | 100 | 202 | 98 | ✓ |
| 800 | 100 | 601 | 99 | ✓ |

- conflict(CR023)·5xx = **전 런 0** (C는 재시도·서버에러 없음).
- 정합성 무결: 전 런 `succ==정원` 정확, `succ+full+drop==VU` 검산 일치.
- 드롭은 정원초과 거부분이 409 대신 연결 리셋으로 샌 회계 — 정원(succ)은 안 깨짐(원본 §4.2 논리와 동일).

## 원자적 前(100) 후보로 쓸 때 캐비앗 (drop-in 아님)

1. **backlog 값 미기록** — 06-19는 07-03 accept-count 100→256 변경 **前**이라 backlog=100으로 추정되나, 이 원본 파일엔 accept-count 기록이 없다 〔추정, 타임라인 근거〕.
2. **k6 스크립트 다름** — 06-19 = `crew-rush.js`(raw `k6-report_C_*`), 0707 세션(C11·A4) = `crew-rush-jian.js`. p95 등 latency 지표는 **정의 대조 후에만** 비교 가능(succ/full/drop 카운트는 지표 무관·안전).
3. **cross-session** — 06-19 vs A4(07-06) 다른 날·warm 상태. 절대 latency 직비교 제한.
4. **drop 노이즈** — 정원10 vu300 drop 79, 정원100 vu400/800 drop 98/99. 재실행 편차 큰 연결계층 지표라(원본 §측정신뢰성) 드롭 절대값 과해석 금지. 원본 결론: 드롭 천장 = HikariCP 풀(max 10) 포화, 전략 무관.

## 참조한 실제 파일

- `results/11_conditional-update-comparison.md` (06-19 A/B/C 비교 정본 — §1 정원10·§2 정원100 C행)
- `results/raw/k6-report_C_max10_vu{50,100,200,300}_*.summary.json` (정원10 채택 런: `02-56-37`·`02-59-44`·`03-01-40`·`03-06-20`)
- `results/raw/k6-report_C_max100_vu{200,400,800}_*.summary.json` (정원100 채택 런: `03-18-43`·`03-21-59`·`03-23-18`)
- `k6/crew-rush.js`, `k6/lib/{scenarios,metrics}.js` (측정 스크립트 — feat/load-test 8edef88)

_생성: 2026-07-07 · 정본 = 11_conditional-update-comparison.md의 C행 발췌 · C=조건부(원자적) 확정 · backlog 100은 타임라인 추정_
