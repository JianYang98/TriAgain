# 추후 고려 사항

> 개발 중 나온 개선 아이디어나 스케일업 시 필요한 작업을 기록한다.
> 지금은 불필요하지만, 나중에 참고할 내용들.
> **최신 항목이 위에 오도록 추가한다.**

---

### [2026-04-08] WithdrawUserService LEADER 검증 race condition (블로그 글감 후보)

- 현재 상태: `WithdrawUserService.withdraw()` 진입 메서드에서 LEADER 멤버 카운트 검증 → 트랜잭션 밖 Apple `/auth/revoke` 호출 → `@Lazy self.completeWithdraw()` 트랜잭션 진입의 3단계 흐름. 사전 검증과 트랜잭션 진입 사이 시간 갭 동안 다른 사용자가 해당 크루에 가입하면 `LEADER_CANNOT_WITHDRAW` 검증을 우회 가능 (`completeWithdraw`는 LEADER 재검증을 하지 않음)
- 필요 시점: Phase 2 또는 동시성 이슈 보고 시
- 이유: Phase 1(TPS 50, 500명) 트래픽에서는 발생 확률 매우 낮음. 본 PR(Apple revoke) 범위와 직교하므로 별도 이슈로 분리. 해결 옵션: (1) `completeWithdraw()` 진입 직후 LEADER 재검증, (2) 크루 row에 비관적 락 후 멤버 카운트 재확인, (3) 현 상태 유지
- **블로그 글감 메모**: "트랜잭션 안에 외부 API 호출 금지" 컨벤션을 지키려고 외부 호출을 트랜잭션 밖으로 빼는 순간, 사전 검증과 트랜잭션 사이에 race window가 생긴다는 트레이드오프. 카카오 탈퇴에는 외부 호출이 없어 race가 없고, Apple 탈퇴에만 등장한다는 비대칭성이 훅. App Store 5.1.1(v) 외부 요건이 내부 아키텍처 결정(self-injection 패턴)을 흔든 구조라는 점도 흥미로움. 기존 블로그(`blog-dead-letter-chunk-processor.md`, `blog-jpa-idclass-pitfall.md`)의 "문제 해결 경험" 톤과 일치. 제목 후보: "트랜잭션 밖으로 외부 API를 빼면 생기는 동시성 트레이드오프 — Apple Sign-In Revoke 사례"

---

### [2026-04-08] Apple revoke 후 트랜잭션 실패 시 정합성 트레이드오프

- 현재 상태: `WithdrawUserService`가 트랜잭션 밖에서 Apple `/auth/revoke` 호출 후 트랜잭션 진입. revoke 성공 직후 `completeWithdraw()` 트랜잭션이 실패하면 → Apple 측은 revoke 완료 / DB는 active 상태로 inconsistency 발생
- 필요 시점: 회원탈퇴 신뢰성 이슈 보고 시
- 이유: 사용자가 재로그인하면 신규 토큰으로 정상 동작하므로 사용자 경험상 거의 무영향(이전 refresh_token은 어차피 revoke되어 무효). 양방향 보상 트랜잭션을 도입하면 복잡도가 급증하고 Phase 1에는 과도. 현재는 graceful 정책으로 두고, 운영 중 실제 사례 발생 시 재평가

---

### [2026-04-08] users.apple_refresh_token DB 평문 저장 → application-level 암호화

- 현재 상태: V16 마이그레이션으로 `apple_refresh_token VARCHAR(500) NULL` 평문 저장
- 필요 시점: Phase 2 또는 보안 감사 시
- 이유: OAuth refresh_token은 application-level 암호화(KMS / Jasypt) 권장 자산. 누출 시 공격자가 사용자 Apple 권한을 직접 얻지는 못하지만(Apple은 revoke만 가능) 보호 가치가 있는 인증 자산. Phase 1에서는 RDS 외부 노출이 없고 backup 암호화가 적용되어 있어 acceptable

