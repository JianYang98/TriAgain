---
name: pr-review-check
description: "/pr-review-check — 리뷰 수정 사항 재검증"
model: opus
---

# /pr-review-check — 리뷰 수정 사항 재검증

이전 리뷰에서 지적된 항목이 실제로 수정됐는지 빠르게 확인한다.
전체 리뷰를 다시 도는 것이 아니라, **지적 사항만 재검증**한다.

## 사용법
```
/pr-review-check
/pr-review-check api
/pr-review-check domain
/pr-review-check security
/pr-review-check docs-sync
/pr-review-check test
```

## 입력

| 실행 방법 | 읽는 파일 |
|----------|----------|
| 인자 없음 | `docs/review-comment/pr-review-comment.md` |
| `api` | `docs/review-comment/api-review-comment.md` |
| `domain` | `docs/review-comment/domain-review-comment.md` |
| `security` | `docs/review-comment/security-review-comment.md` |
| `docs-sync` | `docs/review-comment/docs-sync-review-comment.md` |
| `test` | `docs/review-comment/test-review-comment.md` |

---

## 동작 방식

### Step 1: 이전 리뷰 결과 읽기

해당 review-comment.md에서 🔴 CRITICAL + 🟡 WARNING 항목만 추출한다.
Docs Sync의 `## 🚨 Major Drift (즉시 수정)` 아래 항목은 `CRITICAL`,
`## ⚠️ Minor Drift (정리 권장)` 아래 항목은 `WARNING`으로 분류합니다.
`[Overall: ...]`은 전체 요약일 뿐 개별 항목의 심각도를 바꾸지 않습니다.
✅ APPROVE, 🟢 INFO는 무시.

시작 전에 아래를 확인합니다.

1. `git status --porcelain --untracked-files=no` 출력이 없나. 추적 파일의 미커밋 변경이 있으면 중단한다.
2. 리뷰 파일에 `review_head` 전체 SHA와 `review_branch`가 있나. 하나라도 없으면 중단한다.
3. `review_branch`가 현재 `git branch --show-current`와 정확히 같은가. 다르면 다른 브랜치의 결과이므로 중단한다.
4. `review_head`가 실제 commit이고 현재 HEAD의 조상인가.
   `git merge-base --is-ancestor <review_head> HEAD`가 실패하면 현재 커밋 계보의 결과가 아니므로 중단한다.
5. `review_head == HEAD`면 수정 커밋이 없으므로 재검증하지 않고 그 사실을 보고한다.

미추적 파일은 차단하지 않고 `git status --porcelain | grep '^??'` 목록을 리포트에 남깁니다.
어느 게이트든 실패하면 응답 최상단에 `BLOCKED: <사유>`를 적고 재검증을 중단합니다.
`/pr-review-fix`는 동일 HEAD에서 시작하고, `/pr-review-check`는 그 HEAD 이후 커밋을 검증합니다.

### Step 2: 각 항목별 수정 여부 확인

먼저 `git diff <review_head>..HEAD`로 리뷰 이후 변경분을 확보합니다. 각 지적 사항에 대해:
1. 해당 파일의 해당 심볼을 확인한다. 라인 번호는 길잡이지 근거가 아니다
2. 지적 내용이 수정됐는지 판단
3. 결과를 ✅ (수정됨) / ❌ (미수정) / ⚠️ (부분 수정)으로 표시

### Step 3: 재검증 리포트 출력

```markdown
# PR Review Check 결과

## 이전 리뷰: {파일명}

| # | 심각도 | 항목 | 상태 |
|---|--------|------|------|
| 1 | 🔴 CRITICAL | Controller에 비즈니스 로직 | ✅ 수정됨 — UseCase로 분리 |
| 2 | 🔴 CRITICAL | 외부 URL 검증 누락 | ✅ 수정됨 — Service에서 검증 추가 |
| 3 | 🟡 WARNING | null 가드 누락 | ✅ 수정됨 — email != null 가드 추가 |
| 4 | 🟡 WARNING | 상태 전이 유효성 검증 누락 | ❌ 미수정 |

## 결과: 3/4 수정 완료

🔴 CRITICAL: 2/2 ✅ 전부 수정
🟡 WARNING: 1/2 — 미수정 1건 남음

## 미수정 항목 상세
### #4: {지적 제목}
- 파일: {경로}:{심볼}
- 내용: {원 지적 요약}
- 수정 필요: {구체적 조치}
```

### Step 4: 판정

- 🔴 CRITICAL 0건 + 🟡 WARNING 0건 → **✅ PASS — 머지 가능**
- 🔴 CRITICAL 0건 + 🟡 WARNING 남음 → **🟡 PASS (WARNING 잔존) — 머지 가능하지만 권장 수정**
- 🔴 CRITICAL 남음 → **🔴 FAIL — /pr-review-fix로 추가 수정 필요**

---

## 전체 플로우

```
/pr-review          → 전체 리뷰 (diff에 걸린 관점만 실행)
  ↓
/pr-review-fix      → 지적 사항 수정
  ↓
/pr-review-check    → 수정됐는지 빠르게 확인 ← 이 커맨드
  ↓
✅ PASS → 머지
🔴 FAIL → /pr-review 다시 (fix 커밋으로 HEAD가 이동했으므로 재리뷰가 먼저)
         → /pr-review-fix → /pr-review-check
```

---

## /pr-review와의 차이

| | /pr-review | /pr-review-check |
|---|---|---|
| 범위 | 전체 코드 리뷰 | 이전 지적 사항만 |
| 속도 | 느림 (전체 스캔) | 빠름 (해당 항목만) |
| 토큰 | 많음 | 적음 |
| 용도 | 최초 리뷰 | 수정 후 재검증 |

---

## Language

한국어(Korean)로 진행합니다. 코드와 기술 용어는 영어로 유지합니다.
