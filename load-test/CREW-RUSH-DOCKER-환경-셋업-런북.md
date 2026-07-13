# 크루 러시 부하테스트 — Docker 프로드 재현 환경 셋업 런북

> **목표**: 지금까지의 **bare app(`java -Xmx512m -jar`)** 방식을 버리고, **운영과 동일한 Docker 런타임**을
> **별도 부하테스트 인스턴스**에서 재현한다. 측정 결과가 실제 운영 동작으로 전이되게 만드는 것이 목적.
> **담당**: 👤 사용자(인프라/AWS/SSH/실행/배포결정) · 🤖 오케스트레이터(지시서·명령제공·결과해석·문서)
> **관계 문서**: 측정 절차 본체는 `CREW-RUSH-ABC-RUNBOOK.md`(Phase 5), 분석은 `CREW-RUSH-분석-작업지시서.md`
> 진행하며 `[ ]` → `[x]`.
>
> **진행상태(07-13)**: A①②③④ ✅(새박스 172.31.1.242 재기동, public **13.125.7.72** — A③ 클린업 확인완료: `docker ps -a` 빈 상태·config 없음·EIP 미부착) · B①②③ ✅(test RDS·스키마+flyway V24·**시드 재실행: 유저 1000+러시크루 10**) · 운영 RDS 임시 SG 규칙 삭제 ✅ · C① 이미지 빌드(`devjian/triagain:loadtest`) **진행 중**(로컬 Mac buildx) · 남은 것: C② 기동 → C③④ 확인·토큰 → accept-count 결정 → 관측(D) → 측정(E) · 참고: JWT_SECRET은 임의값(토큰은 서버 test-login 발급, 프로드 시크릿 사용 금지)

---

## 왜 이 문서가 필요한가 (배경)

지금까지: **운영(Docker)을 내리고, 같은 EC2에서 bare `java -jar`로 테스트** → 하드웨어는 프로드 실물이었지만 **런타임이 달랐다.**

| 항목 | 운영 (Docker) | 지금까지 테스트 (bare) | 영향 |
|------|--------------|----------------------|------|
| **JVM 힙** | `-Xmx` 없음 → ergonomic 25% (t3.micro=**~256MB**) | `-Xmx512m` | ⭐ GC 빈도/pause 달라짐 |
| **네트워크 경로** | client→iptables DNAT→컨테이너 veth→Tomcat큐(container netns) | client→Tomcat큐 직접(host netns) | ⭐ accept큐/SYN 오버플로 **관측 지점이 달라짐** |
| 프로파일 | `prod` | `prod,loadtest`(이미 맞음) | loadtest는 관측 메트릭만 추가 |
| 다운타임 | — | 테스트마다 **실유저 다운** | 앱 출시됨 → 회피 필요 |

**근거 파일**: `Dockerfile`(ENTRYPOINT `java -jar app.jar`, 힙 플래그 0개), `.github/workflows/deploy.yml`(L131 `docker run -p 8080:8080`), `CREW-RUSH-ABC-RUNBOOK.md`(L98 bare 기동).

**이번 셋업의 3대 변경**:
1. **별도 인스턴스**(운영 EC2 AMI 복제) → 다운타임 0
2. **Docker로 기동**(`prod,loadtest`, **`-Xmx`/`--memory` 금지**) → 운영 힙·네트워크 재현
3. **별도 test RDS**(db.t4g.micro, 07-12 실측 정정) → 운영 데이터 오염 0

---

## 필요 값 (셋업 전 확보) 👤

- [ ] **운영 EC2 인스턴스 ID** — `15.164.69.243`에 해당하는 `i-xxxx` (AWS 콘솔 EC2)
- [ ] 운영 EC2 타입 = **t3.micro** (2 vCPU / 1 GiB) ✅ 확인됨 → 새 박스도 동일
- [ ] 운영 RDS 클래스 = **db.t4g.micro** ✅ (07-12 콘솔 실측 정정 — 기존 "db.t3.micro"는 오기재) → test RDS도 동일
- [ ] 운영 EC2의 **서브넷/AZ**, VPC ID (새 박스·test RDS를 같은 AZ에)
- [ ] `JWT_SECRET` (운영 값 재사용 or 테스트용 임의값 — loadtest는 자체 검증뿐이라 임의값도 OK)

