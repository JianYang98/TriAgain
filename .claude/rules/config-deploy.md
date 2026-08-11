---
description: 설정·배포 파일 규칙 — application*.yml·워크플로·Dockerfile. 게이트는 "된다"로 확인, 잡 조건은 최종 파일로 확인. 수정 전 반드시 읽는다
paths: "src/main/resources/application*.yml, src/test/resources/application*.yml, .github/workflows/**, Dockerfile"
---

# 설정·배포 규칙 (yml · workflows · Dockerfile)

> 위 `paths` 파일을 편집할 때 로드된다.
>
> ⚠️ 이 영역은 tier-policy **Tier 3**다 — 운영 트래픽·보안 설정, 배포 파이프라인 직결 설정.
> "한 줄짜리 yml"로 보여도 Tier가 내려가지 않는다. 배포 트리거·경로 변경은 특히 그렇다.
>
> Java 구현 교훈은 `lessons-learned.md`, 마이그레이션 파일 규칙은 `db-migration.md`가 정본이다.
> 여기엔 **설정·배포 파일을 쓸 때의 규칙**만 둔다. 겹치는 사건은 한쪽에만 쓰고 반대편을 가리킨다.

---

## 1. 배포 게이트는 "있다"가 아니라 "된다"를 확인한다

- 왜? 존재 체크는 **죽은 자격증명을 통과시킨다.** 게다가 `GoogleCredentials.fromStream()`은 JSON 구조만 파싱하고 Google 통신은 첫 `send()`까지 지연된다 — 부팅 성공이 "키 정상"을 뜻하지 않는다. 다음 cron까지 아무도 모른다.
- 근거: `.github/workflows/deploy.yml:120-126`이 `[ -f "$FIREBASE_KEY_PATH" ]` 하나로 `FIREBASE_ENABLED=true`를 정한다. 2026-06-12 FCM 전면 실패(`docs/log/debugging-log.md`) — 무효 키로 부팅해 09:00 cron이 401로 전멸했다. 후속으로 스모크 엔드포인트 `POST /internal/fcm-test`가 생겼다(2026-06-13).
- 규칙: 외부 자격증명을 켜는 게이트는 **실호출 1회**로 확인한다. 워크플로 안에서 못 하면 배포 직후 스모크 호출을 절차에 넣는다. `deploy.yml:120-126`은 **지금도 존재 체크만 한다** — 이 파일을 손대는 김에 같이 올린다.
- ⚠️ 스모크의 **판정 기준도 같은 함정을 밟는다.** `InternalFcmTestController:27`은 `ResponseEntity.ok(...)`라 키가 죽어도 **HTTP 200**이고, 결과는 body 의 `FcmTestResult`(`SUCCESS`/`TOKEN_INVALID`/`ERROR`)에 들어 있다. `curl -f` 로는 초록불이 뜬다 — 게이트는 `data.status == SUCCESS` 로 판정한다. 토큰은 **전용 카나리**를 쓴다(실유저 토큰으로 스모크하면 남의 폰에 알림이 간다).

## 2. 잡 조건은 diff가 아니라 최종 파일에서 읽는다