---

### [2026-04-08] APPLE_PRIVATE_KEY 환경변수 노출 표면 → Secrets Manager / 파일 마운트

- 현재 상태: GitHub Secrets → SSH `envs:` → `docker run -e APPLE_PRIVATE_KEY=...`로 PEM 전체가 컨테이너 환경변수에 주입. `/proc/<pid>/environ`을 통해 동일 EC2 내 동일 권한 사용자가 접근 가능
- 필요 시점: Phase 2 또는 다중 사용자/다중 컨테이너 환경 전환 시
- 이유: 현재 EC2는 단독 사용자/단일 컨테이너이므로 실질 위험 낮음. 개선 옵션: (1) AWS Secrets Manager에서 런타임 fetch, (2) `.p8` 파일을 권한 제한 디렉토리(예: `/etc/triagain/keys/apple.p8`, mode 0400)에 두고 `APPLE_PRIVATE_KEY_PATH`만 환경변수로 주입

---

### [2026-03-27] BC 경계 위반 리팩토링 (D-C1, D-C2)

- 현재 상태:
  - D-C1: UserCrewMembershipAdapter (User Context)가 Crew Context의 JPA 인프라를 직접 import
  - D-C2: NotificationAdapter, VerificationNotificationAdapter가 Support Context의 Notification 도메인 모델을 직접 생성
- 필요 시점: Phase 2 또는 마이크로서비스 분리 시
- 이유: 모노리스 단일 배포이므로 Phase 1에서는 실질적 문제 없음. 리팩토링 범위가 크고 기능 변경 없으므로 별도 PR로 분리

---

### [2026-03-27] 부하 테스트 우선순위

- 현재 상태: Phase 1 (500명, TPS 50 목표), 부하 테스트 미실시
- 필요 시점: Phase 1 출시 전 (1~3번), 데이터 축적 후 (4~5번)
- 우선순위:

| 순위 | 대상 | 핵심 이유 | 테스트 시나리오 |
|------|------|----------|---------------|
| 1 | `POST /verifications` | 트랜잭션 내 FCM 동기 호출 + 마감 직전 피크 몰림 | 마감 10분 전, 50명 동시 인증 |
| 2 | `GET /crews/{crewId}/feed` | 가장 빈번한 조회 + 복합 데이터 조합 | 100명이 각자 다른 크루 피드 동시 조회 |
| 3 | `POST /crews/join` | 비관적 락 경합 + 정원 동시성 정합성 검증 | 정원 10명 크루에 20명 동시 가입 → 10명만 성공? |
| 4 | 리마인더 스케줄러 (`findReminderTargets`) | 4-way JOIN (crews→crew_members→verifications→users) 풀스캔 가능성 | 크루 100개 × 멤버 10명 상태에서 쿼리 시간 측정 |
| 5 | `GET /crews` | 홈 화면 진입 = 전원 동시 호출 + todayVerified 배치 쿼리 검증 | 200명 동시 홈 화면 진입 |

---

### [2026-03-27] CreateVerificationService — 트랜잭션 내 FCM 동기 호출 분리

- 현재 상태: `CreateVerificationService.createVerification()`이 `@Transactional` 내부에서 `verificationNotificationPort.sendChallengeSuccessNotification()`을 동기 호출. 이 안에서 FCM 발송(`FcmAdapter.send()`)이 `@Retryable` 3회(1s+2s+4s, 최악 7초) 블로킹되며, 그동안 DB 커넥션을 점유
- 필요 시점: Phase 2 또는 트래픽 증가 시
- 이유: CLAUDE.md Anti-Pattern "트랜잭션 안에 외부 API 호출 금지" 위반. Phase 1(TPS 50, 500명)에서는 커넥션 풀 고갈 가능성 낮으나, 트래픽 증가 시 인증 API 응답 지연 + 커넥션 풀 고갈 위험. 해결 방향: (1) `@Async` + 스레드 풀로 FCM 비동기 분리, (2) 트랜잭션 커밋 후 이벤트(`@TransactionalEventListener`)로 FCM 발송, (3) 스케줄러처럼 트랜잭션 밖으로 FCM 호출 이동
- 검증: 리팩토링 전후로 `POST /verifications` 부하 테스트 실시하여 응답 시간 및 커넥션 풀 사용량 비교 필요

