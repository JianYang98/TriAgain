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
/pr-review-check state
/pr-review-check ui
/pr-review-check code-quality
```

## 입력
- 인자 없음: `docs/review-comment/pr-review-comment.md` 읽기
- 인자 있음: `docs/review-comment/{인자}-review-comment.md` 읽기

---

## 동작 방식

### Step 1: 이전 리뷰 결과 읽기

해당 review-comment.md에서 🔴 CRITICAL + 🟡 WARNING 항목만 추출한다.
✅ APPROVE, 🟢 INFO는 무시.

### Step 2: 각 항목별 수정 여부 확인

각 지적 사항에 대해:
1. 해당 파일의 해당 라인을 확인
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
| 4 | 🟡 WARNING | dispose 시 cancel 누락 | ❌ 미수정 |

## 결과: 3/4 수정 완료

🔴 CRITICAL: 2/2 ✅ 전부 수정
🟡 WARNING: 1/2 — 미수정 1건 남음

## 미수정 항목 상세
### #4: dispose 시 cancel 누락
- 파일: lib/features/profile/screens/edit_profile_screen.dart
- 내용: CancelToken이 dispose에서 cancel 되지 않음
- 수정 필요: dispose() 메서드에 _cancelToken.cancel() 추가
```

### Step 4: 판정

- 🔴 CRITICAL 0건 + 🟡 WARNING 0건 → **✅ PASS — 머지 가능**
- 🔴 CRITICAL 0건 + 🟡 WARNING 남음 → **🟡 PASS (WARNING 잔존) — 머지 가능하지만 권장 수정**
- 🔴 CRITICAL 남음 → **🔴 FAIL — /pr-review-fix로 추가 수정 필요**

---

## 전체 플로우

```
/pr-review          → 전체 리뷰 (4개 리뷰어)
  ↓
/pr-review-fix      → 지적 사항 수정
  ↓
/pr-review-check    → 수정됐는지 빠르게 확인 ← 이 커맨드
  ↓
✅ PASS → 머지
🔴 FAIL → /pr-review-fix 다시 → /pr-review-check 다시
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
