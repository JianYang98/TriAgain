# 0706 로그 상태 분석 — 원자적 UPDATE 2차(A2) · full nstat · ss · server-A2.log

> 출처 라벨: **〔측정·nstat〕** `nstat-ts-012017.log` · **〔측정·ss〕** `ss-state-012032.log` · **〔측정·앱로그〕** `server-A2.log` · **〔측정·k6〕** raw/summary 교차 인용 · **〔파생〕** 계산 · **〔분석〕** §8에 격리.
> 대응 k6 결과 정본: `0706_원자적update2-결과.md`. 포맷은 `0705_로그상태분석_nstat-서버로그-C10.md` 계승.
> ⭐ **이번 세션 = 시리즈 최초의 관측 풀셋**: full nstat 20종 + ss 시계열 + 앱로그 + Prometheus 4종 동시 확보 — C10 분석서 §8이 요구한 수집 개선이 전부 이행됐다.

---

## 0. 로그 인벤토리 & 무결성 〔측정〕

| 파일 | 구간 (KST) | 규모 | 비고 |
|---|---|---|---|
| `nstat-ts-012017.log` | 01:20:17 ~ 01:40:07 (1,191초) | 77,260줄 → 카운터 **20종** × 1,191초 | 초당 3~4회 중복 기록 → 각 초 마지막 값 채택(불일치 113건 전부 진행 중 캡처). 비단조 감소 0건 |
| `ss-state-012032.log` | 01:20:32 ~ 01:40:05 | 3,761샘플 | `acceptQ=X/LIMIT \| LISTEN ESTAB` 형식, 초당 2~3샘플 |
| `server-A2.log` | 01:19:40.558 ~ 01:39:24.477 | 1,720줄 (nohup) | 기동 로그 + CR002 1,694건, ERROR 0 |

- C10(카운터 3종)과 달리 **Syncookies 3종 · OutRsts · AttemptFails · EstabResets · BacklogDrop · ReqQFullDoCookies/Drop 전부 포함** — 드롭 등식을 닫을 수 있는 세트.

## 1. 창 시작 상태 〔측정〕

| 항목 | 값 | 해석 가능 범위 |
|---|---|---|
| 앱 기동 | 01:19:40.558, **PID 35817**, 20.9s, 프로파일 `prod`+`loadtest` | C10(PID 22281)과 다른 프로세스 = **재기동 확정**. 첫 런까지 7분 24초 |
| 커널 카운터 시작값 | AttemptFails 1,037 · SyncookiesSent 1,825 · OutRsts 1,751 등 | 부팅 누적 연속 — **OS 재부팅 없음** (전일 C8~C10 세션분 포함 누적) |
| 리스너 backlog | **`acceptQ=0/256` 전 구간 고정** 〔측정·ss〕 | **A2 실효 backlog = 256 실측 확정** — 6차·07-04와 같은 값. C10의 실효값은 여전히 미상(👤) |

## 2. 움직인 카운터 (Δ ≠ 0) — 창 전체 〔측정·nstat〕

| 카운터 | 시작 | 종료 | 전체 Δ |
|---|---|---|---|
| TcpExtSyncookiesRecv | 3,296 | 4,869 | +1,573 |
| TcpExtSyncookiesSent | 1,825 | 2,769 | **+944** |
| TcpExtTCPReqQFullDoCookies | 1,825 | 2,769 | +944 (Sent와 매 런 동일 — 동일 이벤트 이중 계상) |
| TcpExtTCPSynRetrans | 3,726 | 4,575 | +849 |
| TcpOutRsts | 1,751 | 2,312 | +561 |
| TcpExtSyncookiesFailed | 1,394 | 1,903 | **+509** |
| TcpAttemptFails | 1,037 | 1,535 | **+498** |
| TcpExtTCPAbortOnData | 103 | 144 | +41 (배경 잡음 위주) |

**Δ=0 (12종)**: **ListenOverflows · ListenDrops · TCPBacklogDrop · TCPReqQFullDrop**(누락층 4종 전부 침묵) + EstabResets · AbortOnClose/Memory/Timeout/Linger · MemoryPressures(2종) · FastOpenListenOverflow.

## 3. 런별 창 집계 & 드롭 등식 〔측정·nstat / 파생〕

런 창 = 완료 − 런길이 − 3s ~ 완료 + 3s. (움직인 카운터만 표기)

