# Upload Session PR 리뷰 — 후속 TODO

> PR: `feat/upload-sessions-happy-path`
> 리뷰 날짜: 2026-03-01
> CRITICAL 3건 모두 구현 완료 확인, 아래는 후속 개선 항목

---

## TODO 목록

### 1. afterCommit 단위 테스트 추가 [Medium]

**대상:** `CompleteUploadSessionService`

트랜잭션 커밋 후 SSE 알림이 발행되는 구조(`TransactionSynchronizationManager.registerSynchronization` + `afterCommit`)에 대한 단위 테스트가 없음.

- 트랜잭션 정상 커밋 시 `SsePort.send()` 호출 검증
- 트랜잭션 롤백 시 `SsePort.send()` 미호출 검증
- `@TransactionalEventListener(phase = AFTER_COMMIT)` 전환도 고려 가능

### 2. recordCompletion 예외 테스트 추가 [Medium]

**대상:** `ChallengeClientAdapter.recordCompletion()`

`CHALLENGE_NOT_FOUND` 예외 케이스에 대한 단위 테스트가 없음.

- 존재하지 않는 challengeId로 호출 시 `BusinessException(ErrorCode.CHALLENGE_NOT_FOUND)` 발생 검증
- ChallengePort mock을 통한 서비스 레벨 테스트도 함께 고려

### 3. SseEmitterAdapter 단위 테스트 추가 [Medium]

**대상:** `SseEmitterAdapter`

SSE 어댑터의 엣지 케이스 테스트가 없음.

- `IOException` 발생 시 emitter 정리 처리 검증
- emitter 타임아웃(60초) 후 콜백 동작 검증
- 구독 → 이벤트 수신 → 완료 라이프사이클 통합 테스트

### 4. 챌린지 중복 조회 구조 리팩토링 [Low]

**대상:** `CreateVerificationService`

현재 DTO에서 이미 받은 챌린지 정보를 서비스 내부에서 다시 DB 조회하는 패턴이 있음.

- DTO → Entity 재로드 패턴이 필요한 경우인지 검토
- 불필요한 조회라면 DTO에서 직접 도메인 객체 생성으로 전환
- 데이터 정합성 보장이 필요한 경우 현행 유지 후 주석으로 이유 명시