---

### [2026-03-27] BC 경계 위반 리팩토링 (D-C1, D-C2)

- 현재 상태:
  - D-C1: UserCrewMembershipAdapter (User Context)가 Crew Context의 JPA 인프라를 직접 import
  - D-C2: NotificationAdapter, VerificationNotificationAdapter가 Support Context의 Notification 도메인 모델을 직접 생성
- 필요 시점: Phase 2 또는 마이크로서비스 분리 시
- 이유: 모노리스 단일 배포이므로 Phase 1에서는 실질적 문제 없음. 리팩토링 범위가 크고 기능 변경 없으므로 별도 PR로 분리

---

### [2026-03-25] Dead Letter 자동 재시도 스케줄러

- 현재 상태: DeadLetter 도메인에 `retry()`, `resolve()` 메서드 구현 완료. DeadLetterRepositoryPort에 `findRetryable()` 메서드 존재. 재시도 스케줄러는 미구현
- 필요 시점: Phase 2 또는 Dead Letter 건수 증가 시
- 이유: Phase 1에서는 실패 건이 적고 수동 모니터링으로 충분. 지수 백오프(10분, 20분, 40분) 로직은 도메인에 구현되어 있어, 스케줄러만 추가하면 됨

---

### [2026-03-20] 알림 테이블 정리 스케줄러 (30일 삭제)

- 현재 상태: NotificationRepositoryPort.deleteOlderThan() 메서드는 구현 완료, 호출하는 스케줄러는 미구현
- 필요 시점: 알림 테이블 10만건 이상 시
- 이유: Phase 1 규모(500명)에서 알림 데이터량이 적어 즉시 도입 불필요. 데이터 증가 시 도입 예정

---

### [2026-03-20] CloudWatch 로그 연동

- 현재 상태: EC2 서버 로컬 로그만 존재
- 필요 시점: 출시 후 운영 모니터링 시
- 참고: 청천님 사례 — 중요 로그만 CloudWatch에 기록

---

### [2026-03-19] CrewPreviewAssembler 도메인 검증 로직 중복 해소

- 현재 상태: `Crew.addMember()`와 `CrewPreviewAssembler.calculateJoinBlockedReason()`이 동일한 가입 검증(정원/상태/마감일/중복)을 각각 수행. Assembler는 reason을 세분화(CREW_ENDED, LATE_JOIN_NOT_ALLOWED)하므로 단순 위임 불가
- 필요 시점: Phase 2 또는 다음 Crew 도메인 리팩토링 시
- 이유: 현재 두 로직은 동기화되어 있고, `addMember()` 수정 시 Assembler도 함께 확인하면 됨. 도메인 모델 변경 범위가 크므로 별도 작업으로 분리
- Phase 2 방향: `Crew` 도메인에 `getJoinBlockedReason(): Optional<JoinBlockedReason>` 메서드 추가 → Assembler는 위임만 수행

---

### [2026-03-15] Docker 이미지 SHA 태그 기반 배포

- 현재 상태: deploy.yml에서 `devjian/triagain:latest`로 pull/run. 빌드 시 SHA 태그(`devjian/triagain:${{ github.sha }}`)도 push하지만 배포에는 미사용
- 필요 시점: 롤백 필요성 발생 시 또는 Phase 2
- 이유: latest 태그 배포는 간단하지만 롤백 시 어떤 버전인지 추적 불가. SHA 태그로 배포하면 특정 커밋으로 즉시 롤백 가능

---

### [2026-03-13] Moderation Context BC 경계 위반 수정

