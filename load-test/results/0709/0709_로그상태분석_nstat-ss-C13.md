# 0709 로그 상태 분석 — 비관락 13차(C13) · nstat · ss

> 출처 라벨: **〔측정·nstat〕** `nstat-ts-171103-C13.log` · **〔측정·ss〕** `ss-state-171112-C13.log` · **〔측정·k6〕** raw json 8종(`results/raw/crew-rush-jian_C13_max10_vu*.json`) 교차 인용 · **〔측정·서버로그〕** `server-C13.log` · **〔파생〕** 계산. 해석·추정은 §7에 격리.
> 범위: nstat·ss 2종이 대상. 패킷 레벨은 `0709_tcpdump분석-C13.md`, k6 클라 측은 `0709_비관락13차-결과.md`가 각각 정본.
> 전략: **PESSIMISTIC(비관락 13차) — 👤 운영자 직접 확인(2026-07-09)**. 라벨 정본 = `0709_C13_라벨링-스냅샷-기록.md`, 서버측 acquire 지문(`prometheus-c13/`)이 비관 정합 보강. accept-count **256** — 👤 확인(2026-07-09), 아래 ss backlog 분모 `/256`과 정합.

---

## 0. 로그 인벤토리 & 무결성 〔측정〕

| 파일 | 구간 (KST) | 규모 | 비고 |
|---|---|---|---|
| `nstat-ts-171103-C13.log` | 17:11:03 ~ 17:35:54 (1,481초 창) | 29,620줄 = 카운터 **20종** × 1,481초, 파싱 실패 0 | 불완전 블록 0. **결측 11초**(각 1초씩) — 전부 버스트 창 밖(§6) |
| `ss-state-171112-C13.log` | 17:11:12 ~ 17:35:53 | 4,743줄, 파싱 실패 0 | `acceptQ=X/256 \| LISTEN=1 ESTAB=N ...`(상태별 토큰) 형식. **결측 0초** |

- 리스너 backlog 분모: **전 구간 `/256` 고정** — C11·C12·A2~A4·C9와 동일. (관측치 — accept-count 256 👤 확인과 정합.)
- 관측된 소켓 상태: LISTEN · ESTAB · SYN-RECV · LAST-ACK · TIME-WAIT · **CLOSE-WAIT** 6종. **CLOSE-WAIT은 C12에 없던 상태**(§5에서 상세).
- nstat 시작점(17:11:03) 누적값: AttemptFails 1,001 · EstabResets 39 · OutRsts 1,401 · SyncookiesSent(=ReqQFullDoCookies) 1,121 · SyncookiesRecv 2,218 · SyncookiesFailed 949 · SynRetrans 11,699 · ListenOverflows/ListenDrops/BacklogDrop/ReqQFullDrop 0 — 부팅 이후 누적치로, 세션 분석은 전부 델타 기준.

## 1. 세션 전체 Δ (17:11:03 → 17:35:54) 〔측정·nstat〕

| 카운터 | 전체 Δ |
|---|---|
| TcpAttemptFails | **+599** |
| TcpEstabResets | **+1** |
| TcpOutRsts | +1,248 |
| TcpExtSyncookiesSent(=ReqQFullDoCookies) | +1,186 |
| TcpExtSyncookiesRecv | +1,895 |
| TcpExtSyncookiesFailed | **+1,046** |
| TcpExtTCPSynRetrans | +1,592 |
| TcpExtTCPAbortOnData | +33 |
| ListenOverflows / ListenDrops / TCPBacklogDrop / TCPReqQFullDrop | **전부 0** |

(시작 누적치는 §0 참조)

**Δ=0 (전세션 침묵)**: ListenOverflows · ListenDrops · TCPBacklogDrop · TCPReqQFullDrop — **accept 큐 관련 4종이 전부 0**. EstabResets는 C12(Δ=0)와 달리 이번 세션은 **+1**(런 버스트 밖 배경 이벤트, §2 참조 — 버스트 내 8/8 전부 0).

## 2. 런별 버스트 Δ & 드롭 등식 검증 〔측정·nstat / 파생〕

