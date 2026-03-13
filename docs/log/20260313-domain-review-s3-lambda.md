# 도메인 리뷰: feat/s3-lambda-presigned-url

## Context
S3 Lambda presigned URL 브랜치의 도메인 로직 리뷰.
핵심 변경: USED 상태 제거 → DB UNIQUE, DeadlinePolicy 중앙화,
upload session에 crewId 추가, crewId 기반 전환.

## Overall: PASS WITH SUGGESTIONS

## Issues (수정 필요)
### 1. [CRITICAL] challengeId-only 플로우에서 session cross-crew 검증 누락
- 위치: CreateVerificationService.java:47-51
- command.crewId()가 null이면 cross-crew 체크 스킵됨
- 공격: Crew A 이미지 → Crew B 인증에 사용 가능
- 해결: createPhotoVerification() 내 session.crewId vs challenge.crewId 재검증

## Warnings (4건)
1. DeadlinePolicy.todayDeadline() 타임존 의존 (Phase 2 대응)
2. CrewMembershipAdapter 반복 findById (JPA L1 캐시로 현재 OK)
3. expire() vs complete() 멱등성 비대칭 (의도적, 문서화 권장)
4. 크루 상태 검증이 Service에 위치 (현 구조에서는 적절)

## 잘된 점
- DeadlinePolicy 중앙화, USED 상태 제거 → DB UNIQUE
- 헥사고날 순수성, 테스트 커버리지

## 수정 우선순위
1. [즉시] Issue #1: cross-crew 검증 추가 (보안 결함)
2. [선택] expire() 비멱등 이유 Javadoc
3. [Phase 2] Clock 주입, UploadSessionPolicy 분리