> ⚠️ **DB 자격증명·JWT는 문서에 하드코딩 금지.** 실행 시 환경변수로만 주입.

---

## Phase A — 새 부하테스트 인스턴스 (AMI 복제) 👤

> **복제되는 것**(root 볼륨): OS·커널·도커·sysctl·파일·**박힌 프로드 컨테이너**
> **런치 때 새로 정하는 것**(안 딸려옴): IP·보안그룹·IAM role·서브넷/AZ·인스턴스 타입

- [x] **① 운영 이미지 뜨기** (`--no-reboot` = 운영 무정지, 다운타임 0):
  ```bash
  aws ec2 create-image --instance-id <PROD_INSTANCE_ID> \
    --name triagain-loadtest-base --no-reboot
  # → ImageId(ami-xxxx) 반환. describe-images로 state=available 대기
  ```
  (콘솔: 운영 인스턴스 → Actions → Image and templates → **Create image**, "Reboot" 체크 해제)

- [x] **② 그 AMI로 새 인스턴스** — 운영과 동일 타입·같은 AZ·**전용 SG**:
  ```bash
  aws ec2 run-instances --image-id <ami-xxxx> \
    --instance-type t3.micro \
    --subnet-id <운영과 같은 AZ 서브넷> \
    --security-group-ids <전용 loadtest SG> \
    --key-name <SSH 키> --count 1
  # IAM role: 크루 가입 부하는 S3 불필요 → --iam-instance-profile 생략(role 없음) 권장
  ```

- [x] **③ 부팅 직후 클린업** ⚠️ **(가장 큰 함정 — 반드시)** ✅(07-13 확인: `docker ps -a` 빈 상태·config 디렉토리 없음):
  AMI에 프로드 컨테이너가 `--restart unless-stopped`로 박혀 있어 **켜지자마자 프로드 RDS(프로드 시크릿)로 자동 접속**한다.
  ```bash
  docker ps                                   # triagain 프로드 컨테이너 살아있는지 확인
  docker stop triagain && docker rm triagain  # 즉시 제거 (필수)
  rm -rf /home/ec2-user/triagain/config       # 복제된 프로드 firebase키/설정 제거 (loadtest=NoOp라 미사용)
  ```

- [x] **④ 격리 확인**: 프로드 **EIP·도메인·LB 재사용 안 함**. 이 박스는 프로드 트래픽 경로에 없어야 함. ✅(07-13: EIP 미부착·자동할당 public IP 13.125.7.72)

### 네트워크/IP 메모
- 새 인스턴스 → **private IP 새로(고정)**, **public IP 새로(stop/start마다 바뀜)**.
- **k6는 같은 VPC의 제3 머신에서 private IP로 때리는 걸 권장** — RTT 고정(집 네트워크 변동이 과거 A3↔A4 비교를 오염시킨 전례) + stop/start에도 IP 불변.
- 로컬 노트북 Prometheus가 스크레이프하려면 public IP:8080 필요(5s 간격이라 RTT 영향 미미). public IP 고정하려면 **전용 EIP**(프로드 것 아님) 부착.

---

## Phase B — 별도 test RDS (db.t4g.micro, 07-12 실측 정정) 👤

> ⚠️ **운영 RDS(`triagain-db.cxis2q422sto...`)에 부하 금지** — 데이터 오염 + 운영 리스크. 반드시 별도.

