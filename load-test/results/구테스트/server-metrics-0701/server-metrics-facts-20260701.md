# 서버측 실측 요약 — 비관락(A) 크루러시 · 2026-07-01 (KST 00:46~01:43)

출처: Prometheus(localhost:9090) job=`triagain` → EC2 t3.micro `15.164.69.243:8080/actuator/prometheus` · 스크랩 **5s** · 전 구간 `up==1`(타깃 연속 가동).
테스트 11런: vu20 00:47:25(폐기)·01:05:58·01:08:31·01:11:54 / vu50 01:12:30 / vu100 01:13:36 / vu200 01:14:13·01:41:31 / vu300 01:15:39·01:19:29·01:39:30.

## 신뢰 등급 (정직성 주의)
- **워터마크(`*_max`)는 신뢰** — 스크랩 사이 최댓값을 누적하므로 1초 미만 버스트도 포착.
- **게이지(active/pending/cpu)는 과소표집** — 버스트<1s인데 스크랩 5s라 대부분 idle을 찍음. 잡힌 값은 **하한**(스크랩이 버스트에 우연히 겹친 순간).

## 핵심 사실 (peak over 전 구간)
- HikariCP acquire 대기 max 〔워터마크·신뢰〕: **0.742s** @01:39:35 KST — 풀(max10) 커넥션 획득 최장 대기 = SELECT FOR UPDATE 직렬화 큐의 서버측 지문
- http_server_requests max 〔워터마크·신뢰〕: **0.877s** @01:39:35 KST — 서버가 처리한 단일 요청 최장시간(네트워크 제외)
- GC pause max 〔워터마크·신뢰〕: **0.180s** @01:39:35 KST — heaviest run에서 1회
- process_cpu_usage peak 〔게이지·하한〕: **23.0%** @01:19:35 KST — CPU 포화 아님 (크레딧 만탱크와 일치)
- system_cpu_usage peak 〔게이지·하한〕: **23.8%** @01:19:35 KST
- system_load_average_1m peak: **0.48** @01:14:15 KST
- HikariCP active 〔게이지·하한〕: **10/10** @01:06:00 KST — 풀 포화(=10) 포착
- HikariCP pending 대기스레드 〔게이지·하한〕: **36** @01:14:15 KST — 최소 이만큼 큐잉(실 피크는 더 높음)
- HikariCP acquire timeout 누적: **0건** @00:46:00 KST — 0 = 풀 압박 있었으나 획득 실패/타임아웃 없음
- jvm live threads peak: **127** @01:39:35 KST
- jvm BLOCKED threads peak: **0** @00:46:00 KST — 0 = 락 대기는 JDBC 소켓(DB) 레벨, Java monitor BLOCK 아님

## 해석
1. **CPU·GC·스레드 병목 아님** — proc/sys CPU 피크 ~23~24%(게이지·하한), GC pause 0.18s 1회, BLOCKED 0. t3.micro CPU 여유 충분.
2. **병목은 HikariCP 10-커넥션 풀** — active=10 포화 + pending 큐 + acquire 대기 max 0.742s. 비관락 SELECT FOR UPDATE 직렬화가 서버측 커넥션 큐로 드러남. acquire 742ms ≈ vu300 전체 p95(~785ms) → 지연 대부분이 풀/락 대기.
3. **timeout 0 · 5xx 0** — 풀 압박은 있었으나 획득 실패·앱 에러 0. 받은 요청은 전부 정상 처리.
4. **드롭(status 0)은 액추에이터에 안 보임** — accept 큐 오버플로는 커널/Tomcat acceptor에서 요청이 앱에 닿기 전 발생. 액추에이터는 '받아들인 요청'만 본다 → 드롭은 여전히 accept-count=100 커널 스토리(06-23 ss/nstat). 서버측 지표는 **지연 원인(풀 큐)**을 설명하고 **CPU/GC/앱에러를 범인에서 배제**할 뿐.

## 저장 파일 (results/server-metrics-0701/)
- `prometheus_triagain_full_20260701.json` — 전 구간 135지표 원본 시계열(5s, 253시리즈)
- `prometheus_triagain_key_20260701.csv` — 핵심 13지표 와이드 CSV(5s)
- `server-metrics-facts-20260701.md` — 이 요약