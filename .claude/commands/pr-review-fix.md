---
name: pr-review-fix
description: "PR 리뷰 결과(review-comment.md)를 읽고 수정 플랜을 세워 실행한다. /pr-review 또는 개별 리뷰어(/api-reviewer 등) 실행 후 수정이 필요할 때 사용한다."
model: opus
---

리뷰 결과 파일을 읽고, 수정 플랜을 세워 실행합니다.

## Workflow

### Step 1: 리뷰 코멘트 읽기

인자에 따라 읽을 파일을 결정합니다.

| 실행 방법 | 읽는 파일 |
|----------|----------|
| `/pr-review-fix` (인자 없음) | `docs/review-comment/pr-review-comment.md` |
| `/pr-review-fix api` | `docs/review-comment/api-review-comment.md` |
| `/pr-review-fix domain` | `docs/review-comment/domain-review-comment.md` |
| `/pr-review-fix security` | `docs/review-comment/security-review-comment.md` |
| `/pr-review-fix docs-sync` | `docs/review-comment/docs-sync-review-comment.md` |
| `/pr-review-fix test` | `docs/review-comment/test-review-comment.md` |

파일이 없으면:
> "⚠️ 리뷰 코멘트 파일이 없습니다. 먼저 해당 리뷰어를 실행해주세요."
> - 종합 리뷰: `/pr-review`
> - 개별 리뷰: `/api-reviewer`, `/domain-reviewer`, `/security-reviewer`, `/docs-sync-reviewer`, `/test-reviewer`

**신선도 게이트:** 수정 시작 전 아래를 모두 만족해야 합니다.

1. `git status --porcelain --untracked-files=no` 출력이 없다. 추적 파일의 미커밋 변경이 있으면 중단한다.
2. 선택한 리뷰 파일에 `review_head` 전체 SHA와 `review_branch`가 있다. 하나라도 없으면 중단한다.
3. `review_head`가 현재 `git rev-parse HEAD`와 정확히 같다. 다르면 `/pr-review`를 다시 실행한다.
4. `review_branch`가 현재 `git branch --show-current`와 정확히 같다. 다르면 `/pr-review`를 다시 실행한다.

미추적 파일은 차단하지 않습니다. 수정 전 아래처럼 파일 단위 baseline을 `/tmp`에 저장하고,
플랜에는 출력된 임시 파일 경로와 개수만 남깁니다.

```bash
BASELINE_UNTRACKED_FILE=$(mktemp /tmp/triagain-pr-review-fix-untracked.XXXXXX)
git ls-files --others --exclude-standard | LC_ALL=C sort > "$BASELINE_UNTRACKED_FILE"
echo "untracked_baseline_file=$BASELINE_UNTRACKED_FILE"
wc -l "$BASELINE_UNTRACKED_FILE"
```
게이트가 실패하면 응답 최상단에 `BLOCKED: <사유>`를 적고 수정을 중단합니다.
리뷰 시점과 코드가 다르면 좌표와 심볼이 어긋난 대상을 수정할 수 있으므로 추측해서 진행하지 않습니다.

---

### Step 2: 수정 필요 항목 분류

리뷰 코멘트에서 수정이 필요한 항목을 추출하고 우선순위를 정합니다.
Docs Sync의 `## 🚨 Major Drift (즉시 수정)` 아래 항목은 `Critical`,
`## ⚠️ Minor Drift (정리 권장)` 아래 항목은 `Warning`으로 분류합니다.
`[Overall: ...]`은 전체 요약일 뿐 개별 항목의 심각도를 바꾸지 않습니다.

```
1순위: 🚨 Critical (즉시 수정) — 보안 결함, 데이터 손실 위험
2순위: ⚠️ Warning (개선 권장) — 컨벤션 위반, 성능 이슈
3순위: 📝 문서 동기화 — docs/spec/ 업데이트
```

---

### Step 3: 수정 플랜 제시

수정 전에 반드시 플랜을 먼저 보여줍니다. 승인 방식은 실행 맥락에 따라 다릅니다.

| 실행 맥락 | 승인 방식 |
|----------|----------|
| 독립 실행 | 플랜을 보여주고 사용자의 `전체 / Critical만 / 선택` 응답을 기다린다 |
| `/implement` Step 4 내부 | Step 1에서 승인된 범위만 자동 수정한다. 플랜은 출력하되 대기하지 않는다 |

다음 변경이 필요하면 실행 맥락과 무관하게 중단하고 사용자에게 넘깁니다.

- API 요청·응답·경로·HTTP status·ErrorCode 계약 변경
- 상태 전이, 락 전략, 비즈니스 규칙 수치 등 도메인 결정 변경
- `/implement` Step 1에서 승인되지 않은 파일·기능 변경

중단할 때는 남은 항목, 중단 사유, 필요한 사용자 판단을 한 줄씩 보고합니다.

```
## 🔧 수정 플랜

### 읽은 파일: [파일명]

### Critical (N건)
1. [파일명:라인] — 문제 요약 → 수정 방법
2. ...

### Warning (N건)
1. [파일명:라인] — 문제 요약 → 수정 방법
2. ...

### 문서 동기화 (N건)
1. [문서명] — 불일치 내용 → 수정 방법
2. ...

### 커밋
- 수정 후 커밋: 예 / 아니오 (기본 예)
- 아니오면 `/pr-review-check`를 호출하지 않고 `NO-COMMIT: 사용자 선택`으로 종료

진행할까요? (전체 / Critical만 / 선택)
```

---

### Step 4: 수정 실행

사용자 확인 후 수정을 실행합니다.