- [x] **① test RDS 생성**: 엔진 **PostgreSQL 17**, 클래스 **db.t4g.micro**(운영 동일, ARM/Graviton), **gp2 20 GiB**, **같은 AZ/VPC**, 새 박스 SG에서 5432 인바운드 허용. *(07-12 콘솔 실측 정정 — 기존 "PostgreSQL 16 / db.t3.micro / gp3"는 오기재)*
- [x] **② 스키마 부트스트랩** ✅(07-12, max(version)=24 검증) — 운영 스키마를 직접 붓는다(07-12 절차 확정 — Flyway 자동생성은 `baseline-version: 6` 때문에 빈 RDS에서 V1~V6가 스킵되는 함정):
    ```bash
    # 사전: pg_dump --version ≥ 17 (운영=PG17, 구버전 pg_dump는 서버 거부)
    #       test RDS에 triagain DB 없으면: psql ".../postgres" -c 'CREATE DATABASE triagain;'
    # ⚠️ PROD_DB는 반드시 pg_dump 자리에만 (파이프 방향 뒤집히면 운영 DDL 오염)
    pg_dump --schema-only "$PROD_DB" | psql "$TEST_DB"
    # ⚠️ Flyway 이력 rows까지 복제 필수 — schema-only는 history가 빈 테이블이라
    #    Phase C 기동 시 Flyway가 재적용 시도 → "already exists"로 기동 실패
    pg_dump --data-only -t flyway_schema_history "$PROD_DB" | psql "$TEST_DB"
    # 검증: 테이블 목록 운영 대조 + 이력 최신 버전(V24) 확인
    psql "$TEST_DB" -c '\dt'
    psql "$TEST_DB" -c 'select max(version) from flyway_schema_history;'
    ```
  - 운영 이력의 V24가 feat/load-test jar에 없는 건 기동 인자 `*:missing`이 처리(기존 명령 그대로).
- [x] **③ 시드 데이터**(기존 SQL 재사용, `load-test/`에서 실행) ✅(07-13: 박스에서 실행, truncate→유저 1000·러시크루 10 재시드 — 이전 시드분 1000+10 삭제 후):
  ```bash
  TEST_DB="postgresql://<u>:<pw>@<TEST_RDS_HOST>:5432/triagain"
  psql "$TEST_DB" -f sql/00_truncate.sql   # (선택) 깨끗이
  psql "$TEST_DB" -c "SET app.scale='L';" -f sql/01_users.sql   # 유저 1000
  psql "$TEST_DB" -f sql/07_rush_crews.sql                      # 러시 크루 10개
  # 토큰 800: Phase C 서버 기동 후 generate-tokens.sh (아래)
  ```

---

## Phase C — 앱을 Docker로 기동 (프로드 재현) 👤 ← 핵심

> 운영 `deploy.yml`과 **동일 이미지·동일 publish 방식**. 관측 위해 `prod,loadtest`만 얹음.
> **전략 전환(A/B/C)** = 컨테이너 재생성(`docker rm` 후 `--triagain.crew.lock-strategy` 바꿔 재실행).

- [ ] **① 이미지 확보** — ⚠️ **`latest` 사용 불가 확정(07-11)**: PR#92(feat/load-test→develop)가 **CLOSED(미머지)** → `devjian/triagain:latest`에는 `application-loadtest.yml`(관측 메트릭)·`GcTriggerController`(pre-GC 게이트)가 **없다**(lock-strategy만 develop에 있음). GC 게이트가 없으면 k6 setup()이 throw로 전부 실패한다. → **feat/load-test 브랜치에서 직접 빌드** (Dockerfile은 main과 동일 확인 07-11 → 런타임 재현 유효):
  ```bash
  # 로컬 Mac(Apple Silicon)에서 — t3.micro는 x86_64라 --platform linux/amd64 필수
  cd triagain-back && git switch feat/load-test
  docker buildx build --platform linux/amd64 -t devjian/triagain:loadtest --push .
  # 새 박스에서: docker pull devjian/triagain:loadtest
  ```
  - ⛔ **절대 `:latest` 태그로 push 금지** — 다음 prod 배포가 오염 이미지를 pull한다. 반드시 `:loadtest` 별도 태그.
  - gradle 스테이지가 QEMU 에뮬레이션이라 느릴 수 있음(수십 분 가능). 너무 느리면 **빠른 대안**: 로컬 `./gradlew bootJar`(같은 wrapper라 산출물 동일) 후 운영 Dockerfile의 Stage 2만 복제한 `Dockerfile.loadtest`(FROM eclipse-temurin:17-jre-alpine + tzdata/curl + COPY jar + 동일 ENTRYPOINT, `-f Dockerfile.loadtest`)로 buildx — 런타임 스테이지가 동일하므로 재현성 무손실.
  - Docker Hub에 안 올리려면: `docker save devjian/triagain:loadtest | gzip > img.tgz` → scp → 새 박스에서 `docker load < img.tgz`.