- 현재 상태: `moderation/infra/CrewClientAdapter.java`가 `crew.domain.model.Crew`, `crew.domain.model.CrewMember`, `crew.port.out.CrewRepositoryPort`를 직접 import. Verification → Crew 경계 수정과 동일 패턴의 위반
- 필요 시점: 다음 Moderation Context 관련 작업 시
- 이유: 이번 PR은 Verification → Crew 경계만 수정 범위. Moderation도 동일하게 `CrewQueryUseCase` (Input Port) 도입 후 어댑터가 UseCase만 의존하도록 변경 필요

---

### [2026-03-13] Request DTO Validation 메시지 통일

- 현재 상태: `CreateUploadSessionRequest`에만 `@NotBlank(message = "...")` 설정. `CreateCrewRequest`, `JoinCrewRequest` 등 다른 Request DTO는 message 미설정 (기본 메시지 사용)
- 필요 시점: 프론트 에러 메시지 표시 구현 시
- 이유: 기능 문제는 아니고 일관성 이슈. 프론트에서 validation 에러 메시지를 유저에게 직접 표시하게 되면 한국어 메시지 통일이 필요

---

### [2026-03-11] 썸네일 생성은 Phase 2로 보류

- 현재 상태: 클라이언트 압축 이미지 1장만 업로드. 썸네일 미생성. COMPLETED = "원본 1장 업로드 완료"
- 필요 시점: Phase 2 (피드 성능 최적화 시)
- 이유: 지금 도입하면 아래 결정이 추가로 필요하여 업로드 플로우 안정화가 지연됨
  - 썸네일 생성 완료까지를 업로드 완료로 볼지
  - thumbnailUrl 저장 위치 (upload_session? verification?)
  - 피드/상세 응답 분기 방법
  - 썸네일 생성 실패 시 fallback 처리
- Phase 2 확장 방향:
  - thumbnailUrl 필드 추가 (피드: 썸네일, 상세: 원본)
  - 현재 imageUrl 중심 구조에서 thumbnailUrl만 추가하면 큰 변경 없이 확장 가능

---

### [2026-03-10] ~~코드 버그: 크루 최소 기간 미검증 (Crew.validateDates)~~ → 해결 완료 (2026-03-12)

- ~~현재 상태: `Crew.validateDates()`에서 `endDate > startDate`만 체크. biz-logic.md의 "최소 시작일+6일 (작심삼일 2회 보장)" 규칙이 코드에 미반영~~
- **해결**: `Crew.validateDates()`에 `endDate.isBefore(startDate.plusDays(6))` 검증 추가. 단위테스트(경계값+실패) 포함.

---

### [2026-03-12] 크루 최소 인원: 백엔드 @Min(1) 유지, 프론트에서 @Min(2) 제한

- 현재 상태: CreateCrewRequest @Min(1), Crew.java maxMembers < 1. biz-logic.md 규칙은 "2~10명"
- 필요 시점: 프론트 크루 생성 UI 구현 시
- 이유: 백엔드는 솔로 테스트 및 향후 솔로 모드 확장을 위해 @Min(1) 유지. 프론트 UI에서 최소 2명 제한으로 정상 사용자 가드. API 직접 호출로 1명 크루 생성 가능하나 Phase 1 규모에서 실질적 위험 낮음

---

### [2026-03-04] StartupCompensationRunner — Phase 2 전환 시 제거 검토

- 현재 상태: 단일 서버 + Spring @Scheduled 기반이라 서버 다운 시 스케줄러 미실행 → 서버 재시작 시 밀린 작업(크루 활성화 → 챌린지 실패 → 크루 종료)을 순서대로 보정
- 필요 시점: Phase 2 (Quartz 등 persistent scheduler 도입 시)
- 이유: Quartz의 misfire policy가 자동 보정을 제공하므로 이 Runner 제거 가능. 단, 제거 전에 3단계 순서(활성화 → 실패 → 종료) 보장 여부 확인 필요