| VU | k6 드롭 | **AttemptFails** | SyncookiesFailed | OutRsts | SyncookiesSent | SyncookiesRecv | SynRetrans |
|---|---|---|---|---|---|---|---|
| 20  | 0 | 0 | 0 | 0 | 0 | 0 | 1 |
| 50  | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| 100 | 0 | 0 | 0 | 0 | 0 | 0 | 1 |
| 200 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| 300 | **34**  | **34**  | 34  | 34  | 35  | 68  | 109 |
| 400 | **82**  | **82**  | 82  | 82  | 149 | 190 | 252 |
| 500 | **147** | **147** | 147 | 147 | 226 | 503 | 162 |
| 700 | **233** | **233** | 246 (+13) | 247 (+14) | 534 | 812 | 292 |

- **드롭 등식: `TcpAttemptFails Δ = k6 join_dropped` — 4/4런 정확 일치.** SyncookiesFailed·OutRsts는 3/4런 동행, vu700만 +13/+14 잉여 (07-04 A1 vu700의 Failed +9 잉여와 동일 패턴 — 드롭으로 계상되지 않은 검증 실패 소수 존재).
- **쿠키 첫 발동 = vu300 창** (전 구간 통틀어 첫 SyncookiesSent 움직임이 01:32:33). vu20~200 창은 쿠키 계열 완전 0. 발급량 산수: 35(vu300)·149(vu400)·226(vu500)·534(vu700) — "SYN 큐(≈backlog 256) 초과분부터 쿠키 폴백"과 정합 〔파생〕.
- 쿠키 계열 4종은 **창 밖 노이즈 0** (전량 런 창 안). OutRsts/AbortOnData/SynRetrans만 런 무관 배경 잡음(초당 1~3, 20분 산발).
- 미시 구조: 드롭 런마다 SyncookiesFailed가 **첫 1초에 전량 집중**(34/81/147/244), AttemptFails는 다음 초에 계상 — RST 즉사 볼리의 초 단위 지문.

## 4. ss 소켓 상태 〔측정·ss〕

| VU | acceptQ 피크 | ESTAB 피크 | VU−드롭+1 | 오차 |
|---|---|---|---|---|
| 20  | 0 | 21  | 21  | 0 |
| 50  | 0 | 51  | 51  | 0 |
| 100 | 0 | 101 | 101 | 0 |
| 200 | 0 | 201 | 201 | 0 |
| 300 | 0 | 267 | 267 | 0 |
| 400 | 0 | 319 | 319 | 0 |
| 500 | 0 | 354 | 354 | 0 |
| 700 | 0 | 468 | 468 | 0 |

- **acceptQ > 0인 샘플이 3,761개 중 0건** — accept 큐는 vu700까지 한 순간도 쌓이지 않았다 (nstat ListenOverflows 0과 이중 확인).
- **ESTAB 피크 = VU − 드롭 + 1: 8/8런 오차 0** — 07-04 A1의 10런에 이어 누적 18런 무결. 드롭된 커넥션은 ESTAB에 도달한 적이 없다 = 수립층 소멸의 소켓 상태 증명.
- 유휴 기저 ESTAB = 1 (모니터링 keepalive).

## 5. 서버 앱로그 — CR002 교차 〔측정·앱로그〕

| VU | 서버 CR002 | k6 409 | 일치 | 서버 처리 구간 |
|---|---|---|---|---|
| 20  | 10  | 10  | ✓ | 31 ms |
| 50  | 40  | 40  | ✓ | 282 ms |
| 100 | 90  | 90  | ✓ | 781 ms |
| 200 | 190 | 190 | ✓ | 1,438 ms |
| 300 | 256 | 256 | ✓ | 1,811 ms |
| 400 | 308 | 308 | ✓ | 1,611 ms |
| 500 | 343 | 343 | ✓ | 1,556 ms |
| 700 | 457 | 457 | ✓ | 1,969 ms |

- **8/8런 정확 일치 (총 1,694), 창 밖 0건 — C10 vu700의 "유령 409"(+4)는 이번엔 재현되지 않았다.** 드롭 496건 전원 앱 미도달.
- 부하 구간 CR002 외 이벤트 0건: ERROR 0 · 스택트레이스 0 · Hikari/Tomcat/GC 경고 0.
- 락 전략 직접 증빙 없음 (전 세션과 동일한 로거 구성 — 간접 증빙은 Prometheus acquire 지문 몫, 결과서 §7).