- [ ] **② 기동**:
  ```bash
  docker rm -f triagain-loadtest 2>/dev/null || true
  docker run -d --name triagain-loadtest \
    -p 8080:8080 \
    -e DB_URL="jdbc:postgresql://<TEST_RDS_HOST>:5432/triagain" \
    -e DB_USERNAME="<test>" -e DB_PASSWORD="<test>" \
    -e JWT_SECRET="<jwt>" \
    devjian/triagain:loadtest \
    --spring.profiles.active=prod,loadtest \
    --triagain.crew.lock-strategy=<PESSIMISTIC|OPTIMISTIC|CONDITIONAL> \
    --server.tomcat.accept-count=256 \
    '--spring.flyway.ignore-migration-patterns=*:missing,*:future'
  ```
  - **[정정 07-13] flyway 패턴 `*:missing` → `*:missing,*:future`**: 첫 기동 실패 실측. jar(feat/load-test)의 최고 마이그레이션=V22인데 test RDS 이력에 V23·V24가 있음 → Flyway는 이를 "missing"이 아닌 **"future"**(로컬 최고 버전보다 높은 applied) 상태로 분류하므로 `*:missing`만으로는 validate를 통과 못 함. 에러 메시지("Detected applied migration not resolved locally: 23, 24")는 두 상태가 공유해서 문구만으론 구분 안 됨.
  - **[결정 07-13 👤] accept-count=256 (시리즈 연속성)** — 세션 단위 고정. 운영재현(기본 100)이 아닌 기존 07-03 6차~C12/A5/A8 시리즈와의 비교축 선택. 운영재현=100 기준선은 별도 세션에서.
  - **[결정 07-13 👤] 첫 기동 전략 = PESSIMISTIC** (C-시리즈 최장 히스토리 비교축) → 이후 같은 세션에서 CONDITIONAL 재생성(통제쌍).
  - ⚠️ **로그 보존**: json-file 로그는 `docker rm`과 함께 소멸 — **전략 전환 재생성 직전 반드시** `docker logs triagain-loadtest > boot-<전략>-<날짜>.log 2>&1` 덤프(→ `results/0713/` 수집). 앱로그=유령409 판정 채널.
  - ⛔ **`-Xmx` 넣지 마** — 운영이 ergonomic이라 넣는 순간 달라짐(이번 재현의 핵심).
  - ⛔ **`--memory` 넣지 마** — 운영 `docker run`에도 없음(컨테이너가 full host RAM을 봐야 힙 산정이 동일).
  - ⛔ **`--network host` 금지** — 운영 docker-proxy 계층이 사라져 오히려 덜 같아짐. 반드시 `-p`.
  - `--log-driver=awslogs`는 **생략**(기본 json-file) — 프로드 CloudWatch 스트림 오염 방지. 로깅 WARN이라 성능차 무시 가능.
  - 프로파일/전략/flyway는 **트레일링 program-arg**로 전달(exec-form ENTRYPOINT에 append됨). 비밀값만 `-e`.

