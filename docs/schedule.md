# MVP 스프린트 (2/28 ~ 3/3)

## 목표

Feed API + Verification API + Upload Session 완성 → Flutter 연동 → E2E 데모

## 일정

### Day 1 — 2/28 (토) Feed + Verification API
- [x] `GET /crews/{crewId}/feed` 구현 + Cucumber 7개 시나리오
- [x] `POST /verifications` 서비스 보강 (멤버십/PHOTO_REQUIRED/deadline) + Cucumber 11개 시나리오
- [x] Upload Session Cucumber 8개 시나리오

### Day 2 — 3/1 (일) Flutter 연동
- [ ] Flutter ↔ 백엔드 API 연동
- [ ] E2E 흐름 확인 (크루 생성 → 참여 → 인증 → 피드)

### Day 3 — 3/2 (월) 점검 및 보완
- [ ] E2E 전체 흐름 테스트
- [ ] 부족한 점 리스트업

### Day 4 — 3/3 (화) 배포 준비
- [ ] 운영 환경 설정 점검
- [ ] 최종 테스트

## API 현황

| API | 상태 |
|-----|------|
| `POST /crews` | ✅ 완료 |
| `POST /crews/join` | ✅ 완료 |
| `GET /crews` | ✅ 완료 |
| `GET /crews/{crewId}` | ✅ 완료 |
| `POST /upload-sessions` | ✅ 완료 |
| `GET /upload-sessions/{id}/events` | ✅ 완료 (SSE) |
| `PUT /internal/upload-sessions/{id}/complete` | ✅ 완료 (Lambda 콜백) |
| `GET /crews/{crewId}/feed` | ✅ 완료 |
| `POST /verifications` | ✅ 완료 |

## Lambda/SSE 아키텍처

상세 설계: `docs/photo-upload-lambda-sse.md` 참고
