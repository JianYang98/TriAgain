---
description: Flyway 마이그레이션 작성 규칙 — 불변성·버전 선점·연쇄 점검. 마이그레이션 파일 추가/수정 전 반드시 읽는다
paths: "src/main/resources/db/migration/**"
---

# DB 마이그레이션 규칙 (Flyway)

> `src/main/resources/db/migration/**` 작업 시 로드된다.
>
> ⚠️ 이 영역은 tier-policy **Tier 3**(DB 스키마 변경·마이그레이션)다 — SDD 풀트랙 + 사용자 승인 대상.
> "한 줄짜리 ALTER"로 보여도 Tier가 내려가지 않는다.
>
> Java 구현 교훈은 `lessons-learned.md`가 정본이다. 여기엔 **마이그레이션 파일을 쓸 때의 규칙**만 둔다.
> 겹치는 사건은 한쪽에만 쓰고 반대편은 가리킨다(복사 금지).

---

## 1. 이미 적용된 마이그레이션 파일은 한 글자도 고치지 않는다 (체크섬)

- 왜? Flyway는 적용 시점의 파일 체크섬을 `flyway_schema_history`에 저장한다. 파일이 바뀌면 다음 기동에서 검증 실패로 **앱이 뜨지 않는다.** 이미 배포된 환경이 있으면 로컬만의 문제가 아니다.
- 근거: `sdd/grace-targetdate/step3-schema.md:27` — "기존 파일 수정 절대 금지 (체크섬)". `sdd/verification-cancel/step0-overview.md:180`에 "과거 V22 체크섬 충돌 이슈는 해소된 상태"로 남아 있다(과거에 실제로 겪었다는 뜻).
- 규칙: 스키마를 바꿔야 하면 **기존 파일 수정이 아니라 새 버전 파일 추가.** 오타 하나라도 마찬가지다.
- 로컬에서 이미 깨졌다면: 운영과 무관한 로컬 DB에 한해 복구 절차가 필요하다. **[미확인]** — 이 프로젝트의 표준 복구 절차가 문서화된 기록을 찾지 못했다. 실행 전 사용자에게 확인할 것.

## 2. 다음 버전 번호는 "원격 기준"으로 확인한다 — 로컬 최신 ≠ 진짜 최신

- 왜? 브랜치마다 마이그레이션 진도가 다르다. 뒤처진 브랜치에서 번호를 정하면 **이미 남이 쓴 번호를 선점**해 머지 시점에 충돌한다.
- 근거(실측): `origin/feat/load-test`는 **V21·V23·V24가 없이 V22까지**만 있다(`sdd/verification-cancel/step0-overview.md:181`). `sdd/grace-targetdate/step3-schema.md:29`도 "feat/load-test 브랜치(V22가 최신)라 develop의 최신본이 아닐 수 있음"이라고 같은 함정을 경고한다.
- 규칙: 번호를 정하기 전 **`origin/develop` 기준**으로 목록을 확인한다.
  ```bash
  git ls-tree -r --name-only origin/develop -- src/main/resources/db/migration | sort -V | tail -3
  ```
  로컬 `ls`만 보고 정하지 않는다.

## 3. 새 UNIQUE 제약을 추가하면 예외 매핑 분기 점검을 세트로 한다

- 왜? 제약 위반은 `DataIntegrityViolationException`으로 올라와 `GlobalExceptionHandler`가 **제약 이름으로** 에러코드를 가른다. 새 제약을 추가하고 분기를 안 건드리면 조용히 엉뚱한 코드가 나간다 — 컴파일도 단위테스트도 통과한다.
- 근거: **4개월간 오매핑**이 방치된 실제 사건. 상세·원인 분석은 `lessons-learned.md`의 **"DB 제약 이름으로 분기할 땐 마이그레이션에서 이름을 복사하라"**가 정본이다(여기 복사하지 않는다). 그 사건에서 V23이 `uk_habit_verifications_*` 2건을 추가했는데 분기가 갱신되지 않은 것이 재발 경로였다.
- 규칙 — 제약을 추가하는 마이그레이션은 아래 3개가 **한 커밋**에 들어간다:
  1. 마이그레이션의 제약 이름 (짓고 나면 그 문자열이 계약이다)
  2. `GlobalExceptionHandler` 분기 — **정확 매칭**으로 추가 (`contains()` 부분매칭 금지: 새 제약이 남의 분기에 조용히 삼켜진다)
  3. 통합테스트 — 실제 위반을 유발해 응답 코드를 assert

