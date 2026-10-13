# DB baseline B — 앱 컨테이너 기동·점검 런북 (DB Only)

지시서: 루트 `sdd/crew-join-db-baseline/instructions/C3-launch-procedure.md` (Tier 3, step0 종속).
대상: 👤 사용자. **이 문서의 명령은 하나도 실행되지 않았다** (에이전트 작성·정적 검사만).
근거 표시: `[실물]` 코드·설정·jar에서 문자열 확인 · `[문서]` 공식 문서 확인 · `[실물 과거]` 7월 런북 · `[추정]` · `[미확인]`.
단계 표시: `[사용자·AWS]` 테스트 EC2·AWS에서만 확인 가능 · `[로컬 확인 대상]` 루트 통합 단계에서 맥 로컬 기동으로 확인 가능 (확인되면 루트가 `[로컬 검증됨]`으로 바꾼다).
C8 수집기는 이 문서를 쓰는 동안 작성 중이었다. C8 스크립트 이름·환경변수는 `C8-collectors.md` §3 표에서 옮겼고, 인용마다 `[C8 산출물 대조 필요]`를 붙였다.

## 0. 변수

값은 문서에 쓰지 않는다. `'<…>'` 안을 실제 값으로 바꿔 각 기계의 셸에서 정한다.

```bash
# 테스트 EC2
export APP_CONTAINER=triagain-dbb                  # 운영 컨테이너 이름 triagain 금지
export IMAGE_TAG=triagain:dbb-5ab73a3              # C1 SHA 7자리. latest 금지
export ENV_FILE="$HOME/dbb-secrets/app.env"        # 저장소 밖, 권한 0600 (§2)
export FLYWAY_ENABLED=false                        # Q10: B(psql로 스키마)=false, A'·C(Flyway 이력 있음)=true
export SQL_DIR='<U1 E1로 복사한 load-test/sql 경로>'
export OUT_DIR='<run 폴더 — ~/dbb-results/YYYYMMDD/RUN_ID>'   # run-folder.sh [C8 산출물 대조 필요]
# PGHOST PGPORT PGUSER PGDATABASE + ~/.pgpass (test-plan §2)
```

맥 터미널이 zsh면 `#` 주석을 명령으로 읽지 않도록 아래 블록 첫 줄(`setopt interactivecomments`)을 먼저 실행한다. 이 문서의 맥 블록은 zsh·bash 둘 다에서 쓰도록 썼다.

```bash
[ -n "${ZSH_VERSION:-}" ] && setopt interactivecomments
# 맥
export APP_PUBLIC_IP='<앱 EC2의 현재 공인 IPv4>'
export APP_SSH_TARGET='<ssh 사용자>@<앱 EC2 공인 IPv4>'
export KEY='<SSH 키 파일 경로>'
export GC_HEADER_FILE="$HOME/dbb-secrets/gc-header"   # 비공개 내부 키 헤더 파일, 0600 (§2)
export TOKENS_FILE='<토큰 CSV 절대 경로>'
export OUT_DIR='<run 폴더 — load-test/results/YYYYMMDD/RUN_ID>'
export PROM_DATA_DIR='<Prometheus 데이터 폴더>'        # [C8 산출물 대조 필요]
```

## 1. 전제와 금지

- **테스트 EC2 전용.** 운영 서버·운영 RDS·운영 시크릿을 쓰지 않는다. 시크릿은 테스트용 새 값이다 (step0 §4-2).
- 이미지는 `triagain:dbb-5ab73a3` 하나만. `latest` 금지, 레지스트리 push 금지. 두 팔(DB Only·Redis Async)이 **같은 이미지 ID**를 쓴다.
- 이 박스는 운영 AMI 복제본이다. 운영 컨테이너는 `--name triagain --restart unless-stopped`로 뜬다 [실물 `.github/workflows/deploy.yml`]. 그래서 **박스를 켠 직후 먼저** 옛 컨테이너를 본다 `[사용자·AWS]`:

  ```bash
  # 테스트 EC2 (docker 권한 오류면 sudo docker)
  docker ps -a --format '{{.Names}}\t{{.Image}}\t{{.Status}}\t{{.RunningFor}}'
  ```
  기대: `triagain`이라는 이름이 없고, `devjian/triagain` 이미지 컨테이너가 없다. 있으면 멈추고 `U1` B절로 정리한다 (광범위 prune 금지).
- 보안그룹(22·8080 ← 집 공인 IP `/32`)이 **유일한 벽**이다. EC2의 공인 IPv4는 사설 주소로 매핑되므로 `-p` 사설 IP 바인드는 독립 방어가 아니다 (step0 §4-2). 8080 ← VPC 전체 대역 규칙은 사용자 결정으로 **유지**하며 알려진 잔여 노출이다 (같은 VPC의 운영 서버가 이 8080에 닿을 수 있다).
- 앱이 떠 있는 동안 `POST /auth/test-login`(인증 없음)과 `/internal/**`(내부 키)가 열린다. 그래서 앱은 측정할 때만 띄우고, 끝나면 §7대로 지운다.

## 2. 실제 설정에서 확정한 값

BE 경로는 랩 브랜치 `lab/dbb-c3-launch` = 기준 `5ab73a3` (C1). 근거 열의 문자열은 `grep -F`로 되짚을 수 있다.

