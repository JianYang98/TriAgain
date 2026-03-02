# PR Review: `feat/upload-sessions-happy-path`

> **리뷰 일시:** 2026-02-22
> **브랜치:** `feat/upload-sessions-happy-path` → `main`
> **커밋:** `3dae56f feat: 업로드 세션 생성 API 구현 (POST /upload-sessions)`
> **변경 파일:** 12개 (+293, -6)

---

## 전체 요약

업로드 세션 생성 API (POST /upload-sessions)의 Happy Path 구현이다. 헥사고날 아키텍처 경계를 잘 지키고 있고, Cucumber 인수 테스트도 포함되어 전체적인 구조는 좋다. 다만 몇 가지 이슈가 있다.

---

## Critical (반드시 수정)

### 1. `@Transactional` 안에서 외부 스토리지 호출

**파일:** `CreateUploadSessionService.java:31-48`

```java
@Transactional
public UploadSessionResult createUploadSession(CreateUploadSessionCommand command) {
    // ...
    UploadSession saved = uploadSessionRepositoryPort.save(session);
    String presignedUrl = storagePort.generatePresignedUrl(imageKey, command.fileType());  // 외부 호출
    String imageUrl = storagePort.getImageUrl(imageKey);  // 외부 호출
```

CLAUDE.md Anti-Pattern에 명시: **"트랜잭션 안에 외부 API 호출 금지 (S3 등)"**

현재는 `LocalStorageAdapter`라 문제가 안 되지만, `S3PresignAdapter`로 교체되면 트랜잭션 안에서 네트워크 호출이 발생한다. Pre-signed URL 생성 자체는 S3 통신이 아니라 내부 서명 생성이긴 하지만, 아키텍처 원칙 준수를 위해 `storagePort` 호출을 트랜잭션 바깥으로 분리해야 한다.

**제안:**
```java
public UploadSessionResult createUploadSession(CreateUploadSessionCommand command) {
    validateFileType(command.fileType());
    validateFileSize(command.fileSize());

    String imageKey = storagePort.generateImageKey(command.userId(), command.fileName());
    String presignedUrl = storagePort.generatePresignedUrl(imageKey, command.fileType());
    String imageUrl = storagePort.getImageUrl(imageKey);

    UploadSession session = UploadSession.create(command.userId(), imageKey, command.fileType());
    UploadSession saved = saveUploadSession(session);  // 트랜잭션 범위를 여기만

    return new UploadSessionResult(...);
}

@Transactional
protected UploadSession saveUploadSession(UploadSession session) {
    return uploadSessionRepositoryPort.save(session);
}
```

혹은 더 간단하게 메서드 레벨 `@Transactional`을 제거하고 `UploadSessionRepositoryPort.save()` 자체가 트랜잭션을 갖도록 하는 방법도 있다.

---

## Major (수정 권장)

### 2. API 명세와 인증 방식 불일치

**파일:** `UploadSessionController.java:25`

`@RequestHeader("X-User-Id")` 사용하고 있으나, API 명세(`api-spec.md:26`)에는 `Authorization: Bearer <token>`으로 정의되어 있다. Phase 1에서 인증 미구현이라 헤더로 우회하는 것은 이해하지만, 이 결정이 의도적인지 확인이 필요하다. 의도적이라면 API 명세에 Phase 1 한정 사항으로 명시해야 한다.

### 3. 스키마와 응답 필드 타입 불일치

**파일:** `CreateUploadSessionUseCase.java:13`

스키마(`schema.md`)에서 `upload_session.id`는 `bigint`이고 도메인 모델도 `Long`이지만, API 명세의 예시 응답에는 `"uploadSessionId": "upload_123"`으로 문자열이다. 현재 구현은 `Long`을 반환하는데, 클라이언트와 계약을 맞춰야 한다. API 명세 쪽이 잘못된 것으로 보이므로 명세를 수정하거나, 명세 의도대로 문자열 prefix를 붙일지 결정이 필요하다.

### 4. 검증 로직이 도메인이 아닌 서비스에 위치

**파일:** `CreateUploadSessionService.java:53-63`

파일 타입/크기 검증은 업로드 세션의 도메인 정책이다. CLAUDE.md에 "도메인 정책(Policy)은 별도 클래스로 분리한다"고 되어 있으므로, `UploadSession` 도메인 모델이나 별도 Policy 클래스로 이동하는 것이 적절하다.

---

## Minor (개선 사항)

### 5. `UploadSessionController` — `@RequestMapping` 누락

다른 컨트롤러와의 일관성을 위해 클래스 레벨에 `@RequestMapping("/upload-sessions")`를 두고 메서드에는 `@PostMapping`만 사용하는 것이 일반적이다.

### 6. `expiresAt` 계산이 서비스에서 `LocalDateTime.now()` 호출

**파일:** `CreateUploadSessionService.java:45`

`LocalDateTime.now().plusMinutes(PRESIGNED_URL_EXPIRY_MINUTES)` 값은 실제 pre-signed URL의 만료 시간과 동기화되지 않는다. `StoragePort`에서 만료 시간도 함께 반환하거나, 만료 정보를 한 곳에서 관리해야 한다. 현재는 서비스의 15분과 실제 S3 pre-signed URL 만료 시간이 별도로 관리되어 불일치 가능성이 있다.

### 7. `LocalStorageAdapter.extractExtension` — 보안 고려

**파일:** `LocalStorageAdapter.java:30-34`

파일명을 그대로 신뢰하고 있다. Phase 1이라 로컬 어댑터에서 큰 문제는 아니지만, S3 어댑터 구현 시에는 `fileType` 기반으로 확장자를 결정하는 것이 안전하다 (`image/jpeg` → `.jpg`).

### 8. Cucumber 스텝 `응답 코드는 {int}이다` 중복 가능성

`UploadSessionSteps`에서 `HealthSteps`의 `@그러면("응답 코드는 {int}이다")`를 재사용하고 있는 것으로 보인다. 의도된 것이라면 좋지만, 스텝 정의 중복/충돌이 없는지 확인이 필요하다.

---

## Good Points

- **헥사고날 경계 준수**: Controller → UseCase → Service → Port 흐름이 깔끔하다
- **ScenarioContext 도입**: Cucumber 스텝 간 상태 공유를 Spring Bean으로 관리하는 리팩토링이 좋다
- **도메인 모델 분리**: `UploadSession`이 POJO로 인프라 의존 없이 잘 설계되어 있다
- **LocalStorageAdapter**: 로컬 개발용 어댑터를 먼저 만들어서 S3 없이도 동작 가능하게 한 점이 좋다
- **record 활용**: Command, Result DTO를 UseCase 인터페이스 내부 record로 정의한 방식이 깔끔하다

---

## 요약 테이블

| 구분 | 건수 | 비고 |
|------|------|------|
| Critical | 1 | 트랜잭션 내 외부 호출 |
| Major | 3 | 인증 방식, 타입 불일치, 검증 로직 위치 |
| Minor | 4 | RequestMapping, expiresAt, 확장자, 스텝 중복 |

Critical 1건은 반드시 수정하고, Major는 이번 PR에서 함께 처리하거나 후속 작업으로 분리하면 된다.
