# new-api.md — API 추가/수정 Skill

> **트리거**: 새 API 엔드포인트 추가, 기존 API 수정(요청/응답 구조 변경, 경로 변경, 에러코드 추가) 시
> **이 파일을 읽지 않고 API를 추가/수정하는 것은 규칙 위반이다.**

---

## 0. 원칙

- **api-spec.md가 정본이다.** 코드보다 문서를 먼저 수정한다.
- **api-spec.md는 도메인별로 분할됐다.** `docs/spec/api-spec.md`는 인덱스, 실제 명세는 `docs/spec/api-spec/{도메인}.md`(auth-user·crew·verification·notification·habit·internal). 이 문서에서 "api-spec.md"는 해당 도메인 파일을 가리킨다 — 인덱스에서 도메인을 찾아 그 파일에 추가/수정한다.
- **맞는 도메인 파일이 없으면(새 바운디드 컨텍스트) 임의로 파일을 만들지 않는다.** 먼저 사용자에게 "새 도메인 파일 `api-spec/{컨텍스트}.md`를 만들지" 확인받는다. 승인 후에만 새 파일 생성 + 라우터(api-spec.md) 인덱스에 행 추가. 엔드포인트 본문을 라우터에 넣지 않는다. (예: Moderation 신고, Support 반응 — 라우터 TODO 참조)
- API 변경은 "밖에서 안으로" 설계한다: 스펙 → Controller → Service → Domain
- 구현 전 변경 계획을 사용자에게 보고한다.
- **계획 승인 전까지 코드를 수정하지 않는다.**

---

## 1. 새 API 추가 작업 순서

### Step 1: 문서 먼저

```
1. docs/spec/api-spec/{도메인}.md 에 엔드포인트 추가 (인덱스 docs/spec/api-spec.md 에서 도메인 파일 확인)
   - HTTP Method + Path
   - Request Body / Query Params
   - Response Body (성공 + 에러)
   - 에러코드 정의
   - 인증 필요 여부

2. docs/spec/biz-logic.md에 비즈니스 규칙 추가 (필요 시)

3. docs/spec/schema.md 변경 필요 여부 확인
```

### Step 2: 변경 계획 보고

코드 작성 전에 아래 형식으로 사용자에게 보고한다.

```
## API 추가 계획

### 엔드포인트
- [METHOD] [PATH]
- 인증: 필요/불필요
- 설명: [한 줄 설명]

### 생성/수정할 파일
- [ ] Controller: [파일 경로]
- [ ] Request DTO: [파일 경로]
- [ ] Response DTO: [파일 경로]
- [ ] Service: [파일 경로] — [새 메서드 or 기존 메서드 수정]
- [ ] Repository: [필요 시]
- [ ] Entity: [필요 시]

### 연관 변경
- [ ] SecurityConfig — permitAll 추가 필요 여부
- [ ] 기존 API에 영향 여부
- [ ] docs/spec/ 업데이트 내용
```

### Step 3: 구현 (승인 후)

```
1. Request DTO 생성
   - @Valid 검증 어노테이션
   - api-spec.md와 필드명/타입 일치 확인

2. Response DTO 생성 (또는 기존 DTO 수정)
   - api-spec.md와 필드명/타입 일치 확인

3. Controller 메서드 추가
   - @AuthenticatedUser 필요 여부
   - HTTP Status Code가 api-spec.md와 일치하는지

4. Service 메서드 추가/수정                   ← 빠뜨리지 마라
   - 비즈니스 규칙 적용
   - 트랜잭션 범위 확인

5. Repository 메서드 추가 (필요 시)

6. 예외 처리
   - 비즈니스 예외 클래스 추가
   - 에러코드가 api-spec.md와 일치하는지

7. SecurityConfig 수정 (필요 시)
   - permitAll 추가 or 인증 필요 확인

8. 테스트 → .claude/skills/write-test.md 참조
```

---

## 2. 기존 API 수정 작업 순서

### 2-1. 요청/응답 구조 변경

```
1. api-spec.md 먼저 수정 (정본)
2. Request/Response DTO 수정
3. Controller 파라미터 변경
4. Service — 변경된 필드를 사용하는 로직 확인    ← 여기가 빠진다
5. Entity/Repository 수정 (필요 시)
6. 기존 테스트 수정 → .claude/skills/write-test.md 참조
```

### 2-2. 에러코드 추가

```
1. api-spec.md에 에러 응답 추가
2. 예외 클래스 추가/수정
3. Service에서 해당 예외를 던지는 로직 추가
4. GlobalExceptionHandler 확인
5. Cucumber 에러 시나리오 추가 → .claude/skills/write-test.md 참조
```

### 2-3. API 경로/메서드 변경

```
1. api-spec.md 수정
2. Controller @RequestMapping 수정
3. SecurityConfig — 경로 기반 설정 확인
4. 프론트엔드 영향 보고 (오케스트레이션 에이전트에게)
5. 테스트의 API 경로 전부 수정
6. TestAdapter 경로 수정
```

---

## 3. API 설계 체크리스트

새 API를 설계할 때 아래를 확인한다.

### 네이밍

| 항목 | 규칙 |
|------|------|
| 경로 | 복수형 명사 (`/api/crews`, `/api/challenges`) |
| Method | CRUD 매핑: POST(생성), GET(조회), PATCH(수정), DELETE(삭제) |
| 필드명 | camelCase, api-spec.md 기준 |

### 인증/인가

```
확인사항:
- 이 API는 로그인 필수인가?
- @AuthenticatedUser로 userId를 받는가?
- 리소스 소유자 검증이 필요한가? (크루장만 수정 가능 등)
- SecurityConfig의 permitAll 목록에 추가해야 하는가?
```

### 응답 구조

```
확인사항:
- 성공 응답 HTTP Status (200, 201, 204 중 적절한 것)
- 에러 응답이 GlobalExceptionHandler와 일치하는가
- null 가능 필드가 명시되어 있는가
- 리스트 응답 시 빈 배열 [] 반환 (null 아님)
```

---

## 4. docs/spec/ ↔ 코드 일치 확인

API 작업 완료 후 반드시 확인한다.

```
[ ] api-spec.md의 경로 = Controller @RequestMapping
[ ] api-spec.md의 요청 필드 = Request DTO 필드
[ ] api-spec.md의 응답 필드 = Response DTO 필드
[ ] api-spec.md의 에러코드 = 실제 던지는 예외
[ ] api-spec.md의 인증 여부 = SecurityConfig 설정
[ ] biz-logic.md의 규칙 = Service 검증 로직
```

---

## 5. 금지 사항

- ❌ api-spec.md 수정 없이 Controller부터 만들지 않는다
- ❌ 계획 보고 없이 구현을 시작하지 않는다
- ❌ Controller만 만들고 Service 로직을 비워두지 않는다
- ❌ 에러 응답을 api-spec.md에 정의하지 않고 코드에만 넣지 않는다
- ❌ SecurityConfig 확인 없이 인증 관련 API를 추가하지 않는다
- ❌ 프론트엔드 영향이 있는 변경을 보고 없이 하지 않는다