| 확인 | 확정 결과 | 근거 (파일 · 고정 문자열) | 등급 |
|---|---|---|---|
| `prod,loadtest` 필수 환경변수 | **env-file 키 5개: `DB_URL` `DB_USERNAME` `DB_PASSWORD` `JWT_SECRET` `INTERNAL_API_KEY`**. 과거 4개(DB 3 + JWT)에 `INTERNAL_API_KEY`를 더한다. 기본값 없는 `${…}`는 prod에 6개이고, 그중 `APPLE_REFRESH_KEY`·`INTERNAL_API_KEY`는 loadtest가 리터럴로 덮어 **기동에는 4개면 된다**. `INTERNAL_API_KEY`는 기동이 아니라 **보안상 필수**(공개 값을 덮어쓰기 위해) | `application-prod.yml`: `url: ${DB_URL}` · `username: ${DB_USERNAME}` · `password: ${DB_PASSWORD}` · `secret: ${JWT_SECRET}` · `apple-refresh-key: ${APPLE_REFRESH_KEY}` · `api-key: ${INTERNAL_API_KEY}` / `application-loadtest.yml`: `apple-refresh-key: dGVz`(리터럴, 값 생략) · `api-key: loadtest-internal-key` · `access-token-expiration: 86400000` | [실물] |
| 넣지 않는 환경변수 | `APPLE_REFRESH_KEY`(loadtest가 덮음), `FIREBASE_ENABLED`(기본 false → FCM 빈 미로드), `FIREBASE_SERVICE_ACCOUNT_PATH`(기본값 있음, `FirebaseConfig`는 `firebase.enabled=true`일 때만), `CREW_FIRST_VERIFICATION_ENABLED`(기본 true), `APPLE_*`(기본값 있음), `SPRING_PROFILES_ACTIVE`(실행 인자가 이긴다) | `application-prod.yml`: `FIREBASE_ENABLED:false` · `CREW_FIRST_VERIFICATION_ENABLED:true` / `FirebaseConfig.java`: `@ConditionalOnProperty(name = "firebase.enabled", havingValue = "true")` / `application.yml`: `APPLE_CLIENT_ID:com.triagain.triagain` | [실물] |
| 코드의 `@Value`가 더 요구하는가 | 아니다. `src/main/java`의 `@Value` 17곳은 기본값이 있거나 위 yml 키로 채워진다 (`aws.s3.bucket`·`aws.s3.region`은 prod 리터럴) | `S3StorageAdapter.java`: `@Value("${aws.s3.bucket}")` / `application-prod.yml`: `bucket: triagain-verifications` | [실물] |
| `JWT_SECRET` 형식 | Base64 문자열, 디코드 32바이트 이상(HS256). 예: `openssl rand -base64 32` | `JwtProvider.java`: `Keys.hmacShaKeyFor(Base64.getDecoder().decode(secret))` | [실물] · 32바이트 하한은 [추정: jjwt HS256 규칙] |
| `INTERNAL_API_KEY`가 jar 안 yml을 덮는가 | **덮는다.** 우선순위: OS 환경변수(5) > config data(3). 이름: Boot는 환경변수 `INTERNAL_API_KEY`를 `-`→`_` 규칙으로 `internal.api-key`에 연결한다. 문서의 표준형은 `INTERNAL_APIKEY`(대시 제거)이고 `INTERNAL_API_KEY`는 jar의 legacy 매핑으로 연결된다. 운영도 같은 이름을 쓴다. **최종 확인은 스모크**: 공개 값 403 · 비공개 값 200 (§5-4). 만약 공개 값이 통과하면 G10 실패 → 중단 (그때만 `INTERNAL_APIKEY`를 같은 값으로 추가하는 안을 사용자와 검토) | [문서] Spring Boot 3.4 external-config "5. OS environment variables" · "3. Config data" · "Remove any dashes" / [실물] `spring-boot-3.4.13.jar` `SystemEnvironmentPropertyMapper.convertLegacyNameElement` (`String.replace('-', '_')`) / `SecurityConfig.java`: `@Value("${internal.api-key}")` · `new InternalApiKeyFilter(internalApiKey)` / `deploy.yml`: `-e "INTERNAL_API_KEY=$INTERNAL_API_KEY"` | [문서]+[실물] · 실제 거절은 [로컬 확인 대상] |
| `InternalApiKeyFilter` 거절 응답 | 경로가 `/internal/`로 시작하고 헤더 `X-Internal-Api-Key`가 없거나 다르면 **HTTP 403**, `Content-Type: application/json`, 본문 `{"success":false,"data":null,"error":{"code":"FORBIDDEN","message":"Invalid internal API key"}}`. 비교는 `MessageDigest.isEqual`. `/internal/**`는 Security에서 permitAll이라 이 필터가 유일한 앱 레벨 검사 | `InternalApiKeyFilter.java`: `SC_FORBIDDEN` · `Invalid internal API key` · `startsWith("/internal/")` · `X-Internal-Api-Key` / `SecurityConfig.java`: `.requestMatchers("/internal/**").permitAll()` · `@Profile("prod")` | [실물] |
| 열리는 테스트 경로 | `POST /auth/test-login`(인증 없음), `POST /internal/gc`, `POST /internal/scheduler/{name}` (`loadtest` 프로필) | `TestLoginController.java`: `@Profile({"!prod", "loadtest"})` / `GcTriggerController.java`·`SchedulerTriggerController.java`: `@Profile("loadtest")` | [실물] |
| 이미지 기본 프로파일 | 이미지는 `prod`로 굳어 있고, 실행 인자 `--spring.profiles.active=prod,loadtest`가 이긴다 (명령줄 11 > OS 환경변수 5). 여러 프로필은 뒤가 이긴다 → loadtest가 prod 값을 덮는다 | `Dockerfile`: `ENV SPRING_PROFILES_ACTIVE=prod` · `ENTRYPOINT ["java", "-jar", "app.jar"]` / `application.yml`: `active: local` / [문서] external-config "11. Command line arguments" · "a last-wins strategy applies" | [실물]+[문서] |
| 리슨 포트 · 헬스 | 8080 (`server.port` 설정 없음). `/actuator/health`·`/actuator/prometheus`는 permitAll. Boot 기본 Redis health를 꺼서 DB 전략은 Redis 없이 `UP` (주석의 "실측", 이번엔 미실행) | `Dockerfile`: `EXPOSE 8080` / `SecurityConfig.java`: `"/actuator/health", "/actuator/prometheus"` / `application.yml`: `enabled: false` + 주석 `503(실측)` | [실물] · `UP`은 [로컬 확인 대상] |
| 노출 엔드포인트 | jar 기본은 `health,prometheus,metrics` → 실행 인자 `--management.endpoints.web.exposure.include=health,prometheus`로 `metrics`를 뺀다 (G1) | `application.yml`: `include: health,prometheus,metrics` | [실물] |
| 적용된 락 전략 확인 방법 | **로그로 확인할 수 없다** — 전략을 찍는 로그가 없다. `docker inspect`로 실행 인자를 본다 (§4-2). 인자가 빠지면 기본값 `CONDITIONAL`이다 (과거 런북의 "기본 PESSIMISTIC"은 지금 틀리다). REDIS_ASYNC일 때만 `[CrewJoinRedis] startup PING ok`가 찍히므로 CONDITIONAL에서 이 줄이 0개인 것을 보조 확인으로 쓴다 | `application.yml`: `lock-strategy: CONDITIONAL` / `CrewLockProperties.java`: `private LockStrategy lockStrategy;` / grep `log\.` × `strategy` 0건 (대조군 `[GcTrigger] 완료` 1건) / `CrewJoinRedisStartupCheck.java`: `startup PING ok` | [실물] |
| Flyway | (A) `FLYWAY_ENABLED=true`: Q10 A'(스키마+`flyway_schema_history` 복제)·C(기존 DB). jar 이력 V1~V26과 DB 이력이 맞아야 한다. (B) `FLYWAY_ENABLED=false`: Q10 B(psql로 V1~V26 적용, 이력 테이블 없음) — **켜 두면 `baseline-on-migrate`가 이력 없는 비어 있지 않은 스키마를 버전 6으로 baseline하고 V7부터 다시 적용하려 한다**. 두 갈래 모두 prod `ddl-auto: validate`가 엔티티와 테이블·컬럼을 검증하므로 **기동 성공 = 엔티티 매핑과 스키마 일치**. 인덱스·유니크 제약까지 보는지는 확인하지 않았다 → V22 유니크 인덱스는 C7 검증에 맡긴다. 7월에는 DB 이력이 jar보다 앞서 `ignore-migration-patterns=*:missing,*:future`가 필요했다 — 이번에는 기본으로 넣지 않고, 같은 오류가 나면 멈추고 사용자 판단 | `build.gradle`: `flyway-core` / `application.yml`: `baseline-on-migrate: true` · `baseline-version: 6` / `application-prod.yml`: `ddl-auto: validate` / `db/migration` 26개 (`V26__add_reaction_constraints.sql`) / 루트 `instructions/C7-sql-seeds.md` "`flyway_schema_history`는 만들어지지 않으므로 이 방식의 앱은 `--spring.flyway.enabled=false`로 기동해야 한다" | [실물] · baseline 동작은 [문서: Flyway baselineOnMigrate, 이번 세션 미조회] · V7 실패 양상은 [추정] |
| 로그 레벨 · 확인용 로그 문자열 | prod는 `root: WARN`, `com.triagain: INFO`. 기동 로그의 `profiles are active:`·`using Java`는 메인 클래스(`com.triagain.TriAgainApplication`) 로거라 보일 것이다 | `application-prod.yml`: `root: WARN` · `com.triagain: INFO` / `spring-boot-3.4.13.jar` `SpringApplication` 상수 `profiles are active:` · `StartupInfoLogger` 상수 `using Java` | [실물] · 실제로 찍히는지는 [로컬 확인 대상] |
| 넣지 않는 실행 옵션 | `-Xmx` · `--memory` · `--network host` · `--log-driver=awslogs` · `--server.tomcat.accept-count` · GC 옵션 · 설정 볼륨 마운트. 운영처럼 힙·GC는 ergonomic | `Dockerfile` ENTRYPOINT에 힙 플래그 없음 / 과거 런북 Phase C ⛔ 목록 / `deploy.yml`: `--log-driver=awslogs` / step0 §2 accept-count 미지정 | [실물]+[실물 과거] |
| AWS 자격 | 이 박스에 AWS 자격·IAM 역할이 없어도 기동한다고 본다 — `S3Presigner`는 빌드 때 자격을 읽지 않고, 7월에도 DB·JWT 환경변수만으로 떴다 | `S3Config.java`: `S3Presigner.builder()` / 과거 런북 Phase A② "role 없음" · Phase C② `-e` 4개 | [추정]+[실물 과거] · [로컬 확인 대상] |

