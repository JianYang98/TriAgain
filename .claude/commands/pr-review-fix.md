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

파일이 없으면:
> "⚠️ 리뷰 코멘트 파일이 없습니다. 먼저 해당 리뷰어를 실행해주세요."
> - 종합 리뷰: `/pr-review`
> - 개별 리뷰: `/api-reviewer`, `/domain-reviewer`, `/security-reviewer`, `/docs-sync-reviewer`

---

### Step 2: 수정 필요 항목 분류

리뷰 코멘트에서 수정이 필요한 항목을 추출하고 우선순위를 정합니다.

```
1순위: 🚨 Critical (즉시 수정) — 보안 결함, 데이터 손실 위험
2순위: ⚠️ Warning (개선 권장) — 컨벤션 위반, 성능 이슈
3순위: 📝 문서 동기화 — docs/spec/ 업데이트
```

---

### Step 3: 수정 플랜 제시

수정 전에 반드시 플랜을 먼저 보여주고 확인을 받습니다.

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
- 수정 후 관련 테스트가 있으면 실행하여 확인

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
