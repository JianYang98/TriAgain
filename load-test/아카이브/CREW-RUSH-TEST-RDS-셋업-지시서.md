# CREW-RUSH — 별도 test RDS 셋업 지시서 (새 박스 54.180.154.118 전용)

> **목적**: 새 앱 박스(`54.180.154.118`)가 **프로드 RDS를 물지 않도록** 별도 test RDS(db.t4g.micro)를 만들고,
> 스키마·시드를 채운 뒤 컨테이너를 test RDS로 재기동한다.
> **왜**: 병목이 DB 커넥션 계층(HikariCP acquire)이라, 앱만 격리하고 DB를 프로드와 공유하면 부하가 프로드 RDS를 직접 때려 **실유저가 DB 커넥션을 굶는다.** DOCKER 셋업 런북 L85 "운영 RDS에 부하 금지"의 실행판.
>
> **부모 문서**: `CREW-RUSH-DOCKER-환경-셋업-런북.md` Phase B(별도 test RDS) + Phase C②(기동). 이 지시서는 그 Phase B를 새 박스 기준으로 실행 명령까지 채운 것.
>
> **⚠️ Tier 3 (인프라 신규 생성)** — RDS 생성·기동은 **사용자(운영자) 본인이 실행·판단**한다. 에이전트는 지시서까지. 각 단계 ✅ 체크포인트를 통과한 뒤 다음으로 넘어간다.

---

## 0. 먼저 채울 값 (시작 전 확보)

| 값 | 어디서 | 비고 |
|----|--------|------|
| `PROD_RDS_HOST` | 프로드 RDS 엔드포인트 | `triagain-db.cxis2q422sto.ap-northeast-2.rds.amazonaws.com` (런북 L43·68 확인값) |
| `PROD_RDS_USER` / `PROD_RDS_PW` | 프로드 RDS 접속 계정 | pg_dump 원본용. **읽기만** 함 |
| `BOX_SG_ID` | 새 박스(54.180)의 보안그룹 ID | test RDS 인바운드 소스로 지정 |
| `BOX_VPC` / `BOX_AZ` / `SUBNET_GROUP` | 새 박스의 VPC·AZ·서브넷그룹 | test RDS를 **같은 VPC·같은 AZ**에 (지연 최소화, 런북 L36) |
| `TEST_RDS_PW` | test RDS 마스터 비번 (직접 정함) | 아무 값 (기록해둘 것) |

- **DB명은 `triagain`으로 통일** — 그래야 `DB_URL`의 경로가 `/triagain`로 프로드와 동일.
- **prod RDS 접속이 새 박스에서 되는 건 이미 증명됨** — 자동 기동됐던 프로드 컨테이너가 프로드 RDS에 붙었으니 `box→prod RDS:5432`는 열려 있다. 그래서 아래 DB 작업은 전부 **새 박스 안에서** 실행한다(맥에서 프로드 RDS 여는 것 방지).

---

## 1. test RDS 생성 (AWS 콘솔, Tier 3 👤)

RDS → **Create database** → Standard create:

| 항목 | 값 | 근거 |
|------|-----|------|
| Engine | **PostgreSQL 17** | 프로드 동일 (실측 17.9 · 07-12 실측 정정, 기존 "16" 오기재) |
| Templates | Dev/Test | |
| DB instance class | **db.t4g.micro** | 프로드 동일 (ARM/Graviton, 2 vCPU/1 GiB · 07-12 실측 정정, 기존 "db.t3.micro" 오기재). 병목이 DB 티어라 버스트 거동 재현에 필수 |
| Multi-AZ | **No** | 단일, 테스트용 |
| Storage | **gp2 20 GiB** | 프로드 동일 (07-12 실측 정정, 기존 "gp3" 오기재) |
| VPC | **박스와 같은 VPC** (`BOX_VPC`) | |
| Subnet group | 박스와 같은 서브넷그룹 (`SUBNET_GROUP`) | |
| Availability Zone | **박스와 같은 AZ** (`BOX_AZ`) | 지연 최소 (런북 L36) |
| Public access | **No** | VPC 내부에서만 접속 |
| VPC security group | 새로 생성 (예: `triagain-testrds-sg`) → 2단계에서 규칙 추가 | |
| Master username | `triagain` (또는 임의) | |
| Master password | `TEST_RDS_PW` | |
| Initial database name | **`triagain`** | DB_URL 경로 일치 |
| Backup retention | 1일(최소) | |

