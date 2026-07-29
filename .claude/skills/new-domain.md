# new-domain.md — 도메인/엔티티 변경 Skill

> **트리거**: 엔티티 필드 추가/수정/삭제, 도메인 모델 변경, 상태 전이 변경, 비즈니스 규칙 변경 시
> **이 파일을 읽지 않고 도메인을 변경하는 것은 규칙 위반이다.**

---

## 0. 원칙

- **도메인 변경은 단독으로 끝나지 않는다.** 반드시 의존 체인 끝까지 따라간다.
- 변경 전 영향 범위를 먼저 분석하고, 체크리스트를 사용자에게 보고한다.
- **체크리스트 승인 전까지 코드를 수정하지 않는다.**
- 정본은 `docs/spec/` 문서다. 코드보다 문서를 먼저 수정한다.

---

## 1. 변경 체인 (Hexagonal 구조 기준)

도메인을 변경하면 아래 체인을 **위에서 아래로 순서대로** 확인한다.
하나라도 빠뜨리면 불일치가 생긴다.

```
docs/spec/ (정본 문서)
  ↓
Entity (도메인 모델)
  ↓
Repository (Port / Adapter)
  ↓
Service (Application Layer)        ← 여기가 제일 많이 빠진다
  ↓
DTO / Request / Response
  ↓
Controller (API Layer)
  ↓
Flyway Migration (DB)
  ↓
테스트 (.claude/skills/write-test.md 참조)
```

---

## 2. 변경 전 영향 분석 — 반드시 먼저 수행

코드를 건드리기 전에 아래 체크리스트를 채워서 사용자에게 보고한다.

```
## 도메인 변경 영향 분석

### 변경 내용
- 대상: [어떤 엔티티/도메인 모델]
- 변경: [무엇을 어떻게]

### 문서 수정 필요
- [ ] schema.md — 컬럼 추가/수정/삭제
- [ ] biz-logic.md — 비즈니스 규칙 추가/변경
- [ ] api-spec/{도메인}.md — 응답 구조 변경 (인덱스: api-spec.md)

### 코드 변경 체인
- [ ] Entity — 필드/메서드 추가/수정
- [ ] Repository — 새 쿼리 메서드 필요 여부
- [ ] Service — 해당 필드/로직을 사용하는 메서드 수정
- [ ] DTO/Response — 응답에 새 필드 포함 여부
- [ ] Request — 요청에 새 파라미터 포함 여부
- [ ] Controller — 파라미터 바인딩 변경 여부
- [ ] Flyway — 마이그레이션 스크립트 필요 여부
- [ ] 삭제/정리 경로 — 새 엔티티/테이블이 기존 엔티티를 참조하면, 부모의 삭제/정리 경로에 포함해야 하는지 확인하고 write-test.md 3-6 테스트를 갱신한다

### 해당 없음 (변경 불필요 확인)
- [해당 없는 항목과 그 이유]
```

---

## 3. 변경 유형별 작업 순서

### 3-1. 엔티티 필드 추가

가장 흔한 케이스. 체인 전체를 탄다.

```
1. docs/spec/schema.md에 컬럼 추가
2. docs/spec/api-spec/{도메인}.md에 응답/요청 필드 추가 (필요 시)
3. docs/spec/biz-logic.md에 규칙 추가 (필요 시)
4. Flyway 마이그레이션 스크립트 작성
5. Entity에 필드 + getter 추가
6. 해당 필드를 다루는 Service 메서드 수정     ← 빠뜨리지 마라
7. DTO/Response에 필드 추가
8. Request에 필드 추가 (입력받는 경우)
9. Controller 수정 (필요 시)
10. 테스트 → .claude/skills/write-test.md 참조
```

**Service 확인 포인트:**
- 생성 메서드: 새 필드를 세팅하고 있는가?
- 수정 메서드: 새 필드를 업데이트하고 있는가?
- 조회 메서드: DTO 변환 시 새 필드를 포함하고 있는가?

