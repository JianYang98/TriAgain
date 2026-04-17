# 부하테스트 환경 (실측값)

> Day 1~6 모든 부하테스트가 이 환경에서 수행됐다.
> 측정일 2026-04-16 기준. 실측은 EC2 SSH + RDS psql + `/actuator/prometheus` + EC2 IMDS로 수집.

## ⚠️ 이전 핸드오프 정정

- 이전 세션 handoff/메모리: **"t2.micro / 1 vCPU"** 로 기록돼 있었으나,
  EC2 IMDS 실측 결과 **t3.micro / 2 vCPU**였음.
- 이로 인해 기존 해석 "TPS 920은 1 vCPU 단일 스레드 포화 한계"는 재검토 필요.
  실제로는 **t3.micro (2 vCPU, burstable credits 기반)** 포화 한계. Burstable 크레딧 소진 가능성도 섞여 있을 수 있음.

## EC2 (애플리케이션 서버)

| 항목 | 값 | 출처 |
|------|-----|------|
| 인스턴스 타입 | **t3.micro** | IMDS `instance-type` |
| vCPU | 2 (Burstable) | `/proc/cpuinfo` |
| 메모리 | 916 MiB | `free -h` |
| AZ | ap-northeast-2a | IMDS `placement/availability-zone` |
| Public IP | 15.164.69.243 | - |
| OS | Amazon Linux 2023 | - |

## JVM / 애플리케이션

| 항목 | 값 | 출처 |
|------|-----|------|
| JVM | OpenJDK Corretto 17.0.18 | `java -version` |
| Heap max | `-Xmx512m` (≈ 500MB 실측) | JAR 기동 cmdline, `jvm_memory_max_bytes` |
| 프로파일 | `prod,loadtest` | `--spring.profiles.active` |
| Spring Boot | 3.4 | `build.gradle` |

### 기동 명령 (참고)

```bash
java -Xmx512m -jar ~/triagain-0.0.1-SNAPSHOT.jar \
  --spring.profiles.active=prod,loadtest
```