> ⛔ **프로드 RDS 스냅샷에서 복원하지 말 것** — 실유저 데이터(PII)가 테스트 박스로 복사된다. **빈 RDS 생성 후 스키마만 복제**가 맞다(4단계).

**✅ 체크포인트 1**: RDS 상태가 **Available**. 엔드포인트 주소 확보 → 이걸 `TEST_RDS_ENDPOINT`로 기록.

---

## 2. 보안그룹 — test RDS ← 새 박스 5432 인바운드 (👤)

test RDS의 SG(`triagain-testrds-sg`) 인바운드 규칙 추가:

| Type | Port | Source |
|------|------|--------|
| PostgreSQL | 5432 | **`BOX_SG_ID`** (새 박스의 SG) |

> 소스를 박스 SG로 지정 = 박스만 접속 가능. IP가 바뀌어도 유효.

**✅ 체크포인트 2**: 새 박스에서 접속 확인
```bash
# 새 박스 안에서
nc -zv <TEST_RDS_ENDPOINT> 5432    # succeeded 떠야 함 (nc 없으면 3단계 psql로 확인)
```

---

## 3. 새 박스에 psql/pg_dump 클라이언트 + sql 파일 준비 (👤)

```bash
# (맥에서) sql 시드 파일을 박스로 복사
cd /Users/jian/Projects/triagain/triagain-back/load-test
scp -i <키.pem> -r sql ec2-user@54.180.154.118:~/loadtest-sql

# (새 박스 안에서) postgresql 클라이언트 설치 — 서버(PG16) 이상 버전
sudo dnf install -y postgresql17   # Amazon Linux 2023 (07-12 실측 정정: 프로드 17.9)
# 안 되면: sudo amazon-linux-extras enable postgresql17 && sudo yum install -y postgresql
psql --version   # 17.x 확인 (프로드 17.9 → pg_dump도 17+ 필수, 낮으면 server version mismatch로 거부)
```

**✅ 체크포인트 3**: 박스에서 `psql --version` = 17.x, `~/loadtest-sql/`에 `01_users.sql`·`07_rush_crews.sql`·`07_rush_reset.sql` 존재.

---

## 4. 스키마 복제 (프로드 → test RDS) — **새 박스 안에서** (👤)

> ⚠️ **왜 pg_dump인가 (런북 L88)**: `baseline-on-migrate: true` + `baseline-version: 6`이라, **빈 RDS**에 컨테이너가 붙으면 Flyway가 V1~V6를 "적용됐다 치고" 스킵 → 그 테이블들이 안 생겨 앱이 깨진다. 그래서 스키마는 Flyway에 맡기지 말고 프로드에서 통째 복제한다.

RDS 비번 da6412^^Adb

```bash
# 새 박스 안에서. 접속 문자열 두 개 정의
export PROD_DB="postgresql://<PROD_RDS_USER>:<PROD_RDS_PW>@triagain-db.cxis2q422sto.ap-northeast-2.rds.amazonaws.com:5432/triagain"
export TEST_DB="postgresql://triagain:<TEST_RDS_PW>@<TEST_RDS_ENDPOINT>:5432/triagain"

# 새 박스 안에서. 접속 문자열 두 개 정의
export PROD_DB="postgresql://triagain:da6412^^Adb@triagain-db.cxis2q422sto.ap-northeast-2.rds.amazonaws.com:5432/triagain"
export TEST_DB="postgresql://triagain:da6412^^Adb@triagain-test-db.cxis2q422sto.ap-northeast-2.rds.amazonaws.com:5432/triagain"


# (a) 구조만 복제 (데이터 제외 → 실유저 PII 안 옮김)
pg_dump --schema-only "$PROD_DB" | psql "$TEST_DB"

# (b) 🔴 [신규 — 런북에 없는 필수 단계] Flyway 이력 데이터도 복제
#     이게 없으면 test RDS의 flyway_schema_history가 비어 있어,
#     컨테이너 부팅 시 Flyway가 V7+ 를 "이미 존재하는 테이블"에 재적용 → "relation already exists" 에러로 기동 실패.
#     이력을 채워두면 Flyway가 "다 적용됨" 으로 보고 no-op → 깔끔.
pg_dump --data-only -t flyway_schema_history "$PROD_DB" | psql "$TEST_DB"
```