### 3-2. 엔티티 필드 수정 (타입/제약조건 변경)

```
1. docs/spec/schema.md 수정
2. docs/spec/biz-logic.md 수정 (검증 규칙 변경 시)
3. Flyway ALTER 마이그레이션
4. Entity 필드 타입/어노테이션 수정
5. Service — 해당 필드 사용하는 모든 메서드 확인
6. DTO/Request/Response 타입 일치 확인
7. Controller — @Valid 등 검증 어노테이션 확인
8. 테스트 → .claude/skills/write-test.md 참조
```

### 3-3. 비즈니스 규칙 추가/변경

```
1. docs/spec/biz-logic.md 먼저 수정 (정본)
2. 도메인 모델에 규칙 메서드 추가/수정
3. Service에서 규칙 호출 확인                ← 여기가 빠진다
4. 에러코드/예외 추가 (필요 시)
5. api-spec/{도메인}.md에 에러 응답 추가 (필요 시)
6. 테스트 → .claude/skills/write-test.md 참조
```

### 3-4. 상태 전이 변경

파급력이 가장 넓다. 신중하게 작업한다.

```
1. docs/spec/biz-logic.md에 상태 전이도 수정
2. Entity의 상태 전이 메서드 수정
3. Service의 상태 변경 호출부 전체 확인      ← 여러 곳일 수 있다
4. Scheduler / StartupCompensationRunner 확인
5. API 응답에서 상태값 반환하는 곳 확인
6. Partial Unique Index 영향 확인 (상태 조건이 바뀌면)
7. 테스트 → .claude/skills/write-test.md 참조
```

### 3-5. 새 도메인(Bounded Context) 추가

```
1. docs/spec/에 관련 문서 작성 (schema, biz-logic, api-spec)
2. 패키지 구조 생성 (hexagonal)
   └── domain/
       ├── model/
       ├── port/
       └── service/
   └── adapter/
       ├── in/web/
       └── out/persistence/
3. Entity + Repository 구현
4. Service 구현
5. DTO + Controller 구현
6. Flyway 마이그레이션
7. 기존 Context와의 연결점 확인 (다른 Service에서 호출하는가?)
8. 테스트 → .claude/skills/write-test.md 참조
```

---

## 4. Service 누락 방지 체크

도메인 변경 후 Service를 놓치는 패턴이 반복되므로, 별도로 확인한다.

**변경한 Entity의 이름으로 Service 파일을 검색한다:**

```bash
grep -rn "변경한Entity" src/main/java/**/service/
```

**나온 Service 메서드마다 확인한다:**
- 이 메서드가 변경된 필드/규칙을 사용하는가?
- 사용한다면 변경이 반영되었는가?
- 새 필드를 세팅/조회해야 하는데 빠져있지 않은가?

**DTO 변환도 확인한다:**
- Entity → Response 변환에서 새 필드가 포함되었는가?
- Request → Entity 변환에서 새 필드가 매핑되었는가?

---

## 5. 완료 조건

모든 변경이 끝난 후 아래를 확인한다.

```
[ ] docs/spec/ 문서가 코드와 일치하는가
[ ] 변경 체인의 모든 레이어를 수정했는가
[ ] Service 메서드에서 새 필드/규칙이 반영되었는가
[ ] DTO ↔ Entity 매핑에 빠진 필드가 없는가
[ ] Flyway 마이그레이션이 schema.md와 일치하는가
[ ] 테스트 파급력 분석을 수행했는가 (.claude/skills/write-test.md)
```

---

## 6. 금지 사항

- ❌ 영향 분석 없이 Entity부터 수정하지 않는다
- ❌ 문서(docs/spec/)를 나중에 수정하지 않는다 — 문서가 먼저다
- ❌ Entity만 고치고 Service를 확인하지 않는 것은 금지다
- ❌ DTO 변환을 "나중에" 하지 않는다 — 체인에서 빠지면 불일치가 생긴다
- ❌ Flyway 없이 schema.md만 수정하지 않는다