## 4. `schema.md` 동기화는 같은 커밋에 포함한다

- 왜? `docs/spec/schema.md`가 스키마 정본이고 다른 문서·리뷰가 그걸 기준으로 판단한다. 마이그레이션만 나가면 **정본이 코드보다 낡는다.**
- 근거: 실제로 드리프트했다 — `schema.md` 인덱스 섹션이 V1 시절 **단수형 테이블명**(`verification`·`report`·`review`) 초안 상태로 남아, 실제(`verifications`·`reports`)와 어긋났다. `sdd/verification-cancel/step3-schema.md:175`가 "나중에 추가된 V6·V22·V23 항목은 정확하고 **초기 V1 시절 항목만 오래된 초안 상태**"라고 특정한다. 정정은 `#104`(핵심 3건)·`#108`(잔여 4건)로 나눠 처리됐다.
- 규칙: 마이그레이션 + `schema.md` + (해당되면) `biz-logic.md`를 같은 커밋에. **테이블명·인덱스명·제약명은 마이그레이션에서 복사**한다 — 기억으로 쓰지 않는다.

## 5. 마이그레이션 문법 오류는 단위·Cucumber 테스트로 잡히지 않는다

- 왜? `src/test/resources/application-test.yml`이 **`flyway.enabled: false` + `ddl-auto: create-drop`**이다(:9, :13). 즉 그 프로파일에서는 마이그레이션 SQL이 **아예 실행되지 않고** Hibernate가 엔티티로 스키마를 만든다. 마이그레이션이 문법적으로 깨져 있어도 초록불이 뜬다.
- 배경: 이 설정은 의도된 것이다 — TestContainers를 공유하는 복수 Spring Context에서 Flyway 이력이 충돌했고(`docs/log/debugging-log.md`, 2026-03-08 전후), H2에서 V9의 `ALTER COLUMN SET NOT NULL`과 V1의 partial index가 미지원이라 깨졌다. 끄는 쪽으로 해결했다.
- 규칙: 마이그레이션을 추가/수정하면 **integration 프로파일 테스트를 반드시 돌린다.** `src/test/resources/application-integration.yml`에는 flyway 비활성화 설정이 없어(= Spring Boot 기본값 활성) TestContainers PostgreSQL에 실제 마이그레이션이 적용된다 — 제약 위반을 실제로 유발하는 통합테스트가 이 경로로 동작한다.

## 6. 운영은 baseline 위에서 돈다 — V6 이전은 존재하지 않는 것으로 취급된다

- 사실: `src/main/resources/application.yml:21-23`이 **`baseline-on-migrate: true` / `baseline-version: 6`**이다. 기존 DB에 Flyway를 도입하며 설정한 값이다(`docs/log/debugging-log.md` 2026-03-08 — baseline 없이 붙였다가 V2부터 재실행되며 `column already exists`로 실패한 기록).
- 함의: 운영 DB는 V6 시점 스키마를 출발점으로 삼는다. **V1~V5를 고쳐도 운영에 반영되지 않는다** — 그 파일들은 이력 재현용이다.
- 규칙: 운영에 나가야 하는 변경은 반드시 **새 버전**으로 만든다. 과거 파일을 고쳐 반영하려는 시도는 1번(체크섬) 위반이자 여기서도 무효다.

---

## 추가 방법

이 영역(마이그레이션·스키마)에서 실수가 나오면 **여기에** 추가한다. 형식은 `lessons-learned.md`의 "추가 방법"과 동일:

```markdown
### 규칙 제목
- 왜? 무엇이 어떻게 잘못되는지
- 근거: 실제 사건(파일:줄 또는 커밋/PR 번호) — 기억으로 쓰지 않는다
- 규칙: 다음부터 어떻게
```

**어디에 쓸지 헷갈릴 때**: 마이그레이션 파일을 *쓰는 사람*이 알아야 하면 여기, Java 코드를 *쓰는 사람*이 알아야 하면 `lessons-learned.md`. 양쪽 다 걸리면 **한쪽에만 쓰고 반대편에서 가리킨다**(3번이 그 예다).

> 아직 규칙이 없는 인접 영역: `application*.yml`, `Dockerfile`, `.github/workflows/deploy.yml`.
> 전부 Tier 3 대상인데 로드되는 규칙이 0개다 — 그쪽에서 사건이 쌓이면 같은 방식으로 전용 규칙을 만든다.