`docker inspect`의 `.Config.Env`에는 env-file 값이 **평문으로** 보인다. run card·채팅에는 `.Args`만 옮기고 `docker inspect` 전체 출력을 붙이지 않는다.

## 3. env-file · 내부 키 준비 (최초 1회)

키 이름만 쓴다. 값은 화면·히스토리·채팅에 남기지 않는다.

```bash
# 맥 — 비공개 내부 키를 만들어 맥 헤더 파일과 EC2 env-file에 같은 값으로 넣는다 (값은 출력하지 않는다, 한 번만)
# 원격 경로 $HOME/dbb-secrets/app.env = EC2의 ENV_FILE(§0). 바꿨다면 여기도 같이 바꾼다
umask 077 && mkdir -p "$HOME/dbb-secrets"
KEYVAL=$(openssl rand -hex 32)
printf 'X-Internal-Api-Key: %s\n' "$KEYVAL" > "$GC_HEADER_FILE"
ssh -i "$KEY" "$APP_SSH_TARGET" 'umask 077; mkdir -p "$HOME/dbb-secrets"; cat >> "$HOME/dbb-secrets/app.env"' \
  <<< "INTERNAL_API_KEY=$KEYVAL"
unset KEYVAL
cut -d: -f1 "$GC_HEADER_FILE"        # 기대: X-Internal-Api-Key
```