---

### [2026-03-04 21:50] 챌린지 Lazy 생성 — 실패 후 미재도전 유저 알림

- 현재 상태: 챌린지 생성을 Eager(크루 활성화/참여 시) → Lazy(첫 인증 시 자동 생성)로 변경. 스케줄러는 FAILED 처리만 수행, 새 챌린지 자동 생성 제거.
- 필요 시점: Phase 2 (알림 시스템 도입 시)
- 이유: Lazy 생성이므로 실패 후 재도전하지 않는 유저는 챌린지가 없는 상태로 남음. 리마인더 푸시("다시 도전해보세요!")가 필요하지만 Phase 1에서는 알림 시스템 미구현.

---

### [2026-03-04 20:10] Apple 로그인 실제 연동 TODO

- 현재 상태: 코드 구현 완료 (Port/Adapter/UseCase/Controller/테스트), Cucumber @ignore + AdapterTest @Disabled
- 필요 시점: 앱스토어 출시 전
- 남은 작업:
  - Apple Developer 계정에서 Service ID 발급 → APPLE_CLIENT_ID 환경변수 설정
  - 실제 Apple Identity Token으로 E2E 검증
  - Cucumber @ignore / AdapterTest @Disabled 해제
  - Flutter 클라이언트 Apple Sign In 연동
- 이유: 백엔드 코드는 준비 완료, Apple Developer 계정 설정 + 클라이언트 연동이 별도 작업

---

### [2026-03-03 18:00] Logout 토큰 블랙리스트 도입

- 현재 상태: `POST /auth/logout`은 서버 no-op (200 반환만), 클라이언트가 로컬 토큰 삭제로 로그아웃 처리. refreshToken은 순수 JWT stateless.
- 필요 시점: Phase 2 (Redis 도입 이후)
- 이유: Phase 1에서는 Redis 미사용, 토큰 탈취 시나리오 대응은 Phase 2 보안 강화 시점에 적합
- Phase 2 계획:
  - `token_blacklist` 테이블 또는 Redis SET으로 블랙리스트 관리
  - `TokenBlacklistPort` (Output Port) + `RedisTokenBlacklistAdapter` 구현
  - `RefreshTokenService.refresh()` 시 블랙리스트 조회 추가
  - 만료된 블랙리스트 항목 정리 스케줄러 추가
  - `LogoutUseCase` 생성하여 블랙리스트 등록 로직 분리

---

### [2026-03-03 11:14] 예외 핸들러 로그 폭주 대비

- 맥락: GlobalExceptionHandler에 전체 핸들러 request URI 로깅 추가
- 지금 한 것: 모든 예외 발생 시 `[POST /auth/signup]` 형태로 요청 정보 로깅
- 추후 고려: TPS가 올라가면 동일 에러가 초당 수백 건 반복될 수 있음 (예: 봇 공격)
  - Rate-limiting 로깅 또는 Sampling 적용 검토
  - Phase 1 (TPS 50)에선 해당 없음, Phase 2 이후 트래픽 증가 시 재검토

---

### [2026-03-03 11:12] `/internal/**` Lambda 인증 필터 추가 필요

- 맥락: `/internal/**` 엔드포인트가 `permitAll`로 열려있어 보안 위험 → prod에서 `denyAll`로 임시 차단
- 지금 한 것: `SecurityConfig`(prod)에서 `denyAll`로 변경, `DevSecurityConfig`(!prod)는 `permitAll` 유지
- 추후 고려: Lambda 연동 시 시크릿 키 헤더 검증 필터 추가
  - Lambda 요청에 `X-Internal-Secret` 등의 헤더를 포함하고, Spring Security 필터에서 검증
  - 필터 추가 후 dev/prod 설정 통일 (`denyAll` → 필터 기반 인증으로 전환)
  - VPC 내부 통신만 허용하는 네트워크 레벨 제한도 병행 검토