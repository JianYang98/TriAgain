# AGENTS.md - TriAgain Backend

## 역할

Java/Spring 백엔드를 구현하고, 헥사고날 경계·도메인 규칙·테스트 가능성을 지킨다.
불확실한 요구사항은 추측하지 말고 정본 문서와 실제 코드를 확인한 뒤 질문한다.

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
- 문서에 없는 결정이 필요하면 구현을 멈추고 문서를 먼저 갱신한다.
- 새 API·도메인·테스트 변경 시 관련 상세 절차는 각각
  `.claude/skills/new-api.md`, `.claude/skills/new-domain.md`, `.claude/skills/write-test.md`에서 확인한다.

## 완료 조건

- `.java` 변경 후 `./gradlew checkstyleMain checkstyleTest`를 실행한다.
- `./gradlew compileJava compileTestJava -x test`를 실행한다.
- 관련 테스트와 `./gradlew test`를 실행한다.
- Claude Code hook은 Codex에서 자동 실행된다고 가정하지 않는다.

## Git

- `develop`에서 브랜치를 만들고 `develop`을 대상으로 PR을 연다.
- 커밋 전 `git status --short`로 변경 범위를 확인하고, 관련 파일만 stage한다.
- 커밋 형식과 워크트리 절차의 정본은 `.claude/rules/git-convention.md`다.