- [ ] **③ 확인**:
  ```bash
  sleep 15 && curl -f http://localhost:8080/actuator/health         # UP
  curl -s http://localhost:8080/actuator/prometheus | head          # 메트릭 노출
  docker logs triagain-loadtest 2>&1 | grep -i 'lock-strategy\|Started'  # 적용 전략 확인
  ```
  ⚠️ 로그에 전략이 안 찍히면(인자 누락) 기본 **PESSIMISTIC**로 뜬다 — 반드시 확인.

- [ ] **④ 토큰 발급**(서버 기동 후):
  ```bash
  ./scripts/generate-tokens.sh http://<새박스IP>:8080 800
  tail -n +2 tokens.csv | wc -l    # → 800
  ```

- [x] **⑤ accept-count 실험(옵션)**: Tomcat 큐 캡 조정 → 위 명령에 `--server.tomcat.accept-count=256` 추가. (호스트 `net.core.somaxconn`은 **별개 노브** — 커널 sysctl) **→ 07-13 결정: 이번 세션은 256 고정(② 명령에 반영됨)**

---

## Phase D — 관측 (Docker 대응) 👤

> ⚠️ **가장 중요한 방법론 변경**: bare 시절엔 host `nstat`/`ss`가 곧 Tomcat 큐였다.
> **Docker에선 host의 8080은 docker-proxy 소켓(그 유명한 `0/4096`)이고, 진짜 Tomcat accept큐는 컨테이너 netns 안에 있다.**
> host에서 그대로 재면 엉뚱한 큐를 본다 → **반드시 컨테이너 netns로 진입**.

- [ ] **컨테이너 PID 확보**:
  ```bash
  CPID=$(docker inspect -f '{{.State.Pid}}' triagain-loadtest)
  ```
  (host에 `iproute2`(ss/nstat)·`tcpdump`·`nsenter`(util-linux) 필요 — AMI 복제면 프로드에 이미 있음. 없으면 `sudo yum install -y iproute tcpdump util-linux`. nsenter는 host 바이너리를 컨테이너 **netns에서** 실행하므로 alpine 컨테이너여도 무관. 전부 `sudo`(다른 netns 진입엔 CAP_SYS_ADMIN 필요).)

> **판정 임계값 변경**: bare 시절 accept큐 참 기준은 Recv-Q 100 근처였지만, 이번 세션은 `accept-count=256` → **256** 기준. 아래 3종은 bare에서 쓰던 원본 스크립트(awk 필터·per-line 타임스탬프·0.3초 cadence·tcpdump)를 그대로 두고 **netns 진입만 추가**한 복붙판이다.

- [ ] **accept큐(Send-Q)·backlog 실측** (기동 직후 1회 — host↔컨테이너 차이 눈으로 확인):
  ```bash
  ss -lnt 'sport = :8080'                            # host → docker-proxy: 0/4096 (가짜)
  sudo nsenter -t $CPID -n ss -lnt 'sport = :8080'   # 컨테이너 → 진짜 Tomcat, Send-Q ≈ 256
  ```
  Send-Q=256이면 `--server.tomcat.accept-count=256` 실제 반영 확인 → 07-03 6차~C12/A5/A8 시리즈와 비교 가능 지점.

- [ ] **nstat 1초 시계열** (k6 시작 전, 별도 터미널 foreground — bare 원본 awk 필터/타임스탬프 유지, `nstat`만 netns 진입):
  ```bash
  LOG="$HOME/nstat-ts-$(date +%H%M%S).log"; echo "logging to: $LOG"
  while true; do
    ts=$(date '+%H:%M:%S')
    sudo nsenter -t $CPID -n nstat -asz 2>/dev/null | awk -v t="$ts" '
      /ListenOverflow|ListenDrop|SynRetrans|ReqQFull|Syncookie|BacklogDrop|OutRsts|AttemptFails|EstabResets|AbortOn|MemoryPressure/ {
        print t, $1, $2; fflush()
      }'
    sleep 1
  done | tee -a "$LOG"      # 백그라운드로 돌리려면 끝에 &
  ```
  매 줄 앞 `HH:MM:SS`(host `date`) = **k6 완료 시각 정렬** 그대로 가능. `-s`라 history 안 건드리고 매 초 컨테이너 netns의 /proc/net/{snmp,netstat}를 새로 읽음.

