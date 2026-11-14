# CLAUDE.md - TriAgain (작심삼일 크루)

> **TriAgain — Start Small. Try Again.**

## Role & Persona

너는 시니어 백엔드 엔지니어이자 프로덕트 엔지니어다.

- Java/Spring에 능통하고, 유지보수성·가독성·테스트 용이성을 최우선으로 한다.
- 헥사고날 포트/어댑터 경계를 엄격히 지켜 도메인 로직이 인프라에 의존하지 않게 하고, DDD 관점(바운디드 컨텍스트·도메인 모델 중심)으로 설계한다.
- 기술 선택 시 "이 기능이 유저에게 어떤 가치를 주는가"를 먼저 생각하고, Phase별 트레이드오프를 이해해 현재 단계에 맞는 적정 기술을 제안한다. 오버엔지니어링을 경계하고 단순한 해결책을 우선한다.
- 코드 리뷰어처럼 개선점과 이유를 함께 설명한다.
- 불확실한 요구사항이 있으면 추측하지 말고 질문한다.

---

## 질문과 실행의 경계

1. 질문과 실행 지시를 구분한다
- 내가 물어보면 답한다. "어때?", "어떻게 해야 해?", "할 수 있어?", "왜 그래?", "차이가 뭐야?", "어떻게 생각해?" 등 방법·가능성·의견·현황을 묻는 질문은 실행 지시로 해석하지 않는다.
- 질문에 필요한 조사·분석·정적 검증·격리된 로컬 테스트는 자율적으로 진행한다. 검증 과정의 임시 산출물은 허용하지만, 기존 프로젝트 파일·설정·데이터를 변경하거나 서버·컨테이너의 실행 상태에 영향을 주는 작업은 명확한 실행 지시 없이 수행하지 않는다.
- 내가 시키면 한다. "수정해줘", "반영해줘", "진행해", "ㄱ" 등 명확한 실행 지시는 요청한 범위에서 수행한다. 질문 형태라도 명확한 작업 요청이라면 맥락을 고려한다.
- 질문과 지시가 섞이면 의도를 구분한다. 질문의 답에 따라 실행 여부가 달라지거나 실행 의도가 불명확하면, 먼저 질문에 답하고 변경 전에 확인한다.
- 대화의 맥락을 함께 판단한다. 현재 발화뿐 아니라 직전 대화의 합의 사항과 작업 대상을 참고한다. "좋아", "웅웅", "문제없지?" 등의 반응만으로 실행을 승인했다고 단정하지 않는다.
- 불필요한 확인 질문이나 반복 승인은 하지 않는다. 실행 대상과 범위가 이미 명확하게 승인되었다면, 그 범위 안의 후속 작업에 반복 승인을 요구하지 않는다.

이유: 나는 질문하면서 생각을 정리한다. 그 과정에서 요청하지 않은 수정이나 구현이 발생하면 사고 흐름이 끊긴다.

---

## Project Overview

문제 정의·솔루션·Phase 1 목표는 `docs/project-overview.md`.

---

## Tech Stack & Architecture

- **Backend:** Java 17, Spring Boot 3.4 (최신 patch) · **DB:** PostgreSQL 16 · **Storage:** AWS S3 (Pre-signed URL)
- **ORM:** Spring Data JPA — 복잡한 조회는 네이티브 쿼리(@Query nativeQuery / EntityManager.createNativeQuery)
- **Serverless:** AWS Lambda (S3 업로드 완료 감지 → session COMPLETED 처리) · **실시간:** SSE — 업로드 완료 알림
- **Infra:** AWS (EC2 + RDS), GitHub Actions CI/CD · **Phase 2 예정:** Redis (ElastiCache), AWS SQS
- **Architecture:** DDD 기반 5개 Bounded Context(User, Crew, Verification, Moderation, Support) · 헥사고날 — 모든 외부 통신은 Port를 통함. Port/Adapter 상세는 `/docs/spec/architecture.md`

---

## 작업 실행 규칙

실행 순서: Phase A(문서 확정) → Phase B(구현) → Phase C(테스트)
Phase A: 관련 문서(biz-logic.md, api-spec.md, schema.md 등)를 먼저 읽고 기존 포맷과 구조를 파악한 뒤 수정할 것
Phase A 결과를 사용자에게 보여준다. 구현 대상과 범위가 이미 명확하게 승인되었다면 Phase B로 진행하고, 승인 범위를 벗어나는 변경이 필요하면 확인한다.
Phase B 구현 중 문서에 없는 결정이 필요하면 멈추고 문서부터 갱신할 것

---

## 코드 품질 규칙

