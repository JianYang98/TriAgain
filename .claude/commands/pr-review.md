---
name: pr-review
description: "TriAgain PR 전체 리뷰 워크플로우. 변경된 파일을 분석하여 api-reviewer, domain-reviewer, security-reviewer, docs-sync-reviewer를 필요한 것만 순차 실행하고 종합 리포트를 생성한다."
model: opus
---

현재 브랜치의 변경사항에 대해 전체 리뷰를 수행합니다.

## Workflow

### Step 0: 변경 범위 파악

```bash
# 리뷰 범위는 커밋된 HEAD다. 추적 파일의 미커밋 변경은 diff에서 빠지므로 시작하지 않는다.
test -z "$(git status --porcelain --untracked-files=no)" || {
  echo "BLOCKED: 추적 파일에 미커밋 변경이 있습니다. 커밋 또는 정리 후 다시 실행하세요."
  exit 1
}

# 미추적 파일은 PR diff 대상이 아니므로 차단하지 않고 리포트에 목록만 남긴다.
git status --porcelain | grep '^??' || true

# 기준점은 PR 이 어디로 가는지로 정한다 — 고정하면 한쪽 PR 은 반드시 틀린다
BASE=$(gh pr view --json baseRefName -q .baseRefName 2>/dev/null || echo develop)
echo "기준: origin/$BASE"
git fetch origin "$BASE" -q || echo "⚠️ fetch 실패 — origin/$BASE 가 낡았을 수 있다"
git diff "origin/$BASE...HEAD" --name-only
```

위 검사가 실패하면 응답 최상단에 `BLOCKED: <사유>`를 적고 리뷰와 결과 파일 생성을 중단합니다.

⚠️ **0개면 리뷰를 진행하지 말고 기준점부터 의심한다.** PR 생성 전에 돌리면 `BASE` 가 `develop` 으로
폴백되는데, 릴리스(`develop → main`)를 보려던 거였다면 자기 자신과 비교해 0파일이 나온다.

변경된 파일 목록을 카테고리별로 분류합니다:

| 카테고리 | 파일 패턴 | 기준 |
|----------|----------|-------------|
| API | `*.api/`, Controller, Request DTO, ErrorCode, SecurityConfig | api-reviewer |
| Domain | `*.domain/`, `*.application/`, Policy, Port | domain-reviewer |
| Security | `common/auth/`, JWT, OAuth, `/internal`, SecurityConfig | security-reviewer |
| Data | JPA Entity·Repository·DB persistence Adapter, `db/migration/**`, `nativeQuery`, `createNativeQuery` | `docs/spec/schema.md` + 변경 유형별 규칙 |
| Config·Deploy | `application*.yml`, Dockerfile, `.github/workflows/**`, `lambda/**`, 빌드·배포 wiring을 바꾼 `build.gradle` | `.claude/rules/config-deploy.md` |
| Docs | `docs/spec/`, Flyway, ErrorCode, error-messages.properties | docs-sync-reviewer |
| Test | `src/test/**`, `application-test.yml`, `*.feature`, 테스트 태스크를 바꾼 `build.gradle` | test-reviewer |

**하나의 파일이 여러 카테고리에 해당할 수 있습니다.**
예: SecurityConfig → API + Security, Flyway → Data + Docs, 테스트 태스크를 바꾼 build.gradle → Config·Deploy + Test.
`lambda/**`는 실제 배포 워크플로의 변경 감지 범위이므로 Config·Deploy에 포함합니다.

`Adapter`라는 이름만으로 Data에 넣지 않습니다. JPA·SQL·DB persistence 근거가 있는 변경만 Data입니다.

변경된 파일이 어떤 카테고리에도 해당하지 않으면 해당 리뷰어는 스킵합니다.

⚠️ **하네스 파일은 리뷰 관점이 아닙니다.** `.claude/**`, `CLAUDE.md`, `AGENTS.md`가 diff에 있으면
루트 오케스트레이션 저장소의 `.claude/rules/tier-policy.md` 하네스 변경 트랙으로 안내합니다.
이 클론에서 해당 파일을 읽을 수 없으면 검증하지 않은 채 PASS하지 말고 그 사실을 보고합니다.

---

### Step 1: API 리뷰 (해당 시)

**트리거**: Controller, Request/Response DTO, ErrorCode, SecurityConfig permitAll 경로 변경

변경된 API 관련 파일을 대상으로 `.claude/commands/api-reviewer.md` 기준에 따라 리뷰합니다.

검증 항목:
- RESTful 규칙 준수
- ApiResponse 래퍼 일관 사용
- HTTP Status Code 적절성
- @Valid, @AuthenticatedUser 어노테이션
- api-spec.md와 구현 일치

---

### Step 2: 도메인 리뷰 (해당 시)

**트리거**: 엔티티, VO, Policy, UseCase/Service, Port, 상태 전이 로직 변경

변경된 도메인 관련 파일을 대상으로 `.claude/commands/domain-reviewer.md` 기준에 따라 리뷰합니다.

