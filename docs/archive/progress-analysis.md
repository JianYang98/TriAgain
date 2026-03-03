# TriAgain 프로젝트 총체적 분석 보고서

> 분석 시점: 2026-02-22 (Task 3 완료, Task 4 착수 전)

---

## 1. 프로젝트 현황 요약

| 항목 | 상태 |
|------|------|
| **구현 단계** | Walking Skeleton 완료 (Task 3/7) |
| **실제 구현 클래스** | 16개 (+ package-info 38개) |
| **설정 파일** | 10개 |
| **문서** | 8개 (~60KB) |
| **Git 커밋** | 3개 |
| **테스트** | 2종 (단위 H2 + 인수 Testcontainers PostgreSQL) |
| **빌드 상태** | BUILD SUCCESSFUL |

### Git 이력

```
7ee5753 feat: Walking Skeleton 및 Cucumber 인수 테스트 프레임 구축
668eff1 chore: Spring Boot 프로젝트 초기 설정
bc48920 docs: 프로젝트 문서 초기 커밋
```

---

## 2. 아키텍처 분석

### 2.1 헥사고날 아키텍처 준수도

5개 Bounded Context 모두 동일한 헥사고날 패키지 구조를 갖추고 있다.

```
{context}/
├── api/            ← Controller, Request/Response DTO
├── application/    ← UseCase 구현체
├── domain/
│   ├── model/      ← Entity, Aggregate Root
│   └── vo/         ← Value Object
├── port/
│   ├── in/         ← UseCase 인터페이스
│   └── out/        ← Repository Port, External Port
└── infra/          ← JPA, MyBatis, S3 Adapter
```

| 원칙 | 준수 여부 | 근거 |
|------|-----------|------|
| Domain이 인프라에 의존하지 않음 | ✅ | domain 패키지에 JPA/Spring 어노테이션 없음 (아직 미구현이지만 구조적으로 보장) |
| Port를 통한 외부 통신 | ✅ | port/in, port/out 패키지 분리 완료 |
| Controller → UseCase만 의존 | ✅ | HealthController가 DataSource를 직접 참조하는 점은 common 인프라이므로 예외 |
| Adapter가 Port 구현 | ✅ | 패키지 구조로 강제됨 |

### 2.2 Bounded Context 구성

| Context | 역할 | 구현 상태 |
|---------|------|-----------|
| **User** | 회원/인증 | 스켈레톤 (패키지만) |
| **Crew** (Core) | 크루, 챌린지 핵심 로직 | 스켈레톤 |
| **Verification** | 인증, 업로드 세션 | 스켈레톤 |
| **Moderation** | 신고, 검토 | 스켈레톤 |
| **Support** | 알림, 반응 | 스켈레톤 |
| **Common** | 공통 인프라 | ✅ 구현 완료 |

---

## 3. 구현 완료된 컴포넌트 상세

### 3.1 Common 모듈 (9개 클래스)

#### 응답 체계

| 클래스 | 타입 | 역할 |
|--------|------|------|
| `ApiResponse<T>` | record | 전체 API 공통 응답 래퍼. `ok(data)`, `fail(errorCode)` 팩토리 메서드 |
| `ErrorResponse` | record | 에러 응답 내부 DTO. `code` + `message` |
| `ErrorCode` | enum | HTTP 상태 + 비즈니스 코드 + 메시지 매핑 |

현재 정의된 ErrorCode:

| 코드 | HTTP | 구분 |
|------|------|------|
| C001 INVALID_INPUT | 400 | Common |
| C002 INTERNAL_SERVER_ERROR | 500 | Common |
| C003 RESOURCE_NOT_FOUND | 404 | Common |
| U001 USER_NOT_FOUND | 404 | User |
| CR001 CREW_NOT_FOUND | 404 | Crew |
| CR002 CREW_FULL | 400 | Crew |
| V001 VERIFICATION_DEADLINE_EXCEEDED | 400 | Verification |
| M001 REPORT_NOT_FOUND | 404 | Moderation |

#### 예외 처리

| 클래스 | 역할 |
|--------|------|
| `BusinessException` | 커스텀 예외 기반 클래스. ErrorCode 포함 |
| `GlobalExceptionHandler` | `@RestControllerAdvice`. 3단계 핸들링 |