| VU(실행순) | k6드롭 | AttemptFails | EstabResets | AF+ER | OutRsts | SynFailed | DoCookies(=SynSent) | CookiesRecv | SynRetrans | ListenOvf |
|---|---|---|---|---|---|---|---|---|---|---|
| 20(1)  | 0   | 0   | 0 | 0   | 0   | 0   | 0   | 0   | 1   | 0 |
| 400(2) | 141 | **141** | 0 | 141 | 273 | **273** | 161 | 404 | 397 | 0 |
| 700(3) | 257 | **257** | 0 | 257 | 480 | **480** | 734 | 893 | 546 | 0 |
| 50(4)  | 0   | 0   | 0 | 0   | 0   | 0   | 0   | 0   | 6   | 0 |
| 300(5) | 0   | **0**   | 0 | 0   | 0   | **0**   | **41**  | **41**  | 0   | 0 |
| 100(6) | 0   | 0   | 0 | 0   | 0   | 0   | 0   | 0   | 1   | 0 |
| 500(7) | 196 | **196** | 0 | 196 | 293 | **293** | 250 | 557 | 476 | 0 |
| 200(8) | 0   | 0   | 0 | 0   | 0   | 0   | 0   | 0   | 60  | 0 |

**드롭 등식 검증: `AttemptFails + EstabResets = k6 dropped`**

| VU | k6 dropped | AttemptFails+EstabResets | 오차 |
|---|---|---|---|
| 20  | 0   | 0+0     | 0 |
| 400 | 141 | 141+0   | 0 |
| 700 | 257 | 257+0   | 0 |
| 50  | 0   | 0+0     | 0 |
| 300 | 0   | 0+0     | 0 |
| 100 | 0   | 0+0     | 0 |
| 500 | 196 | 196+0   | 0 |
| 200 | 0   | 0+0     | 0 |

**8/8 런 오차 0.**

- **EstabResets 런 버스트 내 8/8 전부 0**(세션 전체 +1은 버스트 밖 배경 이벤트) — 드롭이 전부 AttemptFails 단독으로 설명됨. **유령409 0**(k6 full 합 = 서버로그 CR002 = Micrometer(`prometheus-c13`) 셋 다 1,596, 3채널 정합)과 정합.
- **드롭 개시 VU = vu400으로 후퇴**(C11·C12는 vu300) — vu300은 실행순 5번째로 vu400(2번째)보다 나중에 실행됐음에도 드롭 0. 실행 순서 효과가 아님(§7).
- **vu300 = 경계**: DoCookies 41 · CookiesRecv 41 발동 — SYN 큐가 가득 차 쿠키 경로까지 진입했으나 SynFailed 0 · OutRsts 0 · 드롭 0(전량 검증 성공). 드롭 개시가 vu300이 아니라 vu400인 이유가 여기서 드러남.
- **SyncookiesFailed(=OutRsts) vs 드롭**: vu400 273>141(잉여132) · vu700 480>257(잉여223) · vu500 293>196(잉여97) — **드롭 3런 전부 잉여 존재**(C12는 vu500만 잉여35였던 것과 대조). 이중/삼중 RST 해석은 `0709_tcpdump분석-C13.md` 참조.
- SynRetrans는 노이즈(vu200 창 +60인데 드롭 0 — 배경 재전송, §4). 드롭 등식 계산엔 버스트 AttemptFails만 사용.

## 3. Syncookies 경로 〔측정·nstat〕

- **DoCookies(=SynSent) 증분은 드롭 3런 + vu300에만 존재**(161/734/41/250), 그 외 무드롭 런(20/50/100/200)은 0. CookiesRecv도 동일 패턴(404/893/41/557).
- **vu300 = 경계 사례**: DoCookies 41 · CookiesRecv 41 발동됐으나 SynFailed 0 · OutRsts 0 · 드롭 0 — 쿠키 경로가 작동해도 검증이 전량 성공하면 드롭으로 이어지지 않음을 보여주는 세션 내 유일 사례.
- **OutRsts = SyncookiesFailed 런별 정확 일치**(273/480/293). 드롭(AttemptFails)보다 큰 잉여(132/223/97)는 이중/삼중 RST — tcpdump 문서와 교차.

## 4. ⚠️ 특이 관측 〔측정〕

- **SynRetrans 배경 비중이 크지 않음**: 세션 전체 Δ +1,592, 8개 런 버스트 합 1,487(93.4%) — 버스트 밖(배경) 잔여 약 105(6.6%)뿐. **C12의 런 밖 램프(+1,950, 세션 전체의 71%)와 달리 이번 세션엔 이상 램프가 관측되지 않음.**
- 버스트 내 **vu200 창 +60**은 드롭 0인 런에서 재전송만 튀는 배경 노이즈로 남음(드롭 등식과 무관, §2).

## 5. ss 상태 상세 〔측정·ss〕

**acceptQ: 전 구간 실사용 거의 없음** — 4,743개 표본 중 `acceptQ>0` **단 2건**: 22/256 @17:23:22(vu400 창) · 21/256 @17:35:33(vu200 창). 그 외 전부 0/256. §1의 Listen계 카운터 4종 침묵과 함께 accept 큐 층은 사실상 미사용.

**런별 상태 피크**

| VU | ESTAB | SYN-RECV | LAST-ACK | TIME-WAIT | CLOSE-WAIT |
|---|---|---|---|---|---|
| 20  | 21  | 0   | 0   | 0 | 0 |
| 400 | 260 | **145** | 0   | 0 | 0 |
| 700 | **444**(세션최대) | **257** | 231 | 0 | **21** |
| 50  | 51  | 0   | 2   | 0 | 0 |
| 300 | 301 | **0**   | 0   | 1 | 0 |
| 100 | 101 | 0   | 0   | 0 | 0 |
| 500 | 305 | **196** | 172 | 0 | **11** |
| 200 | 201 | 0   | 12  | 1(주1) | 0 |

- **SYN-RECV 피크 = 드롭 예측 채널**: vu700 257=드롭257(정확) · vu500 196=드롭196(정확) · vu400 145≈드롭141(ss 1초 샘플 오차). **무드롭 런(20/50/100/200/300)은 SYN-RECV 피크 전부 0** — 드롭↔SYN-RECV 경계가 **C12보다 선명**(C12는 무드롭 런도 38~95였음).
- **CLOSE-WAIT 유의 관측**: vu700 21 · vu500 11 — 드롭 런에만 등장(무드롭 런 0). **C12엔 없던 상태**(§0).
- **SYN-RECV census**(0이 아닌 값 전부): vu400 145→141→141→121(17:23:22~23) · vu700 251→255→257→257→40→9(17:26:04~07) · vu500 11→196→196→196→101(17:33:47~49).
- **LAST-ACK**: vu700 231 · vu500 172 · vu200 12 · vu50 2. **TIME-WAIT**: 세션 최대 1(vu300 1건, 주1: vu200도 표본상 최대 1로 미미). **LISTEN**: 항상 1.

## 6. 캡처 공백 〔측정〕

nstat 결측 11건(각 1초): 17:13:17 · 17:15:34 · 17:17:44 · 17:19:54 · 17:22:01 · 17:24:11 · 17:26:19 · 17:28:32 · 17:30:40 · 17:32:57 · 17:34:59. **k6 요청 버스트 창과 겹침 0건**(전부 버스트 창 밖). ss 결측 0건.

## 7. 이 문서가 확정하지 않는 것 〔분석 경계〕

- **드롭 개시 VU가 vu400으로 후퇴한 원인**(C11·C12는 vu300): 실효 SYN큐 한도 / 클라 볼리 / 서버 상태 중 무엇인지 이 세션 채널로 특정 불가. vu300이 쿠키 발동(41)했으나 무실패라는 사실까지만 확정 — 경계가 vu300~vu400 사이에 있다는 것.
- 클라 SYN 재전송 <150ms 원인(OS/k6 설정)은 이 캡처로 특정 불가.
- ACK 패킷이 캡처 필터에서 빠져(§0, tcpdump 문서 대상) 핸드셰이크 완성 방향은 패킷으로 직접 확인 불가 — ISN₂ 쿠키검증실패 해석은 "모순 관측 없음"까지.
- SynRetrans 배경 잔여(버스트 밖 약 105)의 발생원은 이 세션 채널로 특정 불가(C12 램프만큼 크지 않아 별도 조사 대상은 아님).
- 전략 라벨 PESSIMISTIC · accept-count **256** 모두 👤 확인(2026-07-09, 라벨 정본 = `0709_C13_라벨링-스냅샷-기록.md`, acquire 지문 보강). ss backlog 분모 `/256`은 관측치로 설정값과 정합.

## 참조한 실제 파일

- `load-test/results/0709/nstat-ts-171103-C13.log` (정본)
- `load-test/results/0709/ss-state-171112-C13.log` (정본)
- `load-test/results/raw/crew-rush-jian_C13_max10_vu{N}.json` (런 창·드롭 대조)
- `load-test/results/0709/server-C13.log` (CR002 1,596 대조)
- `load-test/results/0709/0709_C13_라벨링-스냅샷-기록.md` (전략 라벨 프로버넌스) · `prometheus-c13/README.md` (409 Micrometer 채널·기동 시각)
- 참조 포맷: `load-test/results/0708/0708_로그상태분석_nstat-ss-C12.md` (섹션 구조·표 형식 미러링)

_생성: 2026-07-09 · 수치 출처: 오케스트레이터 사전 파싱 FACTS SHEET(`scratchpad/C13-FACTS.md`)_