**✅ 체크포인트 4**:
```bash
psql "$TEST_DB" -c "\dt" | head                          # users, crews, crew_members, challenges ... 존재
psql "$TEST_DB" -c "SELECT max(version) FROM flyway_schema_history;"   # 프로드와 동일 최신 버전 (예: 22)
```
→ 테이블 목록이 프로드와 같고 flyway 이력의 max version이 프로드와 일치하면 통과.

---

## 5. 시드 데이터 (러시 크루 시나리오) — **새 박스 안에서** (👤)

> 크루 참가 러시 테스트용: 유저 1000명(scale L) + 러시 크루 10개. `01_users`는 `app.scale` 세션 변수를 읽는다(소스 확인).

```bash
cd ~/loadtest-sql
# 유저 1000명 (같은 psql 세션에서 SET → -f 순서로 실행)
psql "$TEST_DB" -c "SET app.scale='L';" -f 01_users.sql     # → user_count 1000
# 러시 크루 10개 (정원 기본 10)
psql "$TEST_DB" -f 07_rush_crews.sql                        # → loadtest-rush-crew-1..10, current_members=0, allow_late_join=t
```

**정원 100으로 측정할 거면**(ABC 런북 L87와 동일하게):
```bash
psql "$TEST_DB" -c "UPDATE crews SET max_members=100, current_members=0 WHERE id LIKE 'loadtest-rush-crew-%';"
```

**✅ 체크포인트 5**:
```bash
psql "$TEST_DB" -c "SELECT count(*) FROM users WHERE id LIKE 'loadtest-%';"          # 1000
psql "$TEST_DB" -c "SELECT id,current_members,max_members,allow_late_join FROM crews WHERE id LIKE 'loadtest-rush-crew-%' ORDER BY id;"
```

---

## 6. 컨테이너를 test RDS로 재기동 (핸드오프 → DOCKER 런북 Phase C②)

> 이미지는 이미 빌드·push 중인 `devjian/triagain:loadtest`(⛔`:latest` 아님, feat/load-test, amd64). 여기선 **`DB_URL`만 test RDS로** 채워 기동.

```bash
# 새 박스 안에서
docker rm -f triagain-loadtest 2>/dev/null || true
docker run -d --name triagain-loadtest \
  -p 8080:8080 \
  -e DB_URL="jdbc:postgresql://<TEST_RDS_ENDPOINT>:5432/triagain" \
  -e DB_USERNAME="triagain" \
  -e DB_PASSWORD="<TEST_RDS_PW>" \
  -e JWT_SECRET="dGVzdC1zZWNyZXQta2V5LWZvci1sb2FkdGVzdC10cmlhZ2Fpbg==" \
  devjian/triagain:loadtest \
  --spring.profiles.active=prod,loadtest \
  --triagain.crew.lock-strategy=<PESSIMISTIC|CONDITIONAL> \
  '--spring.flyway.ignore-migration-patterns=*:missing'
```
- `JWT_SECRET`은 **아무 값이나 OK** — 토큰을 이 서버 기동 후에 발급(7단계)하므로 서명/검증이 자기일관이면 됨.
- `DB_USERNAME`/`DB_PASSWORD`는 **test RDS** 계정(1단계에서 정한 것). ⛔ 프로드 값 넣지 말 것.
- ⛔ `-Xmx`·`--memory`·`--network host` 금지 (런북 L138~140 — 프로드 재현 깨짐).