**수정 원칙:**
- Critical은 반드시 수정
- Warning은 사용자 선택에 따라
- 문서 동기화는 코드 수정과 함께 처리
- 수정할 때마다 어떤 리뷰 항목을 해결하는지 명시
- 커밋 전 실제 수정 범위는 아래 둘의 합집합으로 분류하고 게이트의 실행·생략 사유를 기록한다
  1. `git diff HEAD --name-only`의 tracked 변경
  2. 아래 명령이 출력하는 Step 1 이후 신규 untracked 파일

  ```bash
  comm -13 <Step 1에서 기록한 baseline 파일> \
    <(git ls-files --others --exclude-standard | LC_ALL=C sort)
  ```

  분류가 끝나면 Step 1의 baseline 임시 파일을 삭제한다.

  | 수정 범위 | 필수 검증 |
  |----------|----------|
  | 모든 변경 | `git diff --check` |
  | `*.java` | `./gradlew checkstyleMain checkstyleTest`, `./gradlew compileJava compileTestJava -x test`, `./gradlew test` |
  | `src/test/**`, `src/main/resources/**`, `build.gradle` | `./gradlew compileJava compileTestJava -x test`, `./gradlew test` |
  | 엔티티·Request DTO·삭제/캐스케이드 경로 또는 DB 마이그레이션 | 위 검증 + `./gradlew cleanE2eTest e2eTest` |
  | 설정·배포 | 해당 변경에 적용되는 `.claude/rules/config-deploy.md` 검증 |
  | 문서·하네스만 변경 | Gradle 검증 생략 — `docs/harness-only`로 기록 |

- 여러 행에 해당하면 검증을 합쳐서 모두 실행한다
- 테스트 선택의 정본은 `.claude/rules/test-strategy.md`와 변경 유형별 rule이다. 정본이 추가 검증을 요구하면 함께 실행한다
- 선택된 검증이 모두 통과하면 리뷰 항목의 수정만 `.claude/rules/git-convention.md`에 맞춰 커밋한다
  - 독립 실행: Step 3의 사용자 승인 플랜에 커밋을 명시한 경우만
  - `/implement` 내부: Step 1 승인 범위 안에서 자동 커밋
  - 사용자가 커밋을 원치 않으면 `NO-COMMIT: 사용자 선택`으로 정상 종료하고 `/pr-review-check`를 호출하지 않음
  - 검증이 하나라도 실패하면 `BLOCKED: 필수 검증 실패`를 보고하고 커밋과 `/pr-review-check`를 실행하지 않음

`/pr-review-check`는 `review_head..HEAD`의 커밋을 검증하므로 수정만 하고 커밋을 생략하지 않습니다.

---

### Step 5: 수정 완료 리포트

모든 수정이 끝나면 **원본 리뷰 코멘트 파일**에 수정 결과를 추가합니다.

```
---

## 🔧 수정 완료 (YYYY-MM-DD HH:MM)

### 수정된 항목
| # | 카테고리 | 파일 | 수정 내용 | 상태 |
|---|----------|------|----------|------|
| 1 | Critical | CreateVerificationService.java | cross-crew 검증 추가 | ✅ 완료 |
| 2 | Warning | UploadSession.java | expire() Javadoc 추가 | ✅ 완료 |
| 3 | Docs | api-spec.md | 새 엔드포인트 반영 | ✅ 완료 |

### 미수정 항목 (사유)
| # | 카테고리 | 사유 |
|---|----------|------|
| 4 | Warning | Phase 2에서 처리 예정 |

### 검증 결과
- git diff --check: PASS/FAIL
- Checkstyle: PASS/FAIL/SKIPPED (사유)
- Compile: PASS/FAIL/SKIPPED (사유)
- Test: PASS/FAIL/SKIPPED (사유)
- E2E: PASS/FAIL/SKIPPED (사유)
- 변경 유형별 rule 검증: PASS/FAIL/SKIPPED (사유)

### 재리뷰 필요 여부
[모든 Critical 수정 완료 → 해당 리뷰어 재실행 권장]
```

---

## Usage Examples

### 종합 리뷰 수정 (가장 자주 쓰는 패턴)

```
/pr-review-fix
```
→ pr-review-comment.md 읽고 → 수정 플랜 → 실행

### 개별 리뷰어 결과 수정

```
/pr-review-fix api
```
→ api-review-comment.md 읽고 → 수정 플랜 → 실행

```
/pr-review-fix domain
```
→ domain-review-comment.md 읽고 → 수정 플랜 → 실행

```
/pr-review-fix security
```
→ security-review-comment.md 읽고 → 수정 플랜 → 실행

```
/pr-review-fix docs-sync
```
→ docs-sync-review-comment.md 읽고 → 수정 플랜 → 실행

### Critical만 수정

```
/pr-review-fix Critical만 수정해줘
```

```
/pr-review-fix domain Critical만 수정해줘
```

### 특정 항목만 수정

```
/pr-review-fix 3번이랑 5번만 수정해줘
```

### 수정 후 재리뷰

```
/pr-review-fix 수정 끝났어. 재리뷰 돌려줘
```
→ 수정 완료 리포트 작성 → 해당 리뷰어 재실행 안내

---

## Tips

- **리뷰 → 수정 사이클**: `/pr-review` → `/pr-review-fix` → `/pr-review` (재리뷰)
- **개별도 같은 사이클**: `/api-reviewer` → `/pr-review-fix api` → `/api-reviewer` (재리뷰)
- **플랜 확인 후 실행**: 수정 플랜을 보고 "전체 / Critical만 / 선택" 결정
- **수정 결과가 원본 파일에 기록됨**: 나중에 뭘 고쳤는지 추적 가능
- **재리뷰 권장**: Critical 수정 후 리뷰어 다시 돌려서 확인

## Language

한국어(Korean)로 진행합니다. 코드와 기술 용어는 영어로 유지합니다.
