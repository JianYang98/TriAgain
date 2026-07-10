# [수정 지시 · 통합] 부하테스트 관측 3종 — tomcat 스레드풀 + HTTP p95/p99

> 작성: 오케스트레이션 에이전트 → 대상: 백엔드 세션 (triagain-back/)
> 날짜: 2026-07-01

```
[TIER 판정] Tier: 2 | 근거: loadtest 프로파일 한정 관측 설정 2건 + Grafana 대시보드 패널 1건, prod/도메인/보안 무영향 | 판정자: orchestrator
```

---

## 배경

부하테스트 대시보드에서 두 가지가 안 보인다:

1. **tomcat HTTP 워커 스레드풀**(`tomcat_threads_*`) — `server.tomcat.mbeanregistry`가 꺼져 JMX MBean 미등록 → `/actuator/prometheus`에 아예 안 실림. (대시보드의 "Scheduler Threads"는 `executor_*` = Spring async 풀이라 tomcat과 무관)
2. **HTTP p95/p99 Latency 패널 No data** — `percentiles-histogram`이 꺼져 `http_server_requests_seconds_bucket`(le 버킷)이 안 실림. 패널 쿼리는 `…bucket… by (le)`로 이미 정상(확인 완료) → 버킷만 실리면 자동으로 채워짐.

셋 다 **loadtest 프로파일 한정**이라 prod 무영향. 하나의 재배포에 묶는다.

---

## ⚠️ 역할 분담 (엄수)

**백엔드 세션(너)이 하는 것:**
- ① application-loadtest.yml 수정
- ② triagain.json 대시보드 패널 추가
- ③ `./gradlew bootJar` 빌드 + `compileJava`/`checkstyleMain` 그린 확인
- ④ 빌드된 jar 경로를 사용자에게 보고
- ❌ **EC2 배포·재기동은 하지 않는다** (Tier 정책: 배포는 운영자 본인. 에이전트 단독 배포 금지)

**사용자(운영자)가 하는 것:**
- jar를 EC2에 업로드 + loadtest 프로파일로 재기동
- 검증 curl 실행 (아래 "검증" 2·3번)
- 로컬 grafana 리로드/재시작 + 패널 확인
- k6 짧게 주입 후 패널 선 확인

> 대시보드(triagain.json)는 **jar에 안 들어가고 로컬 grafana만** 쓰므로 EC2와 무관하다.
> jar 왕복이 필요한 건 오직 application-loadtest.yml 하나다.

---

## 수정 사항

### ① `src/main/resources/application-loadtest.yml`
파일 끝에 아래 두 블록 추가. `server:`·`management:`는 서로 다른 top-level 키라 나란히 공존한다.

```yaml
# ── 부하테스트 관측용 (prod 무영향, loadtest 프로파일 한정) ──

# tomcat 워커 스레드풀(tomcat_threads_*) 노출.
# MBeanRegistry가 켜져야 Micrometer가 JMX에서 스레드풀 지표를 읽는다.
server:
  tomcat:
    mbeanregistry:
      enabled: true

# HTTP 요청 지연 분포(히스토그램) 노출.
# 이게 켜져야 http_server_requests_seconds_bucket이 실리고
# Grafana에서 histogram_quantile(0.95, ...)로 p95/p99가 계산된다.
management:
  metrics:
    distribution:
      percentiles-histogram:
        http.server.requests: true
```

> 이 `management:` 블록은 application.yml의 `management.metrics.tags.application: triagain`·`endpoints.web.exposure`를 **덮어쓰지 않고 프로퍼티 단위로 병합**된다 → 기존 노출·라벨 유지, 버킷/스레드 지표에도 `application="triagain"` 라벨이 붙는다. prometheus.yml 수정 불필요(이미 `/actuator/prometheus` 통째 scrape).

### ② `load-test/monitoring/grafana/dashboards/triagain.json`
`panels` 배열 맨 끝에 아래 패널 객체 추가(기존 패널과 동일 datasource uid·스타일, y=32 새 행). Latency 패널은 손대지 않는다.

```json
{
  "title": "Tomcat Threads (HTTP workers)",
  "type": "timeseries",
  "gridPos": { "h": 8, "w": 12, "x": 0, "y": 32 },
  "fieldConfig": { "defaults": { "unit": "short", "min": 0 }, "overrides": [] },
  "targets": [
    { "expr": "tomcat_threads_busy_threads{application=\"triagain\"}",       "legendFormat": "Busy (처리중)",   "datasource": { "type": "prometheus", "uid": "PBFA97CFB590B2093" } },
    { "expr": "tomcat_threads_current_threads{application=\"triagain\"}",     "legendFormat": "Current (생성됨)", "datasource": { "type": "prometheus", "uid": "PBFA97CFB590B2093" } },
    { "expr": "tomcat_threads_config_max_threads{application=\"triagain\"}",  "legendFormat": "Max (한계=200)",   "datasource": { "type": "prometheus", "uid": "PBFA97CFB590B2093" } }
  ]
}
```

> `server.tomcat.threads.max` 미설정 → max는 Spring Boot 기본값 **200**으로 읽힌다.

---

## 검증 (순서대로 실행·체크)

- [ ] **0. 전제** — EC2가 **loadtest 프로파일**로 기동(`--spring.profiles.active=...,loadtest`). 아니면 아래 전부 무효. *(사용자)*
- [ ] **1. 부팅 정상** — 재배포 후 앱이 설정 파싱 에러 없이 기동(`management`/`server` 바인딩 에러 없음). *(사용자)*
- [ ] **2. tomcat 노출** — `curl -s http://<EC2>:8080/actuator/prometheus | grep tomcat_threads`
      → `busy_threads` / `current_threads` / `config_max_threads` **3줄**, 각 줄에 `application="triagain"` 라벨. *(사용자)*
- [ ] **3. 버킷 노출** — `curl -s http://<EC2>:8080/actuator/prometheus | grep http_server_requests_seconds_bucket`
      → `le="..."` 라벨 붙은 **여러 줄**. *(사용자)*
- [ ] **4. 부하 주입** — k6 수십 초 주입(버킷/스레드는 요청이 있어야 값이 쌓임). *(사용자)*
- [ ] **5. Prometheus** — :9090에서 값 반환:
      `tomcat_threads_busy_threads{application="triagain"}` /
      `histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket{application="triagain"}[1m])) by (le))` *(사용자)*
- [ ] **6. Grafana** — :3000 대시보드:
      · "Tomcat Threads" 패널 Busy/Current/Max 3선
      · "HTTP p95 / p99 Latency" 패널 p95·p99 선(No data 해소)
      (프로비저닝 자동 리로드 안 되면 grafana 컨테이너 재시작) *(사용자)*

**빌드 검증(백엔드):** `./gradlew compileJava checkstyleMain bootJar` 그린 → 산출 jar 경로 보고.

**롤백:** 설정·대시보드 파일 revert만 하면 원상복구. 런타임/데이터 마이그레이션 없음.

---

## 판독 가이드 (부하 중 병목 판별)

- Busy가 Max(200)에 **안 붙었는데 드롭** → 스레드 고갈 아님 → **accept 큐(acceptCount 100) 오버플로**가 원인 (기존 가설 확증)
- Busy가 **200에 붙어서 드롭** → tomcat 워커 풀 자체 병목 (threads.max 상향 검토)
- ⚠️ accept 큐 깊이 자체는 tomcat 지표에 안 나옴 → 기존처럼 `ss -tln`(Send-Q=100)·`nstat ListenOverflows`로 커널에서 병행 확인.