**✅ 체크포인트 6** (특히 Flyway·DB 대상 확인):
```bash
sleep 15
curl -f http://localhost:8080/actuator/health                          # UP
docker logs triagain-loadtest 2>&1 | grep -i 'flyway\|hikari\|jdbc:postgresql\|lock-strategy\|Started'
```
- 로그의 JDBC URL이 **test RDS 엔드포인트**인지 눈으로 확인 (프로드 아님).
- Flyway 로그: `Successfully validated` / `Schema is up to date` / `No migration necessary` 중 하나 → no-op 정상.
  - ⚠️ **체크섬 오류**(`Migration checksum mismatch`, 이 프로젝트 V22 드리프트 이력 있음)가 뜨면 → 프로그램 인자에 `--spring.flyway.enabled=false` 추가해 재기동(스키마는 이미 프로드 복제라 `ddl-auto: validate`가 정합성 보증).
- lock-strategy 로그 확인 — 안 찍히면 기본 **PESSIMISTIC**로 뜬다(런북 L150). 의도한 전략인지 확인.

---

## 7. 토큰 발급 + IP 반영 (핸드오프 → DOCKER 런북 Phase C④ / D)

```bash
# (맥에서) 토큰 800개 — 새 박스 IP로
cd /Users/jian/Projects/triagain/triagain-back/load-test
./scripts/generate-tokens.sh http://54.180.154.118:8080 800
tail -n +2 tokens.csv | wc -l    # → 800

# 모니터링(로컬 docker) — 새 박스 IP 주입 (기존 것 떠 있으면 먼저 stop)
cd scripts && ./stop-monitoring.sh 2>/dev/null || true
./start-monitoring.sh 54.180.154.118    # Grafana localhost:3000, Prometheus localhost:9090 target UP

# k6 실행 시 BASE_URL만 새 박스로. 매 측정 직전 리셋:
psql "$TEST_DB" -f ~/loadtest-sql/07_rush_reset.sql     # ← test RDS에 실행 (프로드 아님!)
k6 run --env BASE_URL=http://54.180.154.118:8080 k6/crew-rush-jian.js   # (활성 스크립트)
```

**✅ 체크포인트 7**: `tokens.csv` 800줄, Prometheus `localhost:9090` 에서 target(54.180...:8080) **UP**, k6 첫 응답 정상.

---

## 8. 뒷정리 (측정 종료 후 👤)

- **test RDS**: 다음 세션까지 안 쓸 거면 → **최종 스냅샷 후 삭제**(db.t4g.micro라도 방치 시 과금, 런북 L231).
- **새 박스(54.180)**: **STOP 또는 terminate** (t3.micro 크레딧/과금).
- ⚠️ **프로드 RDS엔 아무 것도 안 썼는지** 확인 — 이 절차대로면 loadtest 데이터는 전부 **test RDS**에만 있어야 정상.

---

## 참조한 실제 파일

- `triagain-back/load-test/CREW-RUSH-DOCKER-환경-셋업-런북.md` (Phase B L83~104, Phase C L109~158)
- `triagain-back/load-test/CREW-RUSH-ABC-RUNBOOK.md` (L43·68 프로드 RDS 호스트, L82·87·101·147 시드/기동/BASE_URL)
- `triagain-back/load-test/scripts/start-monitoring.sh`, `scripts/aws-ssh.sh`
- `triagain-back/load-test/monitoring/prometheus.yml`
- `triagain-back/load-test/k6/lib/config.js` (BASE_URL env 주입)
- `triagain-back/load-test/sql/01_users.sql` (app.scale 세션변수, loadtest-user-*), `sql/07_rush_crews.sql` (loadtest-rush-crew-1..10, max_members 10), `sql/07_rush_reset.sql` (loadtest-rush-crew-% 프리픽스 한정 리셋)
- `triagain-back/src/main/resources/application.yml` (flyway baseline-on-migrate:true, baseline-version:6)
- `triagain-back/src/main/resources/application-prod.yml` (DB_URL/DB_USERNAME/DB_PASSWORD/JWT_SECRET env, ddl-auto:validate)
- `triagain-back/src/main/resources/application-loadtest.yml` (마이그레이션 무추가 — 관측 메트릭·JWT 24h·internal.api-key만)

> **[신규] 표기 항목** = 실제 코드/런북에 없고 이 지시서에서 추가한 것: **4-(b) flyway_schema_history 데이터 복제 단계**(부팅 시 Flyway 재적용 에러 방지). 나머지 명령은 위 참조 파일에서 복사/도출.