```bash
# 테스트 EC2 — 나머지 4개. JWT_SECRET은 새로 만들고, DB 3개는 편집기로 넣는다 (따옴표 없이 KEY=VALUE)
umask 077
printf 'JWT_SECRET=%s\n' "$(openssl rand -base64 32)" >> "$ENV_FILE"
"${EDITOR:-vi}" "$ENV_FILE"          # DB_URL=jdbc:postgresql://<테스트 RDS 엔드포인트>:5432/<새 database>, DB_USERNAME, DB_PASSWORD
chmod 600 "$ENV_FILE"
cut -d= -f1 "$ENV_FILE" | sort       # 기대: 정확히 5줄 — DB_PASSWORD DB_URL DB_USERNAME INTERNAL_API_KEY JWT_SECRET
find "$ENV_FILE" -perm 0600          # 기대: 경로 1줄 (0600)
```

- env-file 값에 따옴표를 붙이지 않는다 [미확인: Docker env-file은 따옴표를 값의 일부로 넘기는 것으로 안다 — 로컬 확인 대상].
- Redis 인증을 쓰게 되면 비밀번호는 같은 파일의 `SPRING_DATA_REDIS_PASSWORD`로 넣는다 (인자 금지, §8).
- `openssl` 존재는 [미확인] `[사용자·AWS]`.

## 4. 기동 (DB Only)

### 4-1. 기동 명령 — run마다 새 컨테이너 `[사용자·AWS]` `[로컬 확인 대상]`

```bash
# 테스트 EC2 — 한 블록 통째로 붙여 넣는다. 앞 검사가 하나라도 실패하면 docker run은 실행되지 않는다
: "${APP_CONTAINER:?}" "${ENV_FILE:?}" "${IMAGE_TAG:?}" "${FLYWAY_ENABLED:?}" \
&& [ "$APP_CONTAINER" != triagain ] \
&& case "$IMAGE_TAG" in triagain:dbb-[0-9a-f][0-9a-f][0-9a-f][0-9a-f][0-9a-f][0-9a-f][0-9a-f]) ;; *) false ;; esac \
&& case "$FLYWAY_ENABLED" in true|false) ;; *) false ;; esac \
&& [ -n "$(find "$ENV_FILE" -perm 0600)" ] \
&& docker run -d --name "$APP_CONTAINER" --restart=no \
  -p 8080:8080 \
  --env-file "$ENV_FILE" \
  "$IMAGE_TAG" \
  --spring.profiles.active=prod,loadtest \
  --triagain.crew.lock-strategy=CONDITIONAL \
  --management.endpoints.web.exposure.include=health,prometheus \
  --spring.flyway.enabled="$FLYWAY_ENABLED"
```

- `--restart=no`를 **명시**한다: 박스 재부팅·인스턴스 stop/start 뒤 컨테이너가 저절로 다시 떠서 DB에 붙지 않게 한다 (운영 AMI에는 `unless-stopped` 컨테이너가 박혀 있었다 [실물 과거 Phase A③]).
- 프로파일·전략·노출·Flyway는 **exec 형식 ENTRYPOINT 뒤에 붙는 프로그램 인자**다. 비밀값은 env-file로만 넘긴다 (명령줄·`ps`·히스토리에 남지 않게).
- G9(요청당 SQL)만: `--spring.flyway.enabled=…` 줄 끝에 ` \`를 붙이고 다음 줄에 `--logging.level.org.hibernate.SQL=DEBUG`를 더해 **잠시** 띄운 뒤, 본 측정 전에 이 블록 그대로 다시 띄운다 (`U1` G절 6번).
- 이미지 ID를 run card에 적는다: `docker image inspect -f '{{.Id}}' "$IMAGE_TAG"`.

### 4-2. 기동 직후 점검 (test-plan §5 run 3번)

```bash
# 테스트 EC2 — 헬스 UP까지 최대 약 2분 기다린다
for i in $(seq 1 60); do curl -fsS -m 2 http://localhost:8080/actuator/health && echo && break; sleep 2; done
docker inspect -f '{{.State.Status}} restarts={{.RestartCount}} policy={{.HostConfig.RestartPolicy.Name}} image={{.Image}}' "$APP_CONTAINER"
docker logs "$APP_CONTAINER" 2>&1 | grep -m1 'profiles are active'
docker logs "$APP_CONTAINER" 2>&1 | grep -m1 'using Java'
docker inspect -f '{{range .Args}}{{println .}}{{end}}' "$APP_CONTAINER"
docker logs "$APP_CONTAINER" 2>&1 | grep -c '\[CrewJoinRedis\] startup PING ok'
```

| # | 확인 | 기대 | 표시 |
|---|---|---|---|
| 1 | 헬스 (EC2 `localhost`, 맥은 `curl -fsS "http://$APP_PUBLIC_IP:8080/actuator/health"`) | `{"status":"UP"}`. 루프가 끝나도 없으면 `docker ps -a`에서 `Exited`인지 보고 `docker logs --tail 80 "$APP_CONTAINER"` (Flyway·`validate` 오류가 흔한 원인) | `[사용자·AWS]` `[로컬 확인 대상]` |
| 2 | 상태 | `running restarts=0 policy=no image=sha256:…` — `image`가 `docker image inspect` 결과와 같다 | `[로컬 확인 대상]` |
| 3 | 활성 프로파일 | `profiles are active:` 줄에 `prod`와 `loadtest` 둘 다 (따옴표 표기 형식은 [미확인]) | `[로컬 확인 대상]` |
| 4 | 앱 JVM 버전 | `using Java 17.` 이 들어간 줄 1개 → `jvm-state.txt`(§4-3) | `[로컬 확인 대상]` |
| 5 | 적용 전략 · 인자 | 출력에 `--spring.profiles.active=prod,loadtest`, `--triagain.crew.lock-strategy=CONDITIONAL`, `--management.endpoints.web.exposure.include=health,prometheus`, `--spring.flyway.enabled=<정한 값>`이 각 1줄 (앞의 `-jar`·`app.jar`는 ENTRYPOINT) | `[로컬 확인 대상]` |
| 6 | Redis 모드가 아님 (보조) | `0` | `[로컬 확인 대상]` |

```bash
# 테스트 EC2 — 기동 보정 러너 3종·스케줄러가 dbb- 크루를 건드리지 않았는지 (step0 §3-8)
{ echo "-- after app start $(date -u +%s%3N)"; psql -X -v ON_ERROR_STOP=1 -f "$SQL_DIR/24_b_baseline.sql"; } >> "$OUT_DIR/sql-before.txt"
```
기대: 앱 기동 전 블록과 비교해 `now` 줄만 다르다 — `scheduler_candidates` 0, `dbb-` 크루 `status`가 `RECRUITING` 그대로, `current_members`·`member_rows`가 그대로. 다르면 본 측정을 시작하지 않는다. (파일 이름을 새로 만들지 않고 `sql-before.txt` 뒤에 붙인다 [제안].) `[사용자·AWS]`

```bash
# 맥 — /actuator/prometheus 200, metrics 미노출, G1 지표 이름 (결과는 gate-g1.txt)
curl -sS -o /dev/null -w '%{http_code}\n' "http://$APP_PUBLIC_IP:8080/actuator/prometheus"
curl -sS -H @<(printf 'Authorization: Bearer %s\n' "$(sed -n 2p "$TOKENS_FILE")") \
  "http://$APP_PUBLIC_IP:8080/actuator" | jq -r '._links | keys[]'