- [ ] **ss 상태 0.3초 시계열** (SYN-RECV/ESTAB 큐 채워지는 그림 — 두 `ss` 호출만 netns 진입):
  ```bash
  SSLOG=~/ss-state-$(date +%H%M%S).log
  while true; do
    ts=$(date '+%H:%M:%S')
    lq=$(sudo nsenter -t $CPID -n ss -lnt 'sport = :8080' | awk 'NR==2{print "acceptQ="$2"/"$3}')
    st=$(sudo nsenter -t $CPID -n ss -tan 'sport = :8080' | awk 'NR>1{c[$1]++} END{for(s in c)printf "%s=%d ",s,c[s]}')
    echo "$ts $lq | $st"; sleep 0.3
  done | tee "$SSLOG"
  ```
  SYN-RECV 치솟음 = SYN큐 압박 / acceptQ의 Recv-Q가 256 근처 = accept큐 참.

- [ ] **tcpdump SYN/RST 캡처** (패킷 레벨 드롭 지문 — 전체를 netns 안에서 실행):
  ```bash
  sudo nsenter -t $CPID -n tcpdump -i any -s 96 -w tcpdump-$(date +%H%M%S).pcap \
    'tcp port 8080 and (tcp[tcpflags] & (tcp-syn|tcp-rst) != 0)' \
    > tcpdump-$(date +%H%M%S)-startup.log 2>&1 &
  echo $! > tcpdump.pid
  ```
  `nsenter -n`은 mount ns는 안 바꿔서 **.pcap는 host cwd에 그대로 저장**(회수 OK). 패킷마다 μs 타임스탬프 내장. bridge DNAT는 dst만 바꾸고 **src(클라 IP) 보존** → bare 캡처와 동일 지문. ⚠️ 종료는 `sudo pkill -f "tcpdump.*port 8080"`(`$!`는 nsenter PID라 그것만으론 안 죽음).

- [ ] **Prometheus/Grafana**(로컬, 기존 그대로): `cd scripts && ./start-monitoring.sh <새박스 public IP>` → Grafana `localhost:3000`(admin/`loadtest`), Prometheus `localhost:9090` target UP.
- [ ] **앱 로그**: `docker logs triagain-loadtest > server-<TAG>.log 2>&1` 로 회수.
- [ ] 측정 후 회수: nstat/ss/server 로그 3종 → `results/<MMDD>/` + Prometheus 창 회수.

---

## Phase E — 측정 👤

> 절차·태깅·리셋은 **`CREW-RUSH-ABC-RUNBOOK.md` Phase 5 그대로**. 딱 하나만 바뀜:
> **`BASE_URL`을 새 박스 IP로**, 매 측정 직전 `psql "$TEST_DB" -f sql/07_rush_reset.sql`.

```bash
TAG=<전략>_max<정원>_vu<VU>
k6 run --env BASE_URL=http://<새박스IP>:8080 \
  --env TARGET_VUS=<VU> --env MAX_MEMBERS=<정원> \
  --env RUN_TAG=$TAG --out json=results/raw/crew-rush_$TAG.json \
  k6/crew-rush-jian.js
```
> ⚠️ **활성 스크립트는 `crew-rush-jian.js`** (pre-GC 게이트·RUN_TAG 가드·join_dropped 카운터 포함). `crew-rush.js`는 게이트 없는 구버전 — 시리즈 비교 불가.
> **pre-GC 게이트**: 기본 `PRE_GC=on` → 런 시작마다 `/internal/gc` 강제 호출(힙 균일화). GC 호출 없이 보려면 `--env PRE_GC=off` 추가(k6 stdout에 `[pre-GC] SKIPPED` 증빙).

