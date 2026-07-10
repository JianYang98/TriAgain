# 서버측 실측 요약 — 비관락(3차, RUN_TAG `CCC`) 크루러시 · 2026-07-01 (KST 20:15~20:32)

출처: Prometheus(localhost:9090) job=`triagain` → EC2 t3.micro `15.164.69.243:8080/actuator/prometheus` · 스크랩 **5s** · 전 구간 `up==1`(연속 가동 확인).
전략: **PESSIMISTIC**(비관락). k6 클라이언트측 원값: `results/raw/crew-rush-jian_CCC_max10_vu{50,100,200,300}_*.summary.json`.
테스트 4런(완료시각 KST): vu50 20:23:04 · vu100 20:24:25 · vu200 20:25:22 · vu300 20:27:38.

## 신뢰 등급 (정직성 주의)
- **워터마크(`*_max`)는 신뢰** — 스크랩 사이 최댓값을 롤링 윈도우로 누적하므로 1초 미만 버스트도 포착·유지.
- **게이지(active/pending/cpu/live)는 과소표집** — 버스트<1s인데 스크랩 5s라 대부분 idle을 찍음. 잡힌 값은 **하한**.
  - ⚠️ 실제로 active/pending은 **20:23:05(vu50 버스트) 단 1표본만** 포착됨. vu100/200/300 버스트는 게이지에 흔적 없음 → 이 게이지 피크는 사실상 vu50값이고 상위 VU는 미측정.

## 핵심 사실 (peak over 전 구간)
- HikariCP acquire 대기 max 〔워터마크·신뢰〕: **0.758s** @20:25:25 KST — 풀(max10) 커넥션 획득 최장 대기. **vu200 직후 피크**(vu300은 오히려 낮음 → 초과분이 커널 **커넥션 수립 단계에서 탈락**해 풀에 덜 도달, 경합 감소 — nstat 실측 아래 §). = SELECT FOR UPDATE 직렬화 큐의 서버측 지문
- tomcat/http request max 〔워터마크·신뢰〕: **0.934s** @20:25:25 KST — 서버가 처리한 단일 요청 최장시간(네트워크 제외). 그중 최대 0.758s가 커넥션 대기
- GC pause max 〔워터마크·신뢰〕: **0.016s (16ms)** @20:20:40 KST — minor GC(Copy/Allocation Failure)만, major/full 없음
- process_cpu_usage peak 〔게이지·하한〕: **21.7%** @20:27:40 KST — CPU 포화 아님
- system_cpu_usage peak 〔게이지·하한〕: **29.5%** @20:27:40 KST
- system_load_average_1m peak: **0.62** @20:24:30 KST
- HikariCP active 〔게이지·하한〕: **10/10** @20:23:05 KST — 풀 포화(=10) 포착(vu50 버스트)
- HikariCP pending 대기 〔게이지·하한〕: **37** @20:23:05 KST — 최소 이만큼 큐잉(실 피크는 더 높음, 상위 VU 미표집)
- HikariCP acquire timeout 누적: **0건** — 0 = 풀 압박 있었으나 획득 실패/타임아웃 없음
- jvm live threads peak: **113** @20:25:25 KST
- jvm BLOCKED threads peak: **0** — 0 = 락 대기는 JDBC 소켓(DB) 레벨, Java monitor BLOCK 아님

## 해석
1. **CPU·GC·스레드 병목 아님** — proc/sys CPU 피크 22~29%(게이지·하한), GC pause 16ms, BLOCKED 0, live 113.
2. **병목은 HikariCP 10-커넥션 풀** — active=10 포화 + pending 큐(≥37) + acquire 대기 max 0.758s. 비관락 SELECT FOR UPDATE 직렬화가 서버측 커넥션 큐로 드러남.
3. **timeout 0 · 5xx 0** — 풀 압박은 있었으나 획득 실패·앱 에러 0. 받은 요청은 전부 정상 처리.
4. **드롭(status 0)은 액추에이터에 안 보임** — 커널 레벨 사건이라 액추에이터('받아들인 요청'만 봄)엔 안 잡힌다. **당일 nstat 시계열 실측(아래 §)으로 정체 확정: `TCPSynRetrans` 스파이크(핸드셰이크 수립 단계 탈락)이며 `ListenOverflows=0` → accept 큐 오버플로가 아님.** (⚠️ 06-23 배치는 accept 큐 오버플로였음 → 같은 t3.micro라도 런마다 터지는 커널 층이 다름.) 서버측 지표는 **지연 원인(풀 큐)**을 설명하고 **CPU/GC/앱에러를 범인에서 배제**.

## nstat 커널 카운터 실측 — 드롭의 정체 확정 (2026-07-01 당일)
캡처: EC2에서 `nstat -asz` 1초 시계열, **20:13:01~21:05:35 KST**(이 3차 런 4개 전 구간 포함). 로그: `results/0701/nstat-ts-201300.log`.

| 커널 카운터 | 창 전체 delta | 스파이크 시각(KST) | 비고 |
|---|:---:|---|---|
| `TcpExtListenOverflows` | **0** (2379→2379) | — | accept 큐(backlog=100) 오버플로 **0건** |
| `TcpExtListenDrops` | **0** (2383→2383) | — | accept 큐 드롭 **0건** |
| `TcpExtTCPSynRetrans` | **+264** | 20:25:22 **+83** · 20:27:38 **+100** | SYN-ACK 재전송 = 핸드셰이크 수립 탈락 |

**k6 `join_dropped`와 초·건수 1:1 일치(스모킹건)**:
- vu200(완료 20:25:22): `join_dropped`=**83** = SynRetrans **+83**
- vu300(완료 20:27:38): `join_dropped`=**98** ≈ SynRetrans **+100**

**결론**: 이 3차(CCC) 런의 드롭 = **TCP 핸드셰이크 수립 실패**(서버 SYN-ACK 재전송). accept 큐 오버플로 **아님**(`ListenOverflows=0`). `join_5xx=0`·`join_success=10` 전 VU 정확 → **앱·락 정합성 완벽**, 드롭은 순수 t3.micro 커넥션 수립 천장. ⚠️ 06-23 배치는 accept 큐 오버플로(ListenOverflows·Send-Q=100)로 확정됐던 것과 **커널 층이 다름** — 왜 갈리는지(accept-count·램프 모양·도착 타이밍)는 미확정. 어느 층이든 락 전략과 무관.

## 저장 파일 (results/0701/server-metrics-3차비관락/)
- `prometheus_triagain_full_3차비관락_20260701.json` — 전 구간 **155지표 원본 시계열**(5s, 587시리즈, 96,193표본)
- `prometheus_triagain_key_3차비관락_20260701.csv` — 핵심 15지표 와이드 CSV(5s, 205행)
- `prom-raw-3차비관락-20260701.json` / `prom-key-3차비관락-20260701.csv` — 앞서 뽑은 17지표 부분셋(중복 보존)
- `server-metrics-facts-3차비관락_20260701.md` — 이 요약
- 스크린샷: `../server-grafana-3차비관락/`
- nstat 커널 시계열: `../nstat-ts-201300.log`(1초 간격, ListenOverflows·ListenDrops·TCPSynRetrans, 20:13~21:05 KST) — 위 §nstat 실측 원본