P=$(curl -sS "http://$APP_PUBLIC_IP:8080/actuator/prometheus")
for m in tomcat_threads_busy_threads tomcat_threads_current_threads tomcat_threads_config_max_threads \
  hikaricp_connections_active hikaricp_connections_idle hikaricp_connections_pending hikaricp_connections_max \
  hikaricp_connections_acquire_seconds_bucket http_server_requests_seconds_bucket \
  jvm_gc_pause_seconds_count jvm_gc_pause_seconds_sum jvm_memory_used_bytes jvm_memory_max_bytes \
  process_cpu_usage system_cpu_usage jvm_gc_memory_allocated_bytes_total; do
  printf '%s %s\n' "$m" "$(printf '%s\n' "$P" | grep -c "^${m}[{ ]")"
done | tee -a "$OUT_DIR/gate-g1.txt"
```
기대: 첫 줄 `200`. 둘째 명령의 키에 `health`·`prometheus`가 있고 **`metrics`가 없다** (토큰이 아직 없으면 생략하고 §4-2 5번의 인자 확인으로 대신한다. 토큰 없이 부르면 노출 여부와 무관하게 401이라 판별이 안 된다 [실물 `SecurityConfig` `anyRequest().authenticated()`]). 지표는 `jvm_gc_memory_allocated_bytes_total`·`jvm_gc_pause_*`를 뺀 모두 1 이상 (`http_server_requests_seconds_bucket`이 0이면 한 번 더 조회 — scrape 요청 자체가 첫 표본이다 [추정]). `jvm_gc_pause_*`는 GC가 한 번도 없으면 0일 수 있다 [추정]. 이름은 G1 통과 전 [미확인]. `[로컬 확인 대상]`

### 4-3. 실행 중 앱 JVM 상태 → `jvm-state.txt` (test-plan §5 run 3번)

별도 `java -version` 프로세스의 ergonomic 값으로 대신하지 않는다. 실행 중인 앱의 기동 로그와 노출 지표만 쓴다.

```bash
# 맥
J="$OUT_DIR/jvm-state.txt"
{
  echo "## boot $(python3 -c 'import time;print(int(time.time()*1000))')"
  ssh -i "$KEY" "$APP_SSH_TARGET" "docker logs '$APP_CONTAINER' 2>&1 | grep -m1 'using Java'"
  curl -sS -m 5 "http://$APP_PUBLIC_IP:8080/actuator/prometheus" \
    | grep -E '^(jvm_memory_max_bytes|jvm_info|process_start_time_seconds|process_uptime_seconds)[{ ]'
} >> "$J"
```
기대: `using Java 17.` 줄, `jvm_memory_max_bytes{…area="heap",id="…"}` 줄들. **힙 최대** = `area="heap"` 값의 합(값 `-1`은 "정의 없음"이라 뺀다). **GC 종류**는 `id` 이름으로 본다 — Serial이면 `Eden Space`·`Tenured Gen`, G1이면 `G1 Eden Space`·`G1 Old Gen` [추정, G1 게이트에서 확인]. `jvm_info`는 없을 수 있다 [미확인]. `[로컬 확인 대상]`

## 5. 노출 방지 확인 — G10 (step0 §4-3의 5항목, 결과는 `gate-g10.txt`)

| # | 어디서 | 명령 | 기대 | 표시 |
|---|---|---|---|---|
| 1 | 사용자 PC (콘솔 또는 CLI) | `aws ec2 describe-security-groups --group-ids "$APP_SG_ID" "$RDS_SG_ID" --query 'SecurityGroups[].{id:GroupId,in:IpPermissions}'` | 앱: 22·8080 ← 집 `/32`와 유지 결정한 VPC 규칙뿐, `0.0.0.0/0` 없음. RDS: 5432 ← 앱 EC2 보안그룹만. (Redis는 5-B, `U1` J6) | `[사용자·AWS]` |
| 2 | 테스트 EC2 | `sudo ss -lntp 'sport = :8080'` | `docker-proxy`가 8080을 연다 — 기록만 (진짜 Tomcat 큐는 컨테이너 netns 안 [실물 과거 Phase D]) | `[사용자·AWS]` |
| 3 | **다른 회선**(폰 핫스팟에 붙인 맥 등) / 맥(집) | §5-3 블록 | 앱 8080·RDS 5432 모두 **타임아웃 또는 거부**. HTTP 응답이 오면 즉시 중단 | `[사용자·AWS]` |
| 4 | 맥(집) | §5-4 블록 ①② | 둘 다 **HTTP 403** + 본문 `{"success":false,"data":null,"error":{"code":"FORBIDDEN","message":"Invalid internal API key"}}`. ②가 200이면 키 덮어쓰기 실패 → **G10 실패, 중단** (그 호출로 GC가 한 번 일어났음도 기록) | `[사용자·AWS]` `[로컬 확인 대상]` |
| 5 | 맥(집) | `curl -sS -o /dev/null -w '%{http_code}\n' "http://$APP_PUBLIC_IP:8080/actuator/prometheus"` | `200` (공인 접근은 집 `/32`로만 제한, VPC 잔여 노출은 1번에 기록) | `[사용자·AWS]` `[로컬 확인 대상]` |

### 5-3. 다른 회선 · RDS 직접 접속

```bash
# 다른 회선에서 — 앱 8080 (기대: exit=28 타임아웃 또는 exit=7 거부, HTTP 코드가 찍히면 실패)
curl -sS -m 5 -o /dev/null -w '%{http_code}\n' "http://$APP_PUBLIC_IP:8080/actuator/health"; echo "exit=$?"
# 맥(집)에서 — RDS 5432 (기대: 타임아웃 또는 거부)
nc -vz -G 3 "$RDS_HOST" 5432; echo "exit=$?"
```

### 5-4. `/internal/gc` 세 경우

```bash
# 맥 — ① 키 없음 ② 공개 값 (둘 다 403이어야 한다)
curl -sS -m 10 -X POST -w '\nHTTP %{http_code}\n' "http://$APP_PUBLIC_IP:8080/internal/gc"
curl -sS -m 10 -X POST -w '\nHTTP %{http_code}\n' -H 'X-Internal-Api-Key: loadtest-internal-key' \
  "http://$APP_PUBLIC_IP:8080/internal/gc"
```

③ **비공개 키**는 실제 GC를 일으킨다. 스모크 G3와 매 run의 **워밍업 직후 강제 GC 단계**(§6)에서만 쓰고, 본 측정 도중에는 부르지 않는다. 기대: `HTTP 200`, 본문 `data` 아래 `heapUsedBeforeMb`·`heapUsedAfterMb`·`durationMs` [실물 `GcTriggerController` `record GcTriggerResponse(long heapUsedBeforeMb, long heapUsedAfterMb, long durationMs)` · `ApiResponse.ok`]. `/internal/scheduler/*`도 같은 필터 뒤에 열려 있으나 이 실험에서는 부르지 않는다.

## 6. 강제 GC · 상태 확인 (test-plan §5 run 5·6번)

```bash
# 맥 — 워밍업 k6가 끝난 뒤. 키는 헤더 파일에서 읽어 명령줄·히스토리에 남지 않는다
J="$OUT_DIR/jvm-state.txt"
now_ms() { python3 -c 'import time;print(int(time.time()*1000))'; }
snap() {
  curl -sS -m 5 "http://$APP_PUBLIC_IP:8080/actuator/prometheus" \
    | grep -E '^(jvm_gc_pause_seconds_count|jvm_memory_used_bytes|jvm_memory_max_bytes|process_uptime_seconds)[{ ]' \
    | grep -v 'area="nonheap"'
}
{ echo "## before-gc $(now_ms)"; snap; } >> "$J"
{ echo "## gc-call $(now_ms)"
  curl --max-time 30 -sS -X POST -H @"$GC_HEADER_FILE" -w '\nHTTP %{http_code}\n' \
    "http://$APP_PUBLIC_IP:8080/internal/gc"; } >> "$J"
sleep 5
{ echo "## after-gc $(now_ms)"; snap; } >> "$J"
grep -E '^HTTP |heapUsedBeforeMb' "$J" | tail -2
awk '$1=="##"{s=$2} /^jvm_gc_pause_seconds_count/{c[s]+=$NF} END{print "gc_count before=" c["before-gc"]+0, "after=" c["after-gc"]+0}' "$J"
```

기대: `HTTP 200`, 응답 줄에 `heapUsedBeforeMb`·`heapUsedAfterMb`·`durationMs`, `gc_count`가 before < after. Grafana에서 Hikari pending·Tomcat busy가 0 근처. **셋 중 하나라도 아니면 본 측정을 시작하지 않고 사용자에게 알린다** (GC 완료를 가정하지 않는다, test-plan §5 6번). 지표 이름은 G1 전 [미확인]. `-H @파일`은 curl 7.55 이상 [미확인: 맥 curl 버전]. 키 값이 `jvm-state.txt`에 들어가지 않는다 (요청 헤더는 `-v` 없이는 출력되지 않는다). `[로컬 확인 대상]`

## 7. 종료 — 로그 덤프 → 제거 → 재확인 (test-plan §5 run 11·13번)

```bash
# 테스트 EC2 — 반드시 docker rm 앞에서 덤프한다 (json-file 로그는 컨테이너와 함께 사라진다)
docker logs "$APP_CONTAINER" > "$OUT_DIR/app.log" 2>&1
test -s "$OUT_DIR/app.log" && echo "app.log OK"
grep -cE 'eyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}' "$OUT_DIR/app.log"   # 기대 0 (토큰이 로그에 없다)
docker rm -f "$APP_CONTAINER"
docker ps -a --filter "name=^${APP_CONTAINER}\$" --format '{{.Names}}'  # 기대: 빈 출력
sudo ss -lnt 'sport = :8080'                                            # 기대: 헤더 한 줄만
```

```bash
# 맥 — test-login과 /internal이 사라졌는지 (기대: 두 줄 모두 exit=7 거부 또는 exit=28 타임아웃, HTTP 코드가 찍히면 실패)
curl -sS -m 5 -o /dev/null -w '%{http_code}\n' -X POST -H 'Content-Type: application/json' \
  -d '{"userId":"loadtest-leader"}' "http://$APP_PUBLIC_IP:8080/auth/test-login"; echo "exit=$?"
curl -sS -m 5 -o /dev/null -w '%{http_code}\n' -X POST "http://$APP_PUBLIC_IP:8080/internal/gc"; echo "exit=$?"
```

`[사용자·AWS]` `[로컬 확인 대상]` · 토큰 패턴이 1 이상이면 그 로그를 run 폴더에 두지 말고 사용자 판단 (measurement-contract §4). 마지막 세션의 정리(보안그룹 `/32` 제거, 시크릿 폐기, 이미지 삭제)는 step0 §4-4.

## 8. Redis 팔 (자리표시자 — Redis 설치 후 지시서에서 확정)

같은 이미지·같은 env-file·같은 블록에서 `--triagain.crew.lock-strategy=CONDITIONAL` **한 줄만** 아래 5줄로 바꾼다 (`U1` J6과 같은 내용).

```text
  --triagain.crew.lock-strategy=REDIS_ASYNC \
  --spring.data.redis.host="$REDIS_HOST" \
  --spring.data.redis.port=6379 \
  --triagain.crew.redis.namespace=dbb \
  --triagain.crew.redis.run-id="$RUN_ID" \
```

- 인증을 쓰면 비밀번호는 env-file의 `SPRING_DATA_REDIS_PASSWORD` (인자 금지).
- 인자를 빼면 기동이 실패한다: `application-prod.yml`에 Redis 주소가 없어 localhost로 PING하다 실패한다 [S1 §B-1]. `run-id`를 빼면 `triagain.crew.redis.run-id must match` [실물 `CrewJoinRedisProperties` `requireKeyPart`].
- 기동 성공 판정 로그: `[CrewJoinRedis] startup PING ok (namespace=dbb, runId=…)` 1줄과 `CREW_JOIN_RECOVERY_SUCCEEDED` 1줄, `CREW_JOIN_RECOVERY_FAILED`·`CREW_JOIN_WORKER_STOPPED` 0줄 [실물 `CrewJoinRedisStartupCheck` · `CrewJoinPendingWorker`]. REDIS_ASYNC에서는 Redis health indicator가 다시 등록된다 [실물 `application.yml` 주석 `CrewJoinRedisConfiguration이 같은 이름의 indicator를 직접 등록한다`].
- R-G4 절차는 `U1` J6이 정본이다.

## 9. 맥 모니터링 기동 · 확인 (세션 단위)

C8 스크립트를 **호출 순서와 기대 결과만** 적는다. 스크립트·환경변수 이름은 전부 `[C8 산출물 대조 필요]`.
**컨테이너 안의 `localhost`는 그 컨테이너 자신이다.** 맥 Prometheus 컨테이너는 맥에 열린 SSH 터널을 `host.docker.internal:9100`으로 긁는다 (`NODE_TARGETS` 기본값 [C8 산출물 대조 필요]).

| 순서 | 할 일 | 기대 | 표시 |
|---|---|---|---|
| ① | (I5, ◐) node_exporter 터널 열기 — 별도 터미널, 세션 동안 유지. 설치는 `U1` K1 | 다른 터미널에서 `curl -s http://127.0.0.1:9100/metrics \| grep -c '^node_'` > 0. 실패해도 진행은 막지 않고 run card에 사유 | `[사용자·AWS]` |
| ② | `prometheus-up.sh` (`APP_PUBLIC_IP` `PROM_DATA_DIR`) [C8 산출물 대조 필요] | 오류 없이 끝나고 `docker port`에서 9090·3000이 `127.0.0.1`에만 바인드 | `[사용자·AWS]` `[로컬 확인 대상]` |
| ③ | 타깃 확인 (G2) | job `triagain`: `health=up`, `lastError` 빈 값, `lastScrapeDuration` < 1(초). job `node`는 ◐ (down이어도 차단 안 함, 알림) | `[사용자·AWS]` `[로컬 확인 대상]` |
| ④ | Grafana `http://127.0.0.1:3000` → 대시보드 `TriAgain Load Test` (G3) | `JVM Heap Memory`·`HikariCP Connection Pool`·`Tomcat Threads (HTTP workers)` 패널과 Host 행 [C8 산출물 대조 필요]에 선이 보인다. 부하가 0이면 HTTP 패널이 빈 것은 정상 | `[사용자·AWS]` `[로컬 확인 대상]` |
| ⑤ | run마다 `prom-export.sh` (`START_MS` `END_MS` `OUT_DIR`) [C8 산출물 대조 필요] | `END_MS`는 수집기 종료(폴러 `END` 줄) **뒤 마지막 scrape까지** — k6 종료 시각으로 자르지 않는다 (C8 §3-5). `START_MS`는 test-plan §5 run 7번 수집기 시작 직전의 맥 시각 [제안]. `prom-*.json`이 비어 있지 않다 | `[사용자·AWS]` `[로컬 확인 대상]` |
| ⑥ | 세션 끝: ⑤ 덤프 보관 확인 → `prometheus-down.sh` [C8 산출물 대조 필요] → 터널 터미널에서 Ctrl-C | 모니터링 컨테이너 0개, `PROM_DATA_DIR`은 남는다 | `[로컬 확인 대상]` |

```bash
# ① 맥, 별도 터미널 (U1 K2와 같다)
ssh -i "$KEY" -N -L 127.0.0.1:9100:127.0.0.1:9100 "$APP_SSH_TARGET"
```

```bash
# ② · ③ · ⑤ 맥 (저장소 루트 = BE 랩 체크아웃에서)
APP_PUBLIC_IP="$APP_PUBLIC_IP" PROM_DATA_DIR="$PROM_DATA_DIR" load-test/collectors/prometheus-up.sh
curl -s http://127.0.0.1:9090/api/v1/targets \
  | jq -r '.data.activeTargets[] | [.labels.job, .labels.instance, .health, .lastScrapeDuration, .lastError] | @tsv'
END_MS=$(python3 -c 'import time;print(int(time.time()*1000))')   # 수집기 종료 뒤 2초 이상 지나서
START_MS="$START_MS" END_MS="$END_MS" OUT_DIR="$OUT_DIR" load-test/collectors/prom-export.sh
```

## 10. test-plan §5와의 연결 (순서의 정본은 test-plan §5 — 여기서는 어느 절을 쓰는지만)

| test-plan §5 | 이 문서 / 다른 산출물 |
|---|---|
| 세션 시작 4번 (모니터링) | §9 ①~④ |
| run 1·2번 (run 폴더, 크루 2개, VACUUM, 기준선) | `run-folder.sh` [C8 산출물 대조 필요] · C7 `21`(본 측정) → `21`(`dbb-warm-NN`, `-v max_members=100`) → `23` → `24 > sql-before.txt`. `00_truncate.sql`은 시드 뒤 실행 금지 (`loadtest-%`를 지운다) |
| run 3번 (새 컨테이너, 헬스·전략, 기준선 재확인, JVM 상태) | §4-1 → §4-2 → §4-3 |
| run 4번 (워밍업) | C5 `load-test/k6/db-baseline-b.js` 머리 주석 — `WARMUP=1`, 워밍업 크루, **`--out json=$OUT_DIR/warmup-k6.json`을 붙인다** (`OUT_DIR` 폴더가 없으면 요청 전에 실패) |
| run 5·6번 (강제 GC, 상태 확인) | §6 |
| run 7·8번 (수집기, 본 측정) | `box-app-start.sh` [C8 산출물 대조 필요] · C5 본 측정 (`WARMUP=0`, `--out json=$OUT_DIR/k6.json`) |
| run 10번 (정합성) | C7 `22 > sql-after.txt` (`-v crew_id=… -v k6_201=…`) |
| run 11번 (export, 앱 로그) | §9 ⑤ · §7 첫 블록 |
| run 13번 (컨테이너 제거) | §7 |
| 스모크 G1·G2·G3·G10 | §4-2 · §9 ③ · §9 ④ + §6 · §5 |

## 참조한 실제 파일

BE 파일은 worktree `.worktrees/dbb-c3` (`lab/dbb-c3-launch`, `5ab73a3`)에서 읽었다.

- `Dockerfile`, `.dockerignore`, `build.gradle`, `.github/workflows/deploy.yml` (`docker run` 블록)
- `src/main/resources/application.yml`, `application-prod.yml`, `application-loadtest.yml`, `db/migration/` (V1~V26 파일 목록)
- `src/main/java/com/triagain/common/auth/SecurityConfig.java`, `InternalApiKeyFilter.java`, `JwtAuthenticationFilter.java`, `JwtProvider.java`, `AuthEntryPoint.java`
- `src/main/java/com/triagain/common/api/GcTriggerController.java`, `SchedulerTriggerController.java`, `common/response/ApiResponse.java`, `user/api/TestLoginController.java`
- `src/main/java/com/triagain/crew/application/CrewLockProperties.java`, `crew/infra/redis/CrewJoinRedisConfiguration.java`, `CrewJoinRedisStartupCheck.java`, `CrewJoinRedisProperties.java`, `crew/application/CrewJoinPendingWorker.java` (로그 문장)
- `src/main/java/com/triagain/common/config/S3Config.java`, `FirebaseConfig.java`, `verification/infra/S3StorageAdapter.java`, 기동 보정 러너 3종 (`StartupCompensationRunner`·`HabitStartupCompensationRunner`·`VerificationStartupCompensationRunner`, 클래스 이름만)
- `src/main/java` 전체 `@Value` grep 결과, 전략 로그 grep(대조군 포함)
- `load-test/scripts/generate-tokens.sh` (머리말), `load-test/monitoring/grafana/dashboards/triagain.json` (`"title"`)
- `spring-boot-3.4.13.jar` (Gradle 캐시): `SystemEnvironmentPropertyMapper`(`javap -c`), `SpringApplication`·`StartupInfoLogger`(상수 풀)
- Spring Boot 3.4 Reference "Externalized Configuration" (context7 조회: 우선순위 목록, 환경변수 이름 규칙, 프로필 last-wins)
- 읽기만: `triagain-back/load-test/아카이브/CREW-RUSH-DOCKER-환경-셋업-런북.md.20260713-154955-phaseD-monitoring.bak` (Phase A③ · C · D)
- Wave 1: `.worktrees/dbb-c5/load-test/k6/db-baseline-b.js` (`0d77184`, 머리 주석), `.worktrees/dbb-c7/load-test/sql/20~24_b_*.sql`·`29_b_cleanup_crew.sql` (`2995f79`, 머리말)
- 루트 `sdd/crew-join-db-baseline/`: `instructions/C3-launch-procedure.md`, `test-plan.md` (§1·§2·§4·§5), `measurement-contract.md` (§2-S·§2-I·§4·§5), `step0-overview.md` (§2·§3·§4·§5-1·§8), `instructions/U1-infra-user-checklist.md` (B·D·E·G·J·K절), `instructions/C7-sql-seeds.md` (§3-4 안 B Flyway 문장), `instructions/C8-collectors.md` (§3)
