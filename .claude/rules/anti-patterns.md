---
description: 코드 작성 시 금지 사항 (OOP, 아키텍처, Data Access, Common Pitfalls)
paths: "src/**/*.java"
---

# Anti-Patterns (금지 사항)

## OOP & Clean Code

- `@Autowired` 필드 주입 금지
- Entity를 Controller에서 직접 반환 금지
- `throw new RuntimeException()` 금지
- Lombok `@Data` 사용 금지 → record 사용
- 메서드 20라인 초과 시 분리 고려 (권고. 강제선은 checkstyle 30줄)

## Architecture

- Controller에 비즈니스 로직 금지 → UseCase에 위임
- Domain 계층에서 JPA, HTTP 등 인프라 기술 의존 금지
- Port 인터페이스 없이 Adapter 직접 참조 금지
- 트랜잭션 안에 외부 API 호출 금지 (S3 등)

## Data Access

- N+1 문제 주의 → Fetch Join 또는 Batch Size 설정
- 복잡한 조회는 MyBatis 사용, 단순 CRUD는 JPA
- DB 컬럼 타입/길이 변경 시, 반드시 3곳을 한 세트로 수정한다: ① schema.md (정본 문서) ② Flyway 마이그레이션 (V{N}__.sql) ③ JPA 엔티티 (@Column length/type). PK/FK 변경 시 해당 컬럼을 FK로 참조하는 테이블도 전부 찾아서 3곳 모두 일괄 수정한다 (V5에서 users.id만 확장하고 FK 9곳 + JPA 엔티티 누락한 사례)

## Common Pitfalls

- Pre-signed URL 생성은 S3 통신이 아님 (내부 서명 생성)
- upload_session COMPLETED 처리는 Lambda → /internal API에서 수행 (트랜잭션 분리)
- /verifications는 session이 COMPLETED인지 확인만 하고 인증 생성에 집중
- SSE 타임아웃 60초, 클라이언트는 fallback으로 폴링 대비 필요
