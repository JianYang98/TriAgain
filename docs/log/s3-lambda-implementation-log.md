# S3 Lambda + SSE 사진 인증 구현 로그

## 1. 개요

- **뭘 했는지**: S3 업로드 완료 감지 Lambda + 내부 API + SSE 실시간 알림 플로우 구현
- **왜 했는지**: 기존 방식은 `POST /verifications`에서 세션 완료 + 인증 생성을 모두 처리 → 책임 과다. S3 업로드 완료를 클라이언트가 알려주는 것도 신뢰성 문제
- **결과**: Lambda가 업로드 완료를 자동 감지 → SSE로 프론트에 알림 → `/verifications`는 인증 생성에만 집중

## 2. 전체 플로우 (5단계)

```
1. POST /upload-sessions → presignedUrl + uploadSessionId 수신
2. GET /upload-sessions/{id}/events (SSE 구독 시작)
3. PUT {presignedUrl} → S3에 이미지 직접 업로드
4. S3 Event → Lambda → PUT /internal/upload-sessions/complete?imageKey={key} → SSE "COMPLETED" 전송
5. POST /verifications (인증 생성)
```

상세 시퀀스:

```
Client                    S3                  Lambda               Backend
  |                        |                    |                    |
  |-- POST /upload-sessions ------------------------------------------------->|
  |<-------------------------------------- presignedUrl + sessionId ----------|
  |                        |                    |                    |
  |-- GET /upload-sessions/{id}/events (SSE) -------------------------------->|
  |<====================================== SSE 연결 수립 ====================|
  |                        |                    |                    |
  |-- PUT {presignedUrl} ->|                    |                    |
  |<-- 200 OK ------------|                    |                    |
  |                        |                    |                    |
  |                        |-- S3 PutObject --->|                    |
  |                        |   Event            |                    |
  |                        |                    |-- PUT /internal/   |
  |                        |                    |   upload-sessions/ |
  |                        |                    |   complete?imageKey|
  |                        |                    |   + X-Internal-    |
  |                        |                    |   Api-Key -------->|
  |                        |                    |                    |
  |                        |                    |                    | session.complete()
  |                        |                    |                    | PENDING → COMPLETED
  |                        |                    |                    | (afterCommit)
  |                        |                    |<--- 200 OK -------|
  |                        |                    |                    |
  |<======== SSE event: "upload-complete" / "COMPLETED" ====================|
  |                        |                    |                    |
  |-- POST /verifications (uploadSessionId) -------------------------------->|
  |<-------------------------------------- 인증 생성 완료 ------------------|
```

## 3. 해결한 설계 이슈

### 이슈 1: Lambda가 session ID를 모른다

S3 이벤트는 bucket + key만 전달하므로, Lambda는 upload session ID를 알 수 없다.

**해결**: imageKey 기반으로 내부 API 변경

- 변경 전: `PUT /internal/upload-sessions/{id}/complete`
- 변경 후: `PUT /internal/upload-sessions/complete?imageKey={key}`
- `UploadSessionRepositoryPort`에 `findByImageKey(String imageKey)` 메서드 추가
- `UploadSessionJpaRepository`에 `findByImageKey` 쿼리 메서드 추가

### 이슈 2: SecurityConfig `/internal/**` 접근 제어

JWT 기반 인증은 Lambda에 적합하지 않다. Lambda는 유저 토큰이 없다.

**해결**: API Key 헤더 방식 (Phase 1 적합)

- `InternalApiKeyFilter` 생성 → `X-Internal-Api-Key` 헤더 검증
- SecurityConfig에서 `/internal/**`를 `permitAll()`로 변경 (JWT 필터 스킵)
- `InternalApiKeyFilter`가 `/internal/` 경로에만 적용되어 API Key 검증 수행
- 유효하지 않거나 누락된 키 → 403 Forbidden 반환

### 이슈 3: SSE 이벤트가 트랜잭션 커밋 전에 전송될 수 있다

세션 상태를 COMPLETED로 변경한 직후 SSE를 보내면, 클라이언트가 `/verifications`를 호출할 때 DB에 아직 커밋되지 않은 상태일 수 있다.

**해결**: `TransactionSynchronizationManager.registerSynchronization(afterCommit)` 사용

- 트랜잭션 커밋이 완료된 후에만 SSE 이벤트 전송
- 클라이언트가 SSE 수신 후 즉시 API 호출해도 일관된 DB 상태 보장

## 4. 변경 파일 목록

### 신규 생성

| 파일 | 역할 |
|------|------|
| `lambda/upload-complete/handler.py` | Lambda 핸들러 (S3 이벤트 → 내부 API 호출) |
| `lambda/template.yaml` | SAM 배포 템플릿 |
| `lambda/deploy.sh` | Lambda 배포 스크립트 |
| `src/.../common/auth/InternalApiKeyFilter.java` | 내부 API Key 검증 필터 |
| `src/test/.../common/auth/InternalApiKeyFilterTest.java` | 필터 단위테스트 |

