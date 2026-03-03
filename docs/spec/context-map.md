# Context Map - 바운디드 컨텍스트 관계도

## 컨텍스트 전체 구조

```
┌──────────────────────┐
│  User Context        │  ← 회원/인증
│  - User              │
│  - AuthInfo          │
└──────────────────────┘
         ↑ 
         │ API 호출 (동기)
         │ "요청 계정상태를 User Context를 통해 확인"
         │
┌──────────────────────────────┐
│   Crew Context (Core)        │  ← 작심삼일 핵심
│  - Crew                      │  // 크루 생성, 참여, 챌린지 핵심 로직
│  - CrewMember                │  // crewLeaderId 포함
│  - Challenge                 │
│  - ProgressPolicy            │
└──────────────────────────────┘
         │
         │ Command (동기)
         │ "인증 생성 요청"
         ↓
┌──────────────────────────────┐
│ Verification Context         │  ← 인증 기록
│  - Verification              │  // "인증했어?"
│  - VerificationPolicy        │
│  - UploadSession             │ 
└──────────────────────────────┘
         │                  │
         │ Event (비동기)    │ Command (동기)
         │                  │ "신고 요청"
         │                  ↓
         │    ┌────────────────────────────────┐
         │    │ Moderation Context        ⭐   │
         │    │  ← 신고 + 검토                  │
         │    │                                │
         │    │  [신고]                        │
         │    │  - Report                      │
         │    │  - ReportPolicy                │
         │    │    (신고 3건 → PENDING)         │
         │    │    (1인 1신고)                  │
         │    │    (7일 미검토 → 자동승인)       │
         │    │                                │
         │    │  [검토]                        │
         │    │  - ReviewerStrategy            │
         │    │    (AUTO/CREW_LEADER/AI)       │
         │    │  - ReviewPolicy                │
         │    │                                │
         │    │  Phase 1: AutoReviewer         │
         │    │  Phase 2: HumanReviewer        │
         │    │  Phase 3: AiReviewer           │
         │    └────────────────────────────────┘
         │                  │
         │                  │ Event (비동기)
         │                  │ - ReviewCompletedEvent
         │                  │
         ↓                  ↓
┌──────────────────────────────┐
│ Support Context              │  ← 알림/반응
│  - Notification              │
│  - Reaction                  │
│                              │
│  [Phase 2+ 분리 트리거]       │
│  → Notification 종류 5개↑    │
│  → 독립 배포 필요 시          │
└──────────────────────────────┘
```

## 컨텍스트 간 통신 방식

| From | To | 방식 | 설명 |
|------|----|------|------|
| Crew Context | User Context | API 호출 (동기) | 계정 상태 확인 |
| Crew Context | Verification Context | Command (동기) | 인증 생성 요청 |
| Verification Context | Moderation Context | Command (동기) | 신고 요청 |
| Verification Context | Support Context | Event (비동기) | 인증 완료 알림 |
| Moderation Context | Support Context | Event (비동기) | 검토 완료 알림 (ReviewCompletedEvent) |

## 컨텍스트별 책임

### User Context
- 회원 가입 / 로그인 / 인증
- 계정 상태 관리

### Crew Context (Core)
- 크루 생성 / 참여 / 관리
- 챌린지 사이클 관리 (3일 단위)
- 진행률 추적 (ProgressPolicy)

### Verification Context
- 일일 인증 기록
- 업로드 세션 관리 (Pre-signed URL)
- 인증 정책 (VerificationPolicy)

### Moderation Context
- 신고 접수 및 정책 관리 (ReportPolicy)
- 검토 전략 (ReviewerStrategy)
  - Phase 1: AutoReviewer (자동 승인)
  - Phase 2: HumanReviewer (크루장 검토)
  - Phase 3: AiReviewer (AI 검토)

### Support Context
- 알림 발송 (Notification)
- 반응 관리 (Reaction / 이모지)
- Phase 2+ 독립 서비스 분리 검토