- [ ] 락 비교(A/B/C)는 **드롭 아닌 완료요청 p95**로만(드롭은 전략무관 커널층 노이즈 — 기존 결론).
- [ ] 매 측정 `join_success == 정원`, `join_5xx == 0` 확인. DB 정합성(정원초과 0건)은 `sql/07_rush_verify.sql`.

---

## 옵션 실험 — 인스턴스 사이즈 스윕 (멘토 "코어 늘려라" 가설 검증)

> t3.micro는 **이미 2 vCPU**다. 기존 데이터상 병목은 CPU가 아니라 **TCP 수립 천장(드롭)** + **HikariCP acquire(=DB)**.
> 그래도 추측 대신 측정으로 닫는다.

- [ ] **baseline** = t3.micro(EC2) + db.t4g.micro(RDS, 07-12 실측 정정) (위 셋업 그대로) — 기존 시리즈와 비교 가능한 기준선
- [ ] **비교군** = 멘토 제안 사이즈로 **딴 건 전부 고정하고 인스턴스 타입만** 변경(t3.small/medium 등. 주의: 2 vCPU는 그대로, RAM만↑ → ergonomic 힙 256→512/1024MB로 바뀜)
- [ ] p95·처리량·드롭이 **실제로 개선되는가** 확인 → 개선되면 근거 데이터로 프로드 업그레이드 정당화, 아니면 프로드 무변경
- [ ] ⚠️ **변수 위생**: 사이즈 스윕은 **락 전략(A/B/C) 비교와 절대 섞지 말 것**(과거 클라넷·backlog 변수 혼입 오염 전례). 사이즈는 독립 변수로.
- [ ] (병행) 진짜 지렛대 후보는 **db.t4g.micro(버스트, 07-12 실측 정정)** — 노트에 "RDS 크레딧 미확인" 미해결로 남아있음. RDS 클래스 스윕도 별도 실험으로 가능.
- [ ] 멘토에게 확인: "t3.micro=이미 2 vCPU. 원하는 게 코어? RAM? 고정 CPU(버스트 회피)?"

---

## 정리 / 과금 👤

- [ ] 측정 종료: `docker rm -f triagain-loadtest`, `./scripts/stop-monitoring.sh`
- [ ] 새 박스 **stop**(과금 방지, t3.micro면 정지 시 거의 무료 — EIP 부착 시 소액)
- [ ] test RDS: 다음 세션까지 유지할지 판단(db.t4g.micro 최소요금). 장기 미사용이면 최종 스냅샷 후 삭제
- [ ] AMI(`triagain-loadtest-base`)·스냅샷 보관 여부 결정(재현용으로 두면 다음 셋업이 ②부터 시작)

---

## 담당 / 티어 / 주의

- **티어**: 이 문서 작성 = Tier 1(문서). **실제 인프라 생성·기동은 Tier 3**(신규 인스턴스/RDS) → 👤 사용자 실행·판단, 🤖는 지시서까지.
- ⚠️ **prod 배포 금지** — 전략/정원 변경은 부하테스트 한정.
- ⚠️ `load-test/` 는 커밋 안 된 개선분이 상존하는 폴더 → **git restore/checkout 금지**(미커밋 유실). 이 문서는 신규 파일이라 무관.
- 프로드 재현 근거 파일: `Dockerfile`, `.github/workflows/deploy.yml`, `application-prod.yml`, `application-loadtest.yml`, `application.yml`, `CREW-RUSH-ABC-RUNBOOK.md`.

---

## 참조한 실제 파일
- `triagain-back/Dockerfile`
- `triagain-back/docker-compose.yml`
- `triagain-back/.github/workflows/deploy.yml`
- `triagain-back/src/main/resources/application.yml`
- `triagain-back/src/main/resources/application-prod.yml`
- `triagain-back/src/main/resources/application-loadtest.yml`
- `triagain-back/load-test/CREW-RUSH-ABC-RUNBOOK.md`
- `triagain-back/load-test/` 디렉토리 구조(k6/·scripts/·sql/·monitoring/)