### 수정

| 파일 | 변경 내용 |
|------|----------|
| `src/.../verification/api/internal/InternalUploadSessionController.java` | `@PathVariable id` → `@RequestParam imageKey` 방식으로 변경 |
| `src/.../verification/application/CompleteUploadSessionService.java` | `findByImageKey()` 사용 + `afterCommit` SSE 전송 |
| `src/.../verification/port/in/CompleteUploadSessionUseCase.java` | `complete(Long id)` → `complete(String imageKey)` |
| `src/.../verification/port/out/UploadSessionRepositoryPort.java` | `findByImageKey(String imageKey)` 추가 |
| `src/.../verification/infra/UploadSessionJpaAdapter.java` | `findByImageKey` 구현 |
| `src/.../verification/infra/UploadSessionJpaRepository.java` | `findByImageKey` 쿼리 메서드 추가 |
| `src/.../common/auth/SecurityConfig.java` | `/internal/**` permitAll + InternalApiKeyFilter 등록 |
| `src/main/resources/application-prod.yml` | `internal.api-key` 설정 추가 |
| `src/test/.../verification/application/CompleteUploadSessionServiceTest.java` | imageKey 기반 테스트로 변경 |

## 5. 상태 전이 다이어그램

```
                    Lambda 호출
    PENDING ─────────────────────> COMPLETED ─────────> USED
       │         (complete())         │         (POST /verifications
       │                              │          에서 세션 상태 확인)
       │                              │
       │    스케줄러 (15분 초과)        │
       └──────────────────────> EXPIRED
```

- `PENDING`: 세션 생성됨, S3 업로드 대기 중
- `COMPLETED`: Lambda가 업로드 완료 감지, 세션 완료 처리됨
- `EXPIRED`: 15분 내 업로드 완료되지 않음 (스케줄러가 만료 처리)
- `USED`: `POST /verifications`에서 인증 생성 시 사용됨

## 6. SSE 동작

- **SseEmitterAdapter**: `ConcurrentHashMap<Long, SseEmitter>`로 emitter 관리
- **타임아웃**: 60초
- **이벤트 형식**: `event: upload-complete`, `data: COMPLETED`
- **트랜잭션 동기화**: `TransactionSynchronizationManager`의 `afterCommit()`으로 커밋 후 전송
- **SSE 엔드포인트**: `GET /upload-sessions/{id}/events` (permitAll — JWT 불필요)

## 7. Lambda 구현

- **런타임**: Python 3.12 (cold start 최적화)
- **의존성**: 표준 라이브러리만 사용 (`urllib.parse`, `urllib.request`)
- **메모리**: 128 MB
- **타임아웃**: 15초 (Lambda), 10초 (HTTP 요청)
- **트리거**: S3 `s3:ObjectCreated:Put` 이벤트
- **필터**: `upload-sessions/` prefix로 시작하는 키만 처리
- **환경변수**: `BACKEND_URL`, `INTERNAL_API_KEY`
- **배포**: SAM (Serverless Application Model) + `deploy.sh`

## 8. 보안 구성

| 엔드포인트 | 인증 방식 | 설명 |
|-----------|----------|------|
| `POST /upload-sessions` | JWT (Bearer) | 사용자 인증 필요 |
| `GET /upload-sessions/{id}/events` | 없음 (permitAll) | SSE 구독, JWT 불필요 |
| `PUT /internal/upload-sessions/complete` | API Key (`X-Internal-Api-Key`) | Lambda → Backend 통신 |
| `POST /verifications` | JWT (Bearer) | 사용자 인증 필요 |

## 9. 테스트 커버리지

### CompleteUploadSessionServiceTest

| 테스트 케이스 | 검증 내용 |
|-------------|----------|
| PENDING 세션 완료 성공 | imageKey로 조회 → COMPLETED 전이 → afterCommit에서 SSE 전송 |
| 존재하지 않는 imageKey | `UPLOAD_SESSION_NOT_FOUND` 예외 |
| 이미 COMPLETED 세션 | 멱등 — 예외 없이 저장만 수행 |
| EXPIRED 세션 완료 시도 | `UPLOAD_SESSION_NOT_PENDING` 예외 |

### InternalApiKeyFilterTest

| 테스트 케이스 | 검증 내용 |
|-------------|----------|
| 유효한 API Key | 필터 통과, 다음 체인 실행 |
| 잘못된 API Key | 403 Forbidden |
| API Key 누락 | 403 Forbidden |
| `/internal/` 외 경로 | 필터 스킵 (검증 안 함) |