GlobalExceptionHandler 처리 흐름:
1. `BusinessException` → ErrorCode 기반 구조화된 에러 응답
2. `MethodArgumentNotValidException` → 필드별 검증 에러 집계
3. `Exception` (catch-all) → 로깅 + 500 응답

#### 설정

| 클래스 | 역할 |
|--------|------|
| `MyBatisConfig` | 5개 컨텍스트 infra 패키지 `@MapperScan` |

#### 헬스체크

| 클래스 | 역할 |
|--------|------|
| `HealthController` | `GET /health`. DataSource 연결 검증 |
| `HealthResponse` | record. `status` + `database` 필드 |

### 3.2 테스트 인프라 (7개 클래스 + 1 feature)

#### 인수 테스트 프레임워크

```
CucumberTest (Suite 러너)
  └── CucumberSpringContext (@SpringBootTest + Testcontainers 연결)
      └── TestContainers (static PostgreSQL 16 컨테이너)

BaseTestAdapter (REST-Assured 추상화)
  └── HealthTestAdapter (getHealth() 메서드)

HealthSteps (한국어 BDD Step Definitions)
  └── health.feature (한국어 Gherkin 시나리오)
```

#### 테스트 전략 이중화

| 테스트 | 프로필 | DB | 목적 |
|--------|--------|-----|------|
| `TriAgainApplicationTests` | test | H2 in-memory | 컨텍스트 로딩 검증 |
| `CucumberTest` | integration | Testcontainers PostgreSQL | 인수 테스트 (실제 DB) |

둘 다 `./gradlew test` 한 번으로 실행된다 (JUnit Platform이 Jupiter + Cucumber 엔진 자동 감지).

#### TestAdapter 설계

`BaseTestAdapter`가 REST-Assured를 래핑하여 HTTP 메서드(`get`, `post`, `put`, `delete`)를 제공한다. 도메인별 어댑터가 이를 상속하여 API 호출을 캡슐화한다.

```
BaseTestAdapter (port, baseUrl, HTTP 메서드)
  ├── HealthTestAdapter: getHealth()
  ├── (향후) CrewTestAdapter: createCrew(), joinCrew()
  └── (향후) VerificationTestAdapter: createVerification()
```

이 구조의 장점:
- Step Definition이 HTTP 세부사항에 강결합되지 않음
- API 스펙 변경 시 Adapter만 수정하면 전체 Step이 동작
- 여러 Step에서 같은 API를 재사용 가능

---

## 4. 설정 파일 분석

### 4.1 프로필 구성

| 프로필 | 파일 | DB | ddl-auto | SQL 로깅 | 용도 |
|--------|------|----|----------|----------|------|
| (base) | application.yml | - | - | - | 공통 설정 |
| local | application-local.yml | PostgreSQL localhost:5432 | update | DEBUG | 로컬 개발 |
| dev | application-dev.yml | PostgreSQL (환경변수) | validate | off | 개발 서버 |
| prod | application-prod.yml | PostgreSQL (환경변수) | validate | off | 운영 |
| test | application-test.yml | H2 in-memory | create-drop | on | 단위 테스트 |
| integration | application-integration.yml | Testcontainers PostgreSQL | create-drop | off | 인수 테스트 |

### 4.2 주요 설정 포인트

- `spring.jpa.open-in-view: false` — Lazy Loading 실수 방지, 성능 최적화
- `hibernate.default_batch_fetch_size: 100` — N+1 쿼리 방지
- `jackson.property-naming-strategy: LOWER_CAMEL_CASE` — JSON 필드 네이밍 통일
- MyBatis `mapUnderscoreToCamelCase: true` — DB snake_case ↔ Java camelCase 자동 변환

### 4.3 Docker Compose

PostgreSQL 16 Alpine 단일 서비스. `application-local.yml`과 접속 정보 일치 (5432, triagain/triagain/triagain). Named volume으로 데이터 영속.

---

## 5. 의존성 분석

### 5.1 프로덕션 의존성

