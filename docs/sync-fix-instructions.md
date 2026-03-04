# 백엔드 동기화 수정 지시서

> 생성일: 2026-03-04
> 출처: 오케스트레이션 에이전트 전체 동기화 검증
> 정본: 백엔드 실제 코드 (문서가 코드와 불일치하므로 코드 기준으로 문서를 수정)

---

## 수정 우선순위 요약

| 순위 | 지시서 | 심각도 | 이유 |
|------|--------|--------|------|
| 1 | [완료] #5 api-spec.md 업데이트 | MEDIUM | 문서-코드 괴리 → 프론트 개발 시 혼동 |
| 2 | [완료] #6 V1 Flyway 마이그레이션 | LOW | 프로덕션 배포 전까지 필요 |

---

## [완료] [수정 지시 #5] api-spec.md 문서 업데이트 (6건)

### 배경

api-spec.md의 여러 항목이 실제 구현과 불일치. 프론트 개발 시 잘못된 명세를 참조할 위험이 있음.

### 수정 사항

**파일:** `docs/spec/api-spec.md`

#### 5a. 카카오 로그인 응답 envelope 수정

- 변경 전: `"status": "OK"`
- 변경 후: `"success": true`
- 이유: 백엔드 `ApiResponse`는 `boolean success` 사용, `status` 필드 없음

#### 5b. Upload sessions 요청에 challengeId 필드 추가

- 변경 전: 요청 body에 `challengeId` 없음
- 변경 후: `"challengeId": "string"` (필수, `@NotBlank`) 추가
- 이유: 실제 코드에서 `@NotBlank String challengeId` 필수 파라미터

#### 5c. Upload session ID 타입 수정

- 변경 전: 예시 `"upload_123"` (문자열)
- 변경 후: 예시 `123` (숫자), 타입 `Long` (BIGINT auto increment)
- 이유: 실제 DB 스키마 `BIGINT GENERATED ALWAYS AS IDENTITY`

#### 5d. Verification 응답 reviewStatus 예시 수정

- 변경 전: `"reviewStatus": "AUTO_APPROVED"`
- 변경 후: `"reviewStatus": "NOT_REQUIRED"`
- 이유: 실제 enum 값은 `NOT_REQUIRED`, `PENDING`, `IN_REVIEW`, `COMPLETED`이며, `AUTO_APPROVED`는 존재하지 않음

#### 5e. Crew 참여 엔드포인트 업데이트

- 변경 전: `POST /crews/{crewId}/join` (TODO 상태)
- 변경 후: `POST /crews/join` + request body `{ "inviteCode": "string" }`
- 이유: 이미 구현 완료됨. inviteCode로 참여하는 방식으로 구현됨

#### 5f. Verification 응답에 crewId 필드 추가

- 변경 전: Verification 응답에 `crewId` 없음
- 변경 후: `"crewId": "Long"` 추가
- 이유: 백엔드 entity와 프론트 모델 양쪽에 `crewId` 필드 존재

### 검증

- 수정된 api-spec.md와 실제 백엔드 코드 대조 확인
- 각 엔드포인트별 요청/응답 구조가 코드와 일치하는지 확인

---

## [완료] [수정 지시 #6] V1 Flyway 마이그레이션 생성

### 배경

초기 스키마 생성 마이그레이션(V1)이 없어 프로덕션 신규 환경 배포 불가. 현재 `db/migration/`에 V2~V5만 존재.

- 로컬: Hibernate `ddl-auto: update`로 자동 생성되어 문제 없음
- 프로덕션: Flyway 순차 적용이 필요하므로 V1 필수

### 수정 사항

1. **파일:** `src/main/resources/db/migration/V1__initial_schema.sql` (신규 생성)
   - `schema.md` 기반으로 모든 테이블 CREATE TABLE 문 작성
   - 대상 테이블:
     - `users`
     - `crews`
     - `crew_members`
     - `challenges`
     - `verifications`
     - `upload_session`
     - `reports`
     - `reviews`
     - `notifications`
     - `reactions`
   - UNIQUE 제약조건, 인덱스, DEFAULT 값 포함

### 주의사항

- **V1은 V2~V5 적용 전의 초기 상태로 작성할 것 (권장)**
  - 예: `users` 테이블에 `provider` 컬럼 없음 (V2에서 추가됨)
  - V2~V5가 V1 위에 순차 적용되어 최종 스키마를 만드는 구조
- V2~V5의 기존 마이그레이션 내용을 확인하여 충돌 없는지 검증

### 검증

- 빈 DB에서 `flyway migrate` 실행하여 V1~V5 순차 적용 확인
- 최종 스키마가 `schema.md`와 일치하는지 확인
- 기존 개발 DB에서는 `flyway baseline`으로 V5 이후부터 적용

---

## 일치 확인 항목 (문제 없음)

아래 항목들은 검증 결과 문서-코드 간 일치가 확인됨:

- ErrorCode enum 체계 (A/C/U/CR/V/M/S 접두사) ✅
- SecurityConfig permitAll 엔드포인트 목록 ✅
- 토큰 관리 정책 (accessToken 30분, refreshToken 7일) ✅
- 닉네임 검증 규칙 `^[가-힣a-zA-Z0-9_]{2,12}$` ✅
- schema.md ↔ JPA Entity 필드 매핑 ✅