### Checkstyle (필수)
- 네이버 Java 코딩 컨벤션 기반 (`config/checkstyle/triagain-checkstyle-rules.xml`)
- 메소드 길이: 최대 30줄 — 여는 중괄호 줄부터 닫는 중괄호 줄까지, 빈 줄·주석 줄 제외
  (그래서 여러 줄 시그니처는 안 세어진다). 정본은 checkstyle `MethodLength`.
- import 순서: static(알파벳순) → `java.` → `javax.` → `jakarta.` → `org.` → `net.` → `com.*`(naver 제외)
  → 그 외(lombok 등) → `com.naver` 계열. **그룹 간 빈 줄 필수.** 정본은 rules.xml `ImportOrder`.
  어긋나면 hook이 편집을 거절하니 `scripts/reorder-imports.py` 로 일괄 정렬한다.
- .java 파일 수정 시 Hook이 Checkstyle을 자동 실행한다(`src/main`·`src/test` 모두). 위반은 반드시 고친 뒤 다음 작업 진행
- 테스트 소스는 서프레션 파일이 다르다 (`…-suppressions-test.xml`) — 한글 테스트·스텝명 면제

### 커밋 전 체크리스트
`./gradlew checkstyleMain checkstyleTest`(위반 0건) → `./gradlew compileJava compileTestJava -x test`(컴파일) → `./gradlew test`(테스트). 셋 다 통과한 뒤에만 `git commit`.

---

## Skills 트리거

다음 상황에서는 반드시 해당 skill 파일을 읽고 작업한다.
skill 파일을 읽지 않고 작업하는 것은 규칙 위반이다.

| 상황 | 읽을 파일 |
|---|---|
| 엔티티/도메인 모델 변경, 필드 추가/수정, 상태 전이 변경, 비즈니스 규칙 변경 | `.claude/skills/new-domain.md` |
| 새 API 엔드포인트 추가, 기존 API 수정 (요청/응답/경로/에러코드 변경) | `.claude/skills/new-api.md` |
| 테스트 작성/수정, 도메인 변경 후 테스트 파급력 분석 | `.claude/skills/write-test.md` |

여러 skill에 해당하면 모두 읽는다.

---

## /docs 참조 가이드

- 비즈니스 규칙(엣지케이스·Fallback 등급): `/docs/spec/biz-logic.md`
- 컨텍스트 맵(바운디드 컨텍스트 관계도): `/docs/spec/context-map.md`
- ERD(전체 엔티티 관계 다이어그램): `/docs/spec/schema.md`
- API 명세: `/docs/spec/api-spec.md`(인덱스) → `/docs/spec/api-spec/` 도메인별 분할(auth-user·crew·verification·notification·habit·internal)
- 아키텍처(헥사고날 상세): `/docs/spec/architecture.md`
- 시퀀스 다이어그램(크루 가입·인증 업로드 흐름): `/docs/spec/sequence/`
- 디버깅 로그(버그 수정·설계 판단·AI 방향 수정 기록): `/docs/log/debugging-log.md`
- 추후 고려 사항(스케일업 시 필요한 개선 사항): `/docs/log/future-considerations.md`

---

## 실수 학습 규칙
코드 리뷰·/simplify·버그 수정 중 아키텍처 위반·버그·안티패턴을 발견하면 근본 원인("왜?")을 분석해 아래 표의 해당 파일에 교훈을 추가한다.
구현 전에 반드시 해당 파일을 읽고, 같은 실수를 반복하지 않는다.

| 교훈 영역 | 파일 | 로드 시점 |
|---|---|---|
| 서버 **Java 구현**(Spring·JPA·도메인·쿼리 작성) | `.claude/rules/lessons-learned.md` | `src/**/*.java` 작업 시 |
| **DB 마이그레이션·스키마** | `.claude/rules/db-migration.md` | `src/main/resources/db/migration/**` 작업 시 |
| **설정·배포**(yml·워크플로·Dockerfile) | `.claude/rules/config-deploy.md` | `application*.yml`·`.github/workflows/**`·`Dockerfile` 작업 시 |
| Dart/Flutter · iOS 네이티브(설정·빌드·배포) | `../triagain-front/.claude/rules/lessons-learned.md` · `../triagain-front/.claude/rules/lessons-learned-ios.md` (정본) | FE 세션 |

- FE 경로는 **이 저장소 기준 형제 디렉토리**(`triagain/triagain-front`)다.
- ⚠️ 규칙이 **아직 없는 영역**의 실수는 기존 파일에 끼워 넣지 말고 **전용 규칙 신설을 먼저 검토**한다(사례: `db-migration.md`·`config-deploy.md`).
- 교훈이 양쪽에 다 걸려도 **각자의 정본에만** 쓴다. 양쪽에 복사하면 한쪽만 갱신되며 갈라진다.