| 라이브러리 | 버전 | 용도 |
|-----------|------|------|
| Spring Boot Starter Web | 3.4.13 | REST API |
| Spring Boot Starter Data JPA | 3.4.13 | ORM (단순 CRUD) |
| Spring Boot Starter Validation | 3.4.13 | Bean Validation |
| MyBatis Spring Boot Starter | 3.0.5 | 복잡한 조회 쿼리 |
| PostgreSQL Driver | (BOM) | DB 드라이버 |
| Lombok | (BOM) | 보일러플레이트 감소 |

### 5.2 테스트 의존성

| 라이브러리 | 버전 | 용도 |
|-----------|------|------|
| Spring Boot Starter Test | 3.4.13 | JUnit 5, Mockito, AssertJ |
| H2 | (BOM) | 단위 테스트용 in-memory DB |
| Cucumber Java | 7.17.0 | BDD 프레임워크 |
| Cucumber Spring | 7.17.0 | Cucumber-Spring 통합 |
| Cucumber JUnit Platform Engine | 7.17.0 | JUnit 5 통합 |
| JUnit Platform Suite | 1.10.2 | Suite 러너 |
| REST-Assured | 5.5.0 | HTTP API 테스트 |
| Testcontainers BOM | 1.21.3 | 컨테이너 관리 |
| Testcontainers PostgreSQL | (BOM) | PostgreSQL 컨테이너 |

---

## 6. 코딩 컨벤션 준수 현황

### 6.1 CLAUDE.md 규약 체크리스트

| 규약 | 상태 | 비고 |
|------|------|------|
| `@Autowired` 필드 주입 금지 | ✅ | `@RequiredArgsConstructor` + `private final` 사용 |
| Entity를 Controller에서 직접 반환 금지 | ✅ | DTO(record)로 변환 |
| `throw new RuntimeException()` 금지 | ✅ | BusinessException 사용 |
| Lombok `@Data` 금지 → record 사용 | ✅ | ApiResponse, ErrorResponse, HealthResponse 모두 record |
| Controller에 비즈니스 로직 금지 | ✅ | HealthController는 인프라 관심사로 예외 |
| Domain에서 인프라 의존 금지 | ✅ | domain 패키지에 인프라 import 없음 |
| Port 없이 Adapter 직접 참조 금지 | ✅ | 패키지 구조로 강제 |
| 트랜잭션 안에 외부 API 호출 금지 | ✅ | 해당 케이스 아직 없음 |
| 커밋 메시지 AngularJS Convention | ✅ | feat:, chore:, docs: 사용 |

### 6.2 개선 가능 사항

**Lombok 사용 범위**: `ErrorCode`와 `BusinessException`에서 `@Getter`, `@RequiredArgsConstructor` 사용 중. CLAUDE.md는 `@Data` 금지를 명시하지만 `@Getter`는 명시적으로 금지하지 않음. `@RequiredArgsConstructor`는 의존성 주입 패턴으로 권장됨. 현재 수준은 허용 범위.

---

## 7. 문서화 현황

| 문서 | 경로 | 내용 |
|------|------|------|
| CLAUDE.md | `/CLAUDE.md` | 프로젝트 개요, 아키텍처, 코딩 컨벤션, 금지 사항 |
| API 명세 | `/docs/spec/api-spec.md` | REST API 계약서 (upload-sessions, verifications 등) |
| 아키텍처 | `/docs/spec/architecture.md` | 헥사고날 아키텍처 상세, Mermaid 다이어그램 |
| 비즈니스 규칙 | `/docs/spec/biz-logic.md` | 3일 사이클, 인증 방식, 엣지케이스, NFR |
| 컨텍스트 맵 | `/docs/spec/context-map.md` | BC 간 관계 (Command/Event/API) |
| ERD | `/docs/spec/schema.md` | 엔티티 관계, Enum 정의, 인덱스 전략 |
| 크루 가입 시퀀스 | `/docs/spec/sequence/crew-join.md` | 멱등키 → 분산 락 → DB 비관적 락 3단계 |
| 인증 업로드 시퀀스 | `/docs/spec/sequence/verification.md` | Pre-signed URL → S3 → Verification 생성 흐름 |

문서 총량 약 60KB. Phase 1 구현에 필요한 비즈니스 규칙, API 스펙, ERD가 모두 정의되어 있어 도메인 구현 착수 가능.

---