환경변수: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`.
`INTERNAL_API_KEY`는 application-loadtest.yml에서 오버라이드됨 (`loadtest-internal-key`).

## Spring Boot 설정

### HikariCP (전부 기본값 — yml 명시 없음)

| 속성 | 실측 | 비고 |
|------|------|------|
| `maximum-pool-size` | **10** | Spring Boot 기본 |
| `minimum-idle` | **10** | = max |
| `connection-timeout` | 30,000ms (기본) | |
| `idle-timeout` | 600,000ms (기본) | |
| `max-lifetime` | 1,800,000ms (기본) | |

**실측 누적 지표 (Day 1~6 통합)**:
- `hikaricp_connections_acquire_seconds_count`: 3,442,047
- `hikaricp_connections_acquire_seconds_max`: 0.59 ms (peak)
- `hikaricp_connections_acquire_seconds_sum / count`: 평균 **≈ 36 μs**
- **`hikaricp_connections_timeout_total`: 0** ← Day 1~6 전 구간에서 pool 고갈로 인한 timeout **0회**

이 값이 중요한 이유는 **지금까지 측정한 모든 구간(VU 10~300, TPS 920 포화점)에서 DB pool이 한 번도 병목이 아니었다**는 뜻. CPU/스레드 경합이 먼저 포화됐다는 간접 증거.

### Tomcat (전부 기본값 — yml 명시 없음)

| 속성 | 기본값 | 비고 |
|------|--------|------|
| `server.tomcat.threads.max` | 200 | Spring Boot 기본 |
| `server.tomcat.threads.min-spare` | 10 | |
| `server.tomcat.accept-count` | 100 | |
| `server.tomcat.max-connections` | 8192 | |

> VU 300에서 동시 실행 스레드가 아무리 많아도 200 한도 내. accept-count 100 감안 시 300까지는 큐잉으로 흡수됐고, Breaking Point는 Tomcat 스레드가 아닌 CPU에서 왔을 가능성.

### 명시적 커스텀 설정

| 속성 | 값 | 파일:라인 |
|------|-----|----------|
| `spring.task.scheduling.pool.size` | **4** | `application.yml:24` |
| `spring.task.scheduling.thread-name-prefix` | `sched-` | `application.yml:25` |
| `hibernate.default_batch_fetch_size` | 100 | `application.yml:10` |
| `spring.flyway.baseline-on-migrate` | true / baseline 6 | `application.yml:28-29` |
| `jwt.access-token-expiration` | 86,400,000 (24h) | `application-loadtest.yml:6` |
| `app.crypto.apple-refresh-key` | 테스트용 하드코딩 | `application-loadtest.yml:12` |
| `internal.api-key` | `loadtest-internal-key` | `application-loadtest.yml:15` |
| FCM | 비활성 → `NoOpNotificationSendAdapter` | `application-loadtest.yml:17-19` |

> FCM NoOp: 실제 Firebase 호출 없이 즉시 true. 스케줄러 테스트에서 `send-reminders`/`crew-start-notifications`이 수 ms로 끝난 이유 중 하나.

## RDS (데이터베이스)

| 항목 | 값 | 출처 |
|------|-----|------|
| 인스턴스 클래스 | **db.t4g.micro** (추정) | aarch64 + max_conn 79 + shared_buffers 180MB 역산. AWS 콘솔 추후 확인 권장 |
| 엔진 | PostgreSQL 17.6 (aarch64) | `SELECT version()` |
| 엔드포인트 | `triagain-db.cxis2q422sto.ap-northeast-2.rds.amazonaws.com:5432` | - |
| DB 이름 | `triagain` | - |
| Multi-AZ | 미확인 | |

### Postgres 주요 파라미터 (실측 `current_setting`)

| 파라미터 | 값 |
|---------|-----|
| `max_connections` | **79** |
| `shared_buffers` | 180 MB (184,648 kB) |
| `work_mem` | 4 MB |
| `effective_cache_size` | 360 MB (369,296 kB) |
| `maintenance_work_mem` | 64 MB |
| `wal_buffers` | 5.6 MB (5,768 kB) |
| `max_wal_size` | 2 GB |

> **max_connections=79 vs HikariCP pool=10**: 여유 69. 단일 EC2 애플리케이션이 pool을 꽉 채워도 RDS 커넥션 자체는 병목이 될 수 없음. 수평 확장 시 최대 7개 인스턴스까지 현재 RDS로 수용 가능 (여유 고려).

## 네트워크

- EC2 ↔ RDS: 같은 VPC, 같은 AZ (ap-northeast-2a) 추정 → 네트워크 RTT 1ms 미만
- 측정 클라이언트(k6): EC2 로컬(localhost) 또는 로컬 개발머신(인터넷 경유)
  - 이번 세션: 인터넷 경유 (로컬 k6 → EC2 퍼블릭 IP). 측정된 API p95에는 인터넷 RTT 포함됨.

## 모니터링

- `/actuator/health`: 노출, permitAll
- `/actuator/prometheus`: 노출 (Prometheus scraping 용)
- `/actuator/metrics`: 노출
- Prometheus + Grafana: 로컬 Docker 컨테이너 (`scripts/start-monitoring.sh`)
- 메트릭 태그: `application=triagain`

관련 설정: `application.yml:31-38`
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,prometheus,metrics
```

## 부하테스트 관점 핵심 관찰

1. **CPU가 먼저 포화된다** — HikariCP timeout 0, Tomcat 스레드도 200 여유. TPS 920 포화는 app 서버 CPU 한계에서 발생.
2. **t3.micro는 Burstable** — 지속 100% CPU 시 크레딧 소진 후 baseline(10%)로 떨어질 수 있음. 장시간 부하에서 성능 저하 가능성 별도 관찰 필요. (이번 측정은 7분 피크가 최장, 크레딧 소진 전)
3. **DB pool은 과유여** — pool=10으로 VU 300, TPS 920을 무리 없이 흡수. 튜닝 여지는 있지만 현재 Phase 1 규모에서 불필요.
4. **스케줄러 풀 4개** — 6개 스케줄러가 모두 동시에 돌지 않고 순차 + 병렬 4개씩. 동시 부하 테스트에서 이 점 고려 필요.

## Raw 수집 명령 (재현용)

```bash
# RDS 파라미터
ssh -i ~/triagain-key.pem ec2-user@15.164.69.243 "PGPASSWORD='...' psql -h ...rds... -U triagain -d triagain -c \"SELECT current_setting('max_connections');\""

# HikariCP / JVM 실측
curl -s http://15.164.69.243:8080/actuator/prometheus | grep -E '^(hikaricp|jvm_memory_max)'

# EC2 인스턴스 타입
ssh ... "TOKEN=\$(curl -s -X PUT http://169.254.169.254/latest/api/token -H 'X-aws-ec2-metadata-token-ttl-seconds: 60'); curl -s -H \"X-aws-ec2-metadata-token: \$TOKEN\" http://169.254.169.254/latest/meta-data/instance-type"
```