검증 항목:
- 헥사고날 레이어 규칙 (의존성 방향)
- Bounded Context 경계 (import 위반)
- 비즈니스 규칙이 Domain에 위치하는지
- 상태 전이 유효성
- 동시성 제어 (멱등성 + 락 + UK)
- biz-logic.md와 구현 일치

---

### Step 3: 보안 리뷰 (해당 시)

**트리거**: 인증/인가, JWT, OAuth, SecurityConfig, /internal, S3 Presigned URL, 입력값 검증 변경

변경된 보안 관련 파일을 대상으로 `.claude/commands/security-reviewer.md` 기준에 따라 리뷰합니다.

검증 항목:
- JWT 서명 검증, Secret 관리
- OAuth 서버 측 검증
- 인가 (@AuthenticatedUser, 멤버십 검증)
- /internal 엔드포인트 보안 (InternalApiKeyFilter)
- S3 Presigned URL 보안
- 민감 정보 노출 방지
- prod/dev 분리 (@Profile)

---

### Step 4: 테스트 리뷰 (해당 시)

**트리거** (둘 중 하나):
- ⓐ `src/test/**`, `src/test/resources/application-test.yml`, `*.feature` 변경 → 변경된 테스트를 리뷰
- ⓑ **프로덕션 코드가 바뀌었는데 위 경로 변경이 0건** → 테스트 부재 자체를 리포트한다.
  `*.api/`·Controller·`*.domain/`·`*.application/` 이 바뀐 경우면 **CRITICAL**, 그 외는 WARNING.
  ⓑ는 test-reviewer 를 호출하지 않는다 — 볼 파일이 없으므로 판정만 이 문서에서 한다.

ⓐ의 경우 변경된 테스트 파일을 대상으로 `.claude/commands/test-reviewer.md` 기준에 따라 리뷰합니다.

검증 항목:
- ScenarioContext 상태 관리 (`putCrewId()` vs `setCrewId()` 구분)
- 테스트 어댑터 URL·파라미터 정확성
- Given 단계의 상태 설정 방식
- H2 호환성
- 단위테스트 품질 (비즈니스 규칙을 실제로 검증하는가)
- Cucumber 시나리오 일관성

⚠️ **Scope 한계** — test-reviewer 는 *"최근 변경된 테스트 파일"* 기준이라 **테스트가 없어서 생긴 공백은 못 잡는다**
(변경된 테스트 파일이 애초에 없으므로) — 그래서 위 트리거 ⓑ 를 둔다.