## 8. 리스크 및 주의사항

### 8.1 기술적 리스크

| 리스크 | 심각도 | 현재 상태 | 대응 방안 |
|--------|--------|-----------|-----------|
| 스키마 마이그레이션 도구 부재 | 중 | Hibernate ddl-auto 의존 | Phase 2에서 Flyway 도입 |
| 동시성 제어 미구현 | 중 | 문서에만 설계됨 | 크루 가입, 인증 구현 시 SELECT FOR UPDATE 적용 |
| S3 연동 미구현 | 저 | StoragePort 설계만 완료 | Verification 구현 시 함께 진행 |
| MyBatis 매퍼 파일 없음 | 저 | 설정만 완료 | 복잡한 조회 쿼리 구현 시 추가 |

### 8.2 아키텍처 결정 사항 (ADR)

| 결정 | 이유 | 트레이드오프 |
|------|------|-------------|
| JPA + MyBatis 병행 | 단순 CRUD는 JPA, 복잡한 조회는 MyBatis | 학습 곡선 증가, 유지보수 포인트 2개 |
| Verification 3-way FK | user_id, crew_id, challenge_id 독립 참조 | 데이터 정합성은 애플리케이션 레벨에서 보장 |
| Pre-signed URL 방식 | 서버를 경유하지 않는 직접 업로드 | 클라이언트 복잡도 증가, 서버 부하 감소 |
| 3단계 동시성 제어 | 멱등키 → 분산 락 → DB 락 | Phase 1에서는 DB 락만 사용 (Redis 없음) |

---

## 9. Task 4 착수 전 준비 상태

### 9.1 인프라 준비도

| 항목 | 상태 | 비고 |
|------|------|------|
| Spring Boot 앱 기동 | ✅ | `./gradlew bootRun` |
| Docker PostgreSQL | ✅ | `docker-compose up -d` |
| Cucumber 인수 테스트 | ✅ | 한국어 BDD 시나리오 통과 |
| Testcontainers 연동 | ✅ | 자동 PostgreSQL 컨테이너 |
| 공통 응답/예외 체계 | ✅ | ApiResponse, BusinessException |
| TestAdapter 추상화 | ✅ | BaseTestAdapter → 도메인 어댑터 확장 가능 |
| 5개 BC 패키지 구조 | ✅ | 도메인 코드 배치 준비 완료 |

### 9.2 다음 단계에서 필요한 작업

Task 4부터 실제 도메인 로직 구현이 시작된다. 현재 스켈레톤 위에 다음을 채워나가면 된다:

1. **도메인 모델** — Entity, VO, Aggregate Root (`domain/model/`, `domain/vo/`)
2. **Port 인터페이스** — UseCase, Repository Port (`port/in/`, `port/out/`)
3. **서비스 구현** — UseCase 구현체 (`application/`)
4. **어댑터 구현** — JPA Entity, Repository, Mapper (`infra/`)
5. **API 계층** — Controller, Request/Response DTO (`api/`)
6. **인수 테스트** — Feature 파일 + Step + TestAdapter 확장

모든 문서(API 스펙, ERD, 비즈니스 규칙, 시퀀스)가 준비되어 있어 구현 착수 가능.

---

## 10. 종합 평가

| 영역 | 평가 | 점수 |
|------|------|------|
| 아키텍처 설계 | 헥사고날 + DDD 원칙 충실히 반영 | 9/10 |
| 코드 품질 | record 활용, 예외 체계, 컨벤션 준수 | 8/10 |
| 테스트 전략 | 이중화(H2 + Testcontainers), BDD 프레임 | 8/10 |
| 문서화 | API 스펙, ERD, 시퀀스, 비즈니스 규칙 완비 | 9/10 |
| 설정 관리 | 프로필별 분리, 환경변수 활용 | 9/10 |
| 구현 진척도 | 스켈레톤 완료, 도메인 미구현 | 3/10 |
| **종합** | **견고한 기반 위에 도메인 구현 준비 완료** | **7.7/10** |

프로젝트는 "올바른 기초 공사"를 마친 상태다. 아키텍처, 테스트 인프라, 문서가 잘 갖춰져 있어 도메인 구현에 집중할 수 있는 환경이 조성되었다.