- 왜? **게이트는 삭제로 사라지지 않고 이동으로 사라진다.** `e674fd8`(2026-03-17, PR #25)이 e2e 잡을 신설하면서 `needs: ci`가 e2e 쪽으로 옮겨 붙었고, `deploy-backend`에서 없어진 사실이 unified diff에서 **context 줄로 보였다**. 삭제(`-`) 표시가 안 났다. 그대로 11개 리비전·88회 배포를 지났다.
- 근거: **루트 저장소**(`triagain/`)의 `TODO/TODO-추후-07-30-develop-push가-운영에-직결-배포게이트-소실.md` — deploy.yml 이력 11 리비전 + Actions run 88건 집계. **이 저장소엔 없다**(BE 는 중첩 저장소다).
- 규칙: 워크플로를 고치면 `needs:`·`if:`를 **최종 파일에서 전량** 세로로 읽는다. 잡마다 "무엇이 이 잡을 막는가"를 한 줄로 답할 수 있어야 한다.
  ```bash
  command grep -nE '^  [A-Za-z0-9_-]+:|needs:|if: ' .github/workflows/*.yml
  ```
- 현재(2026-08-11): `deploy-backend`에 `needs:` 없음 → **push 경로에서 도는 테스트는 0개**이고 `develop` push도 운영 배포다. 어떻게 고칠지는 **방향 미정으로 파킹**돼 있으니 임의로 고치지 않는다. 선택지·`git-convention.md`와의 불일치는 위 TODO가, 실제로 걸리는 게이트 목록(브랜치 보호 포함)은 `test-strategy.md`가 정본이다 — 여기 복사하지 않는다.
- ⏳ 이 항목은 **배포 경로·트리거가 바뀌면 같은 PR에서 갱신한다.** 위 grep 결과와 어긋나면 grep이 맞다.

## 3. 프로파일 레인은 5개 파일에 흩어져 있다 — 한 곳만 보고 고치면 갈라진다

- 왜? 같은 키가 파일마다 다르고, **"키 없음"도 값이다**(공통 `application.yml` 상속). 마이그레이션이 실제로 도는 레인과 안 도는 레인이 여기서 갈린다.

  | 파일 | flyway | ddl-auto |
  |---|---|---|
  | `application.yml:18-20` | `baseline-on-migrate: true` · `baseline-version: 6` | — |
  | `application-local.yml:9,13` | `enabled: true` | `validate` |
  | `application-dev.yml:10` · `application-prod.yml:10` | 키 없음 (= 상속·활성) | `validate` |
  | `src/test/.../application-test.yml:9,13` | `enabled: false` | `create-drop` |
  | `src/test/.../application-integration.yml` | 키 없음 (= 활성) | 미지정 |

- 근거: 2026-03-08 Flyway baseline 도입(운영에서 `column already exists`), 2026-03-14 GitHub Actions 8건 실패(H2가 V9 `ALTER COLUMN SET NOT NULL`·V1 partial index 미지원 → test 레인에서 flyway를 끔). 둘 다 `docs/log/debugging-log.md`. 테스트 사각의 상세는 `db-migration.md` 5번이 정본이다.
- 규칙: yml 한 곳의 키를 바꾸면 **나머지 4개에서 같은 키를 grep해 레인별 결과를 적는다.**
  ```bash
  command grep -n "flyway\|ddl-auto" -A2 src/main/resources/application*.yml src/test/resources/application*.yml
  ```
- ⚠️ 로그를 레인의 근거로 쓰지 않는다: debugging-log 2026-03-08은 "로컬은 `ddl-auto: update`라 flyway 불필요 → `local`에서 `enabled: false`"라고 적혀 있지만 **현재 파일은 반대**(`enabled: true` · `validate`)다. 로그는 그 시점의 기록이다. 레인은 언제나 파일에서 확인한다.

## 4. Dockerfile의 `Asia/Seoul` 은 표시 설정이 아니라 도메인 시간 기준이다

- 왜? `Dockerfile:12`(`cp /usr/share/zoneinfo/Asia/Seoul /etc/localtime`)가 JVM 기본존을 정하고, pgjdbc는 커넥션 시작 시 **DB 세션 TimeZone 을 JVM 기본존으로 SET** 한다 — 드라이버가 startup 패킷에서 직접 하는 일이라 **커넥션 프로퍼티로 못 바꾼다**(바꾸려면 JVM `user.timezone` 또는 연결 후 `SET TIMEZONE`). `hibernate.jdbc.time_zone` 은 세션 tz 가 아니라 **Hibernate 의 타임스탬프 바인딩/판독**을 바꾸는 별개 축이고, 이 프로젝트엔 미설정이다. 크루 만료 쿼리(`ChallengeJpaRepository.findExpiredWithoutVerification`)는 naive 마감시각을 DB `NOW()`와 비교한다 — 즉 **이 한 줄이 만료·인증취소 판정 시각을 정한다.**
- 근거: **루트 저장소**(`triagain/`)의 `TODO/TODO-추후-07-17-타임존-도메인시간기준-실측.md` §0 (이 저장소엔 없다). 앱 세션 tz가 KST면 무해, non-KST면 최대 ~9시간 지연 — **실측 미완**이다.
- 규칙: `Dockerfile`의 TZ 줄과 `ENV SPRING_PROFILES_ACTIVE`(:17) 변경은 **Tier 3**. "컨테이너 로그 시각을 UTC로 맞추자" 같은 이유로 건드리지 않는다. 바꾸려면 그 TODO의 실측 4스텝을 먼저 끝낸다.

## 5. 태그가 환경을 안 가른다 — `latest`를 미는 것이 곧 운영 배포다

- 왜? `deploy.yml:97-99`가 `:latest`와 `:${{ github.sha }}`를 둘 다 push하지만, EC2가 pull 하는 건 `:latest` 하나다(:129, :151). 배포 대상도 `secrets.EC2_HOST` 한 대(:114), 프로파일도 `prod` 하드코딩(:139)이다 — **환경 분리가 없다.** 실험 빌드를 그 태그로 올리면 다음 배포·재기동이 그걸 집는다.
- 근거: `docs/log/future-considerations.md` 2026-03-15 "Docker 이미지 SHA 태그 기반 배포"(SHA 태그를 push하면서 배포엔 미사용 — 롤백 시 버전 추적 불가로 등록, 미착수) / 위 07-30 TODO §1.
- 규칙: 부하테스트·실험 이미지를 `devjian/triagain:latest`로 push하지 않는다. 그리고 배포는 무중단이 아니다 — `docker stop`→`run`→`sleep 15`→헬스체크(:152-153) 사이에 20~30초 교체 구간이 있다.

---

## 추가 방법

이 영역(설정·배포 파일)에서 실수가 나오면 **여기에** 추가한다. 형식은 `lessons-learned.md`의 "추가 방법"과 동일:

```markdown
### 규칙 제목
- 왜? 무엇이 어떻게 잘못되는지
- 근거: 실제 사건(파일:줄 또는 커밋/PR 번호) — 기억으로 쓰지 않는다
- 규칙: 다음부터 어떻게
```

**어디에 쓸지 헷갈릴 때**: yml·워크플로·Dockerfile을 *쓰는 사람*이 알아야 하면 여기, 마이그레이션 파일이면 `db-migration.md`, Java 코드면 `lessons-learned.md`. 양쪽 다 걸리면 **한쪽에만 쓰고 반대편에서 가리킨다**(3번이 그 예다).

> 여기서 다루지 않는 인접 영역: 레포 밖 수동 인프라(호스트 nginx·certbot·Route 53). 소스 관리에 안 잡혀 만료일까지 잠복한다 — 2026-07-08 TLS 만료 장애가 그 사례다(`debugging-log.md`).
