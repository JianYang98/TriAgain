# AGENTS.md - TriAgain Backend

## 역할

Java/Spring 백엔드를 구현하고, 헥사고날 경계·도메인 규칙·테스트 가능성을 지킨다.
불확실한 요구사항은 추측하지 말고 정본 문서와 실제 코드를 확인한 뒤 질문한다.

## 질문과 실행의 경계

1. 질문과 실행 지시를 구분한다
- 내가 물어보면 답한다. "어때?", "어떻게 해야 해?", "할 수 있어?", "왜 그래?", "차이가 뭐야?", "어떻게 생각해?" 등 방법·가능성·의견·현황을 묻는 질문은 실행 지시로 해석하지 않는다.
- 질문에 필요한 조사·분석·정적 검증·격리된 로컬 테스트는 자율적으로 진행한다. 검증 과정의 임시 산출물은 허용하지만, 기존 프로젝트 파일·설정·데이터를 변경하거나 서버·컨테이너의 실행 상태에 영향을 주는 작업은 명확한 실행 지시 없이 수행하지 않는다.
- 내가 시키면 한다. "수정해줘", "반영해줘", "진행해", "ㄱ" 등 명확한 실행 지시는 요청한 범위에서 수행한다. 질문 형태라도 명확한 작업 요청이라면 맥락을 고려한다.
- 질문과 지시가 섞이면 의도를 구분한다. 질문의 답에 따라 실행 여부가 달라지거나 실행 의도가 불명확하면, 먼저 질문에 답하고 변경 전에 확인한다.
- 대화의 맥락을 함께 판단한다. 현재 발화뿐 아니라 직전 대화의 합의 사항과 작업 대상을 참고한다. "좋아", "웅웅", "문제없지?" 등의 반응만으로 실행을 승인했다고 단정하지 않는다.
- 불필요한 확인 질문이나 반복 승인은 하지 않는다. 실행 대상과 범위가 이미 명확하게 승인되었다면, 그 범위 안의 후속 작업에 반복 승인을 요구하지 않는다.

이유: 나는 질문하면서 생각을 정리한다. 그 과정에서 요청하지 않은 수정이나 구현이 발생하면 사고 흐름이 끊긴다.

## 정본과 참조

- 비즈니스 규칙: `docs/spec/biz-logic.md`
- API 계약: `docs/spec/api-spec.md` 및 `docs/spec/api-spec/`
- DB 스키마: `docs/spec/schema.md`
- 아키텍처: `docs/spec/architecture.md`
- 과거 결함: `docs/log/debugging-log.md`

도메인·API·테스트 변경 전에는 해당 정본 문서와 기존 구현을 함께 읽는다.
`.claude/rules/`와 `.claude/skills/`는 Codex의 자동 skill이 아니라 프로젝트 참조 문서다.
작업 유형에 맞는 문서를 직접 읽고 적용한다.

## 구현 규칙

- 도메인이 인프라에 의존하지 않도록 Port/Adapter 경계를 지킨다.
- Phase A(문서 확인) → Phase B(구현) → Phase C(테스트) 순으로 진행한다.
- Phase A 결과를 사용자에게 보여준다. 구현 대상과 범위가 이미 명확하게 승인되었다면 Phase B로 진행하고, 승인 범위를 벗어나는 변경이 필요하면 확인한다.
- 문서에 없는 결정이 필요하면 구현을 멈추고 문서를 먼저 갱신한다.

## 작업 유형별 필독 문서

| 작업 | 읽을 파일 |
|---|---|
| 새 API·도메인 | `.claude/skills/new-api.md`, `.claude/skills/new-domain.md` |
| Java 구현 전반 | `.claude/rules/coding-convention.md`, `.claude/rules/anti-patterns.md`, `.claude/rules/lessons-learned.md` |
| DB 마이그레이션·스키마 | `.claude/rules/db-migration.md` |
| 설정·배포 | `.claude/rules/config-deploy.md` |
| 테스트 | `.claude/rules/test-strategy.md`, `.claude/skills/write-test.md` |
| 커밋·브랜치·워크트리 | `.claude/rules/git-convention.md` |
| 디버깅·추후 고려 기록 | `.claude/rules/logging-rules.md` |

## 완료 조건

- `.java` 변경 후 `./gradlew checkstyleMain checkstyleTest`를 실행한다.
- Checkstyle 정본은 `config/checkstyle/triagain-checkstyle-rules.xml`과 suppressions 파일이다.
- import 순서 위반은 `scripts/reorder-imports.py`로 일괄 정렬한다.
- `./gradlew compileJava compileTestJava -x test`를 실행한다.
- 관련 테스트와 `./gradlew test`를 실행한다.
- Claude Code hook은 Codex에서 자동 실행된다고 가정하지 않는다.

## Git

- `develop`에서 브랜치를 만들고 `develop`을 대상으로 PR을 연다.
- 커밋 전 `git status --short`로 변경 범위를 확인하고, 관련 파일만 stage한다.
- 커밋 형식과 워크트리 절차의 정본은 `.claude/rules/git-convention.md`다.
