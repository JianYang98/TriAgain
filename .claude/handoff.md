# Handoff: S3 Lambda Presigned URL PR 머지 후

> PR #14 (`feat/s3-lambda-presigned-url`) 머지 후 다음 작업 방향 정리

---

## 즉시 수정 필요

### 크루 최소 기간 미검증 버그

- **파일**: `crew/domain/model/Crew.java:171-178` (`Crew.validateDates()`)
- **현재**: `endDate > startDate`만 체크
- **정본(biz-logic.md)**: "최소 시작일+6일 (작심삼일 2회 보장)"
- **수정**: `endDate >= startDate + 6` 검증 추가
- **참고**: `docs/log/future-considerations.md` [2026-03-10] 항목

---

## 다음 단계

### 1. Flutter 클라이언트 사진 인증 연동

PR #14에서 백엔드 사진 업로드 플로우가 완성됨:
- Presigned URL 발급 (`POST /upload-sessions`)
- S3 업로드 → Lambda → Internal API로 session COMPLETED 처리
- SSE로 클라이언트에 업로드 완료 알림

프론트엔드에서 구현할 항목:
- Presigned URL 요청 → S3 PUT 업로드
- SSE 구독으로 업로드 완료 감지 (+ 폴링 fallback)
- 인증 생성 API 호출 (`POST /verifications`)

### 2. E2E 스모크 테스트

핵심 해피패스 3~5개 수동 검증:
- 크루 생성 → 참여 → 챌린지 시작
- 텍스트 인증 제출
- 사진 인증 (Presigned URL → S3 업로드 → 인증 생성)
- 3일 연속 인증 완료 → 챌린지 COMPLETED
- 인증 미제출 → 챌린지 FAILED → 새 사이클 자동 생성