## 6. 교차검증 — 드롭의 다중 관측 일치 〔파생〕

vu300 기준 (다른 런 동형): k6 드롭 34 = AttemptFails 34 = SyncookiesFailed 34 = OutRsts 34 = ESTAB 결손 34(267=300−34+1) = 앱 미도달 34(CR002 256=300−34−10) — **5중 독립 관측 일치.** 07-04 A1의 6중 일치 재현이다.

## 7. 터미널 로그 자체 판정 〔측정·k6〕

- PASS 4런 (vu20~200) · FAIL 4런 (vu300~700, join_dropped threshold crossed).
- 드롭 에러 단일종: `read: connection reset by peer` 496건 (34+82+147+233).
- 정합성: 전 런 success=10 정확 · 5xx 0 · 충돌 0.

## 8. 〔분석〕 — 층 분리 결론 (측정값 아님)

1. **A2 드롭 메커니즘 = "쿠키 검증 실패 → RST 즉사" 확정 (등식 마감).** C10에서 카운터 부재로 못 닫은 것을 이번에 닫았다 — 단, 이것은 **A2(backlog 256) 체제의 등식**이다. C10의 "지연 RST"(~0.5s 후 RST, 쿠키 카운터 미수집)가 같은 메커니즘이라는 보장은 없다 — C10 재현 측정 시 full nstat이면 판별된다.
2. **드롭 개시 vu300 복귀는 backlog 귀속이 유력.** A2 실효 backlog 256 실측 — C7·C9(둘 다 256, 개시 vu300)와 정확히 같은 조건·같은 개시점. C10(개시 vu500)만 다른데 C10의 backlog는 미상. `acceptQ=0/4096` 관측은 **운영 박스(도커 프록시 리스너 = 호스트 somaxconn 기본값)로 판명(👤)** — C10 단서 아님. C10 실효값은 조건 재현 스윕(기동 명령 기록 + ss)으로만 확정 가능.
3. **accept 큐 층은 4중 침묵**: ListenOverflows 0 + ListenDrops 0 + BacklogDrop 0 + acceptQ 샘플 전량 0. ReqQFullDrop 0 = SYN 큐 초과분도 드롭이 아니라 전량 쿠키 폴백(DoCookies +944)으로 처리 — 죽는 건 그중 검증 실패분뿐.
4. **드롭 ≠ 락 전략 결론 유지**: 드롭 순간 앱은 CR002 외 무결, ESTAB 공식 성립 — 앱/락이 개입할 통로 없음. 6차→A1→C7→C9→C10→A2 6세션 연속 일관.
5. vu700 SyncookiesFailed 잉여(+13)는 A1 vu700(+9)과 동일한 미확정 항목 — 검증 실패했으나 k6 드롭으로 계상되지 않은 소수. 재현 2회째이므로 "vu700급에서만 나타나는 잔차"로 기록.

## 9. 다음 액션 〔분석〕

1. ~~`acceptQ=0/4096` 출처 박스 확인~~ → **운영으로 판명(👤)**, C10 단서 제외.
2. C10 조건 재현 스윕(기동 명령 터미널 기록 + `ss -lnt` + full nstat) — C10 backlog 실효값과 "지연 RST" 메커니즘 등식을 동시에 마감.
3. 이번 관측 풀셋(nstat 20종 + ss + 앱로그 + Prometheus)을 표준 수집 세트로 런북에 고정.

---

## 참조한 실제 파일

- `load-test/results/0706/nstat-ts-012017.log` · `ss-state-012032.log` · `server-A2.log` (서버측 3종 — §1~§6 정본)
- `load-test/results/0706/20260706_A원자적update2.md` (터미널 캡처 — §7)
- `load-test/results/raw/crew-rush-jian_A2_max10_vu{20,50,100,200,300,400,500,700}.json` + summary 8쌍
- `load-test/results/0704/0704_로그상태분석_nstat-ss-terminal.md` (A1 등식 선례) · `results/0705/0705_로그상태분석_nstat-서버로그-C10.md` (C10 대조)

_생성: 2026-07-06 · 정본 = 서버측 3종 로그 + k6 raw 8런 · 해석은 §8에 격리_
