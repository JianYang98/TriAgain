---
description: Git 브랜치 전략과 커밋 메시지 컨벤션 (AngularJS Convention, 한국어)
---

# Git Convention

## 브랜치 전략

**저장소 3개, 전략이 다르다. 커밋 전에 어디인지 확인한다.**

```bash
git remote -v | head -1        # 어느 저장소인가
git branch --show-current
```

| 저장소 | remote | 허용 |
|---|---|---|
| `triagain` (루트, 오케스트레이션) | `TriAgain-orchestration` (private) | **`main` 직커밋 OK** — 문서만이라 CI·리뷰어가 없다 |
| `triagain-back` | `TriAgain` | **PR만.** `develop`·`main` 직커밋 금지 |
| `triagain-front` | `TriAgain-front` | **PR만.** 〃 |

⛔ **루트가 예외인 것이지 직커밋이 기본이 아니다.** 2026-08-13 실제로 섞였다 —
orchestrator가 BE `develop`에 직접 커밋했고 `branch -f`로 되돌렸다.

### BE · FE 브랜치

- main: 운영 배포 브랜치 (직접 push 금지, develop에서 PR로만 병합)
- develop: 통합 브랜치 (작업 브랜치→develop PR, CI + E2E 통과 필수)
- BE 배포: main push는 백엔드를 자동 배포한다. develop은 자동 배포하지 않고, 필요할 때 `workflow_dispatch`로 수동 운영 배포한다. 두 경로 모두 같은 운영 EC2의 `triagain` 컨테이너를 교체한다. FE 배포 정책을 변경하는 규칙은 아니다.
- feat/*: 기능 개발 브랜치 (develop에서 분기, develop으로 PR)
- fix/*: 버그 수정 브랜치 (develop에서 분기, develop으로 PR)
- docs/*: 문서 변경 브랜치 (develop에서 분기, develop으로 PR)
- refactor/*: 외부 동작을 유지하는 코드 정리 브랜치 (develop에서 분기, develop으로 PR)
- lab/*: 실험·측정 브랜치. 채택 여부를 판단하는 용도.
  - 사용자가 기준 브랜치나 커밋을 지정했으면 그 기준에서 시작한다.
  - 기준을 지정하지 않았으면 브랜치나 워크트리를 만들기 전에 추천 기준과 이유를 보여 주고 사용자에게 확인한다.
    여러 기준이 타당하면 각 후보에서 무엇을 실험할 수 있는지 설명한다. 사용자가 기준을 확정한 뒤 `--no-track`으로 로컬 브랜치를 만든다.
  - 예) 조기 거절이 DB 부하를 줄이는지 비교 → `lab/early-reject`, 도입 결정·구현 → `feat/early-reject`, 기존 오류 수정 → `fix/early-reject`
  - `push -u` 의무·세션 마감 미push 경고에서 제외. push는 공유하거나 PR을 열 때만 (공개 저장소라 비공개 값 확인)
  - 끝나면 성공·실패와 관계없이 조건·핵심 결과를 짧게 적어 로컬 커밋. 워크트리 지우기 전에 다시 필요한 미추적 원본만 저장소 밖에 보관
  - 반영: 일부 채택 → develop에서 새 `feat/*`·`fix/*`로 필요한 변경만 PR / 전체 채택 → `lab/*`에서 develop으로 직접 PR. 리뷰·CI·테스트·Tier는 기존 기준

## PR 본문

- BE·FE 일반 PR 본문은 **왜 → 변경 내용 → 확인한 것** 순서로 쓴다.
- 문구·오타·지침 한 줄처럼 diff만으로 판단할 수 있는 PR은 **왜 → 변경 내용**으로 충분하다.
  리뷰 판단에 영향을 주는 미실행 검증이나 머지 전 확인 사항은 본문에 남긴다.

## 워크트리 (worktree)

**언제 쓰나**: 메인 체크아웃이 다른 브랜치에 있거나 미커밋 변경이 붙어 있을 때.
그 트리에서 브랜치를 바꾸지 말고 워크트리를 판다.

### 이름과 생성 전 확인

- 일반 작업 브랜치는 최신 `origin/develop`에서 시작한다. PR 대상이 따로 정해졌으면 해당 원격 브랜치를 받아 와서 그 커밋에서 시작한다.
  `lab/*`는 실험마다 합의한 시작점을 사용한다.
- 브랜치는 `<유형>/<작업명>`, 워크트리는 BE `.worktrees/be-<작업명>`, FE `.worktrees/fe-<작업명>`으로 짓는다.
  같은 작업명을 쓰고, 작업자나 에이전트 이름 대신 바꾸는 대상과 핵심 변경을 적는다.
  예: `feat/k6-prepare-print-commands` ↔ `.worktrees/be-k6-prepare-print-commands`.
- 사용자가 이름을 지정했거나 작업 대상·핵심 변경·유형이 명확하면 그 이름으로 진행한다.
- 이름을 정하려면 작업 유형이나 범위를 먼저 결정해야 하거나, 관련 내용을 확인해도 작업명을 하나로 좁히기 어려우면 생성 전에 후보 2~3개를 보여 준다.
  추천안을 먼저 쓰고 각 후보의 브랜치 이름·워크트리 경로·의미를 한 줄씩 적는다. 사용자가 고른 뒤 생성한다.

### 생성 명령

아래는 일반 작업 예시다. `lab/*`는 `git worktree add --no-track -b <lab/브랜치> <경로> <합의한 기준>`으로 만든다.

```bash
git fetch origin develop                # ← origin/develop 이 낡으면 낡은 커밋에서 갈라진다
git worktree add <경로> origin/develop -b <브랜치>
git -C <경로> push -u origin <브랜치>   # ← 생략 금지(이유는 아래). 에이전트가 직접 실행한다 (lab/* 는 예외)
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
**BE만** `--exclude='lab/*'`를 더한다(lab/*는 로컬 작업이 기본).
비어 있지 않으면 push 안 된 커밋이 있다. **3개 저장소를 각각** 본다 — 루트는 2026-08-13에
remote가 붙어 **예외가 사라졌다**(그전 문구는 "루트는 예외"였다).
이 점검은 `/session-pack` 4번째 단계로 배선돼 있다.

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