ⓑ 는 **"테스트가 있는가"만** 본다. **"테스트가 의미 있는가"**(API 레벨을 실제로 태우는가 —
컨트롤러 라우팅·인증·직렬화·에러코드→HTTP 매핑)는 상위 오케스트레이션 저장소(`triagain/`)의
`/verify` §1 이 담당하며 **이 클론엔 딸려오지 않는다**
(실측 2026-08-13: `git ls-files | grep -i verify` → 0건, `harness-decisions` → 0건).
**그 층은 여전히 공백이다** (PR #153 Codex P2).

---

### Step 5: 데이터 리뷰 (해당 시)

**트리거**: JPA Entity·Repository·DB persistence Adapter, `db/migration/**`, 네이티브 쿼리 변경

전용 리뷰어 파일은 두지 않습니다. `docs/spec/schema.md`와 실제 구현을 대조하고,
마이그레이션 변경이면 `.claude/rules/db-migration.md`, Java persistence 변경이면
`.claude/rules/lessons-learned.md`를 읽습니다. 규칙 내용을 여기에 복사하지 않습니다.

---

### Step 6: 설정·배포 리뷰 (해당 시)

**트리거**: `application*.yml`, Dockerfile, `.github/workflows/**`, `lambda/**`, 빌드·배포 wiring을 바꾼 `build.gradle` 변경

`.claude/rules/config-deploy.md`를 읽고 판정합니다. `build.gradle`의 테스트 태스크·필터 변경은
Step 4 테스트 리뷰도 함께 실행합니다. 규칙 내용을 여기에 복사하지 않습니다.

---

### Step 7: 문서 동기화 리뷰 (항상 실행)

**트리거**: 항상 실행 (코드 변경이 있으면 문서도 업데이트되어야 하므로)

변경된 파일과 관련된 문서의 동기화 상태를 `.claude/commands/docs-sync-reviewer.md` 기준에 따라 검증합니다.
Docs Sync의 `## 🚨 Major Drift (즉시 수정)` 아래 항목은 `CRITICAL`,
`## ⚠️ Minor Drift (정리 권장)` 아래 항목은 `WARNING`으로 분류합니다.
`[Overall: ...]`은 전체 요약일 뿐 개별 항목의 심각도를 바꾸지 않습니다.

검증 항목:
- api-spec.md ↔ Controller 일치
- schema.md ↔ JPA Entity / Flyway 일치
- biz-logic.md ↔ Domain 코드 일치
- ErrorCode ↔ api-spec.md / error-messages.properties 일치

---

### Step 8: 종합 리포트 → 파일 저장

모든 리뷰 결과를 종합하여 `docs/review-comment/pr-review-comment.md`에 저장합니다.

**파일명 규칙:** `docs/review-comment/pr-review-comment.md` (항상 덮어쓰기)

> 이 파일은 `/pr-review-fix` 커맨드에서 읽어서 수정 플랜을 세우는 데 사용됩니다.
> 개발 에이전트가 이 파일만 보고 무엇을 어떻게 고쳐야 하는지 알 수 있도록 작성합니다.
> 종합·개별 리뷰 결과 모두 `review_head`에 `git rev-parse HEAD`의 전체 SHA를,
> `review_branch`에 `git branch --show-current`를 기록합니다.

아래 형식으로 작성합니다.

```markdown
## 🎯 PR 리뷰 종합 리포트

### 변경 범위
- review_head: `<git rev-parse HEAD의 전체 SHA>`
- review_branch: `<git branch --show-current>`
- 기준 브랜치: `origin/<BASE>`
- 변경 파일: N개
- 실행된 리뷰어: [실제로 실행한 것만 나열. 트리거가 안 맞아 건너뛴 리뷰어는 여기 쓰지 않는다]

---

### 📊 리뷰어별 결과

| 리뷰어 | 결과 | Critical | Warning | 비고 |
|--------|------|----------|---------|------|
| API | PASS/SUGGESTIONS/IMPROVEMENT/**SKIPPED** | 0 | 0 | |
| Domain | PASS/SUGGESTIONS/IMPROVEMENT/**SKIPPED** | 0 | 0 | |
| Security | PASS/SUGGESTIONS/CRITICAL/**SKIPPED** | 0 | 0 | |
| Data | PASS/SUGGESTIONS/IMPROVEMENT/**SKIPPED** | 0 | 0 | schema.md + 변경 유형별 규칙 |
| Config·Deploy | PASS/SUGGESTIONS/CRITICAL/**SKIPPED** | 0 | 0 | config-deploy.md |
| Test | PASS/SUGGESTIONS/IMPROVEMENT/**CRITICAL**/**SKIPPED** | 0 | 0 | ⓑ(테스트 부재)면 CRITICAL |
| Docs Sync | IN SYNC/MINOR/MAJOR | 0 | 0 | (항상 실행) |

⚠️ **안 돌린 리뷰어는 반드시 `SKIPPED` 로 적고 비고에 트리거 미충족 사유를 쓴다.**
Docs Sync 외 관점은 전부 조건부 실행이라, 안 돌았는데 `PASS`·`0건`으로 적으면
**"통과"로 읽히지만 실은 "아무것도 안 봤다"** 가 된다. 이 저장소에서 실제로 난 사고다 —
`docs-sync-reviewer` 가 존재하지 않는 `messages.properties` 를 보며 `0건`을 계속 찍었다(2026-08-13 PR #153).

---

### 🚨 즉시 수정 필요 (Critical)
[모든 리뷰어의 CRITICAL 이슈를 우선순위순으로 모음]

### ⚠️ 개선 권장 (Warnings)
[모든 리뷰어의 WARNING을 카테고리별로 모음]

### ✅ 잘된 점
[각 리뷰어에서 칭찬한 점 요약]

---

### 🏁 최종 판정
[APPROVE / REQUEST CHANGES / NEEDS DISCUSSION]

- APPROVE: Critical 0건, Warning은 선택적 수정
- REQUEST CHANGES: Critical 1건 이상 → 수정 후 재리뷰 필요
- NEEDS DISCUSSION: 설계 판단이 필요한 이슈 → 논의 후 결정
```

---

## Usage Examples

### 기본 사용 (가장 자주 쓰는 패턴)

```
/pr-review
```
→ PR 의 base 브랜치 기준으로 변경된 파일 자동 분류 → 해당 리뷰어 실행

### 브랜치 지정

```
/pr-review feat/s3-lambda-presigned-url 브랜치 리뷰해줘
```

### 특정 리뷰어만 실행

```
/pr-review api + security만 돌려줘
```

```
/pr-review docs-sync만 돌려줘
```

### 변경 파일 직접 지정

```
/pr-review 이 파일들만 리뷰해줘:
- CrewController.java
- CreateCrewRequest.java
- SecurityConfig.java
```

### 이전 리뷰 수정 후 재리뷰

```
/pr-review 지난 리뷰에서 Critical 수정했어. 재리뷰해줘
```

---

## Tips

- **`/pr-review` 한 번이면 전체 리뷰**: 개별 리뷰어를 따로 돌릴 필요 없음
- **변경 파일이 적으면 빠르다**: 변경 파일 3개면 해당 리뷰어만 실행, 토큰 절약
- **Critical이 나오면 즉시 수정**: APPROVE가 아니면 머지하지 말 것
- **docs-sync는 항상 실행**: 코드만 바꾸고 문서 안 바꾸는 실수를 방지
- **개별 리뷰어도 여전히 사용 가능**: 특정 영역만 깊게 볼 때는 `/api-reviewer`, `/domain-reviewer` 등 직접 실행
