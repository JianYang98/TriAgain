---
description: Git 브랜치 전략과 커밋 메시지 컨벤션 (AngularJS Convention, 한국어)
---

# Git Convention

## 브랜치 전략

- main: 운영 배포 (직접 push 금지, develop에서 PR로만 병합)
- develop: 통합 브랜치 (feat→develop PR, CI + E2E 통과 필수)
- feat/*: 기능 개발 브랜치 (develop에서 분기, develop으로 PR)
- fix/*: 버그 수정 브랜치 (develop에서 분기, develop으로 PR)

## 워크트리 (worktree)

**언제 쓰나**: 메인 체크아웃이 다른 브랜치에 있거나 미커밋 변경이 붙어 있을 때.
그 트리에서 브랜치를 바꾸지 말고 워크트리를 판다.

```bash
git fetch origin develop                # ← origin/develop 이 낡으면 낡은 커밋에서 갈라진다
git worktree add <경로> origin/develop -b <브랜치>
git -C <경로> push -u origin <브랜치>   # ← 생략 금지(이유는 아래). 에이전트는 못 하니 사용자에게 요청
```

**`push -u`를 생성 직후에 하는 이유** — 두 가지가 한 번에 해결된다.

1. 이걸 빼면 upstream이 `origin/develop`으로 잡힌다. 그 상태에서 `git push`를 치면 git이
   거부하면서 **`git push origin HEAD:develop`을 제안**하는데, 그대로 실행하면 develop에
   직접 밀어넣어 PR·CI를 전부 건너뛴다.
2. 커밋 후 push는 잊히지만 생성 시점은 절차의 일부라 안 잊힌다.

**⛔ 금지**: 미커밋 변경이 붙은 트리(예: `feat/load-test`)에 `git restore`/`checkout`/`reset`.
untracked 파일은 워크트리를 따라가지 않으므로 "옮기면 되겠지"도 틀렸다.

**끝낼 때**: PR 머지 → `git worktree remove <경로>` → 원격 브랜치 삭제.
로컬 브랜치는 squash-merge면 `-d`가 거부하므로, **PR 상태로 머지를 확인한 뒤** `-D`를 쓴다.

**세션 마감 점검**: `git log --branches --not --remotes --oneline` 이 비어 있는지 본다.
비어 있지 않으면 push 안 된 커밋이 있다. (원격 없는 저장소 — 루트 `triagain` — 은 예외)

## 커밋 메시지 (AngularJS Convention)

**형식**
```
<type>: <한국어 메시지>
- 부연 설명 (선택, 최대 2줄)
```

**예시**
```
feat: 인증 기능 추가
- 사진 인증 시 presignedUrl 발급 로직 추가
```

```
fix: 크루 정원 초과 버그 수정
- SELECT FOR UPDATE 락 누락 수정
```

```
refactor: Verification 도메인 계층 분리
- UseCase와 Policy 클래스 분리
```

**커밋 타입**

| 타입 | 용도 |
|------|------|
| feat | 새로운 기능 추가 |
| fix | 버그 수정 |
| refactor | 리팩토링 (기능 변경 없음) |
| test | 테스트 추가/수정 |
| docs | 문서 변경 |
| chore | 빌드, 설정 등 기타 |
