# 비즈니스 규칙 (Business Logic)

## 1. 핵심 기능 요구사항

### 1.1 크루 생성

| 항목 | 내용 |
|------|------|
| 크루 이름 | 필수 입력 |
| 크루 목표 (제목) | 필수 입력 |
| 최대 인원 | 2~10명 (프론트 UI 제한. 백엔드는 솔로 테스트용 @Min(1) 허용) |
| 기간 설정 | 시작일 ~ 종료일 |
| 시작일 제약 | 내일(오늘+1) 이후만 선택 가능, 초기값은 내일 |
| 종료일 제약 | 시작일 선택 후에만 선택 가능, 최소 시작일+6일 (작심삼일 2회 보장) — 위반 시 `CR024 CREW_DURATION_TOO_SHORT` |
| 기간 제한 | 크루 최대 기간 yml에서 설정 (crew.max-duration-days, 기본값 30일) — 초과 시 `CR016 CREW_DURATION_TOO_LONG` |
| 인증 방식 | TEXT(텍스트 필수) / PHOTO(사진 필수 + 텍스트 선택) |
| 인증 내용 (verificationContent) | 필수 입력, 최대 50자 (크루원이 무엇을 인증해야 하는지 설명) |
| 초대코드 | 크루 생성 시 자동 발급 (6자리 영숫자, 0/O/I/L 제외) |
| 초대 링크 | 초대코드 기반 딥링크 생성 |
| 자동 가입 | 크루 생성자는 LEADER 역할로 자동 가입 |
| 카테고리 (category) | 필수 선택 — `EXERCISE` / `STUDY` / `LIFESTYLE` / `SELF_DEV` / `ETC` |
| 공개 설정 (visibility) | 선택 — `PUBLIC` / `PRIVATE` (기본값 `PRIVATE`) |
| 중간 가입 | 크루장이 허용/불가 설정 (allow_late_join) |

### 1.2 크루 참여

| 항목 | 내용 |
|------|------|
| 비공개 크루 참여 | 초대코드 입력 (POST /crews/join) |
| 공개 크루 참여 | (1) 검색 → 직접 가입 (POST /crews/{crewId}/join), (2) 초대코드 (POST /crews/join) — 공개 크루도 초대코드 유지 |
| 참여 조건 | 정원 미초과 + 크루 종료 3일 전까지 |
| 중간 가입 허용일 때 | 크루 시작 후에도 참여 가능 → 첫 인증 시 챌린지 자동 생성 |
| 중간 가입 불가일 때 | 크루 시작 전까지만 참여 가능 |
| 역할 | MEMBER로 자동 배정 |
| 중복 참여 | 동일 크루 중복 참여 불가 |
| 공통 검증 | 정원, 상태, 마감, 중복 검증은 Crew 도메인 모델에 위임 (가입 경로와 무관하게 동일) |

### 1.3 크루 조회

| 항목 | 내용 |
|------|------|
| 크루 목록 | 내가 참여 중인 크루 목록 |
| 크루 상세 | 크루 정보 + 멤버 목록 + 현재 챌린지 상태 |
| 크루 피드 | 크루원들의 인증 목록 + 나의 현황 |
| 크루 검색 | 공개(PUBLIC) + 모집중/중간가입 가능 크루 검색 (비로그인 허용) |

### 1.4 크루 수정

| 항목 | 내용 |
|------|------|
| 권한 | LEADER만 가능 |
| 상태 조건 | 크루 상태 = RECRUITING |
| 수정 가능 필드 | name, goal, verificationContent, category, visibility |
| 수정 방식 | 부분 수정 (PATCH 시맨틱) — null인 필드는 수정하지 않음 |
| 최소 필드 수 | 최소 1개 이상 필드 필수 (빈 body 거부) |
| 빈 값 검증 | 빈 문자열("") 또는 공백만 있는 값은 거부 |

### 1.5 크루 삭제

| 항목 | 내용 |
|------|------|
| 권한 | LEADER만 가능 |
| 상태 조건 | 크루 상태 = RECRUITING |
| 멤버 조건 | crew_member 수 = 1 (LEADER 본인만 존재) |
| 삭제 방식 | hard delete — Crew 엔티티 + crew_member(리더 본인) 레코드 DB에서 완전 삭제 |
| 크루원 존재 시 | 삭제 불가 → 409 Conflict |
| cascade 확인 | 삭제 시 관련 데이터 FK 관계 확인 필요 |

### 1.6 크루 탈퇴

| 항목 | 내용 |
|------|------|
| 권한 | MEMBER만 가능 (LEADER 탈퇴 불가 — `CR020 LEADER_CANNOT_LEAVE`) |
| RECRUITING | 무조건 탈퇴 가능 |
| ACTIVE + 챌린지 미시작 | 탈퇴 가능 — 챌린지 한 번도 시작 안 한 멤버는 이탈 허용 |
| ACTIVE + 챌린지 시작 / COMPLETED / FAILED | 탈퇴 거부 — `CR025 CANNOT_LEAVE_ACTIVE_CREW` |
| 챌린지 시작 판정 | `challenges` 테이블에 (user_id, crew_id) 레코드가 1건이라도 존재하면 "시작함"으로 본다 (상태 무관: IN_PROGRESS/SUCCESS/FAILED 모두 포함) |
| 탈퇴 방식 | crew_member 테이블에서 해당 유저의 레코드 삭제 + crews.current_members 감소 |
| LEADER 탈퇴 | 불가 — 크루 삭제(DELETE /crews/{crewId}) 또는 회원탈퇴 시 자동 위임(BE-P0-1) 사용 |
| 비멤버 탈퇴 | crew_member 레코드 없으면 `CR021 CREW_MEMBER_NOT_FOUND` |

### 1.7 크루 상태 전이

| 전이 | 트리거 | 시점 |
|------|--------|------|
| RECRUITING → ACTIVE | 스케줄러 (ActivateRecruitingCrewsScheduler) | 매일 00:00, start_date ≤ 오늘 |
| ACTIVE → COMPLETED | 스케줄러 (CompleteExpiredCrewsScheduler) | 매일 00:05, end_date < 오늘 |

- 서버 재시작 시 StartupCompensationRunner가 활성화 → 실패 → 종료 순서로 밀린 작업 보정
- RECRUITING 상태가 아닌 크루에 activate() 호출 시 CREW_NOT_RECRUITING 예외
- ACTIVE 상태가 아닌 크루에 complete() 호출 시 CREW_NOT_ACTIVE 예외

### 1.8 챌린지

| 항목 | 내용 |
|------|------|
| 생성 방식 | Lazy 생성 — 첫 인증 시 챌린지 자동 생성 (FindOrCreateActiveChallengeService) |
| 사이클 | 3일 단위 |
| 성공 조건 | 3일 연속 인증 완료 |
| 실패 시 | 현재 챌린지 FAILED 처리 (스케줄러), 다음 인증 시 새 챌린지 자동 생성 |
| 종료 조건 | 크루 기간 종료 시 진행 중 챌린지도 종료 |
| 작심삼일 표시 | 3회 달성 시 UI에 표시 |

### 1.9 일일 인증

| 항목 | 내용 |
|------|------|
| 횟수 | 하루에 1번만 가능 |
| 텍스트 인증 | 텍스트 입력 → 바로 인증 완료 |
| 사진 인증 | 업로드 세션 → S3 업로드 → 인증 완료 |
| 마감 시간 | 크루의 deadlineTime 기준 (미지정 시 23:59:59) |
| 상태 | 생성 시 APPROVED (기본값) |

### 1.10 크루 내 상호 응원

- Phase 1: 좋아요
- Phase 2: 이모지 확장 검토 (확장 가능하게 설계)

### 1.11 알림 및 리마인더 시스템

| 항목 | 규칙 |
|------|------|
| 크루 시작 알림 | 매일 09:00, startDate = 오늘인 크루의 전체 멤버에게 발송 |
| 인증 마감 리마인더 | 매 15분, deadlineTime 15~30분 전 미인증자 대상 |
| FCM 토큰 관리 | PATCH /users/me/fcm-token으로 등록/갱신, nullable |
| 인앱 알림 | 목록 조회 (페이지네이션), 안 읽은 수, 읽음 처리 |
| 장애 격리 | TransactionTemplate 개별 트랜잭션 — 한 건 실패해도 나머지 계속 발송 |
| 알림 타입 | CREW_STARTED, REMINDER, CHALLENGE_SUCCESS, CHALLENGE_FAILED (추후 VERIFICATION_APPROVED 등 확장) |
| 메시지 템플릿 | 랜덤 메시지 선택, {crewName} 플레이스홀더 치환 |
| 발송 방식 | 인앱 알림 저장 → FCM 푸시 (best-effort, 실패해도 인앱은 유지) |
| 챌린지 성공 알림 | SUCCESS 시 인앱 알림 + FCM 발송 (인증 완료 시점) |
| 챌린지 실패 알림 | FAILED 시 인앱 알림 + FCM 발송 (스케줄러에서 실패 처리 시점) |
| 알림 전체 삭제 | 본인 알림 전체 Hard Delete, 0건이어도 200 OK (멱등) |
| 알림 전체 읽음 | 본인의 is_read=false 알림 일괄 true 갱신, 0건이어도 200 OK (멱등) |
| 읽음 필터 조회 | 기존 목록 API에 isRead 파라미터 추가 — null: 전체, false: 안 읽은 것만, true: 읽은 것만 |

### 1.12 회원가입/로그인

**공통**

| 항목 | 내용 |
|------|------|
| 방식 | 카카오 / Apple 소셜 로그인 |
| 플로우 | 소셜 로그인 → 신규 유저면 isNewUser=true 반환 → 별도 회원가입 API 호출 |
| 약관 동의 | termsAgreed=true 필수, 서버에서 검증, terms_agreed_at 타임스탬프 저장 |
| 닉네임 규칙 | 2~12자, 한글/영문/숫자/언더스코어만 허용, 앞뒤 공백 트림 |
| 기존 유저 | terms_agreed_at=NULL이어도 정상 로그인 가능 (이미 동의한 것으로 간주) |

**카카오**

| 항목 | 내용 |
|------|------|
| 회원가입 요구사항 | 카카오 토큰 + 닉네임 + 약관 동의 |
| kakaoId 검증 | 회원가입 시 카카오 API 재호출하여 요청의 kakaoId와 일치 여부 검증 |
| 저장 정보 | 닉네임(사용자 입력), 이메일/프로필이미지(카카오에서 가져옴), 약관 동의 일시 |

**Apple**

| 항목 | 내용 |
|------|------|
| 회원가입 요구사항 | identityToken + appleId + 닉네임 + 약관 동의 + **authorizationCode** |
| identityToken 검증 | Apple JWKS 공개키로 RS256 서명 검증 (sub, email 추출) |
| appleId 검증 | identityToken에서 추출한 sub와 요청의 appleId 일치 여부 검증 |
| **authorizationCode 교환** | 회원가입 시점에 Apple `/auth/token` 호출(`grant_type=authorization_code`)로 **refresh_token 발급받아 저장** — 회원탈퇴 시 revoke 호출에 사용 |
| **authorizationCode (로그인)** | 옵셔널. 기존 사용자가 로그인 시 함께 보내면 refresh_token 갱신·저장(backfill). 누락 시 기존 흐름 그대로 |
| email 동기화 | Apple은 최초 1회만 email 제공. 재로그인 시 email=null 가능 (기존값 유지) |
| 프로필 이미지 | Apple 미제공 (profileImageUrl은 항상 null) |
| Apple Client Secret | revoke/token 호출 시마다 ES256 JWT 즉석 생성 (캐싱 안 함, exp = now + 5분) |

**Apple authorizationCode 교환 실패 정책**

| 시점 | 정책 |
|------|------|
| 회원가입 (`/auth/apple-signup`) | **차단** — `APPLE_TOKEN_EXCHANGE_ERROR`로 회원가입 실패 처리. refresh_token 없이 가입하면 향후 탈퇴 시 revoke 불가하므로 |
| 로그인 backfill (`/auth/apple`) | **무시** — backfill은 best-effort. 교환 실패해도 로그인은 정상 진행. WARN 로그만 |

> 상세 설계는 [docs/spec/user.md](user.md) 참고

### 1.13 크루 검색

| 항목 | 내용 |
|------|------|
| 검색 대상 | `visibility = PUBLIC` AND (`status = RECRUITING` OR (`status = ACTIVE` AND `allowLateJoin = true` AND 잔여일 ≥ 6)) |
| 비로그인 허용 | permitAll — 인증 없이 검색 가능 |
| 검색 필터 | 키워드 (이름/목표 LIKE 검색), 카테고리 |
| 페이지네이션 | hasNext boolean, 기본 20건, 최대 50건 |
| 정렬 | createdAt DESC |
| 잔여일 임계값 | 설정값으로 외부화 (`crew.search.min-remaining-days`, 기본값 6) |

**카테고리 (CrewCategory enum):**

| 값 | 설명 |
|------|------|
| `EXERCISE` | 운동 |
| `STUDY` | 공부 |
| `LIFESTYLE` | 생활습관 |
| `SELF_DEV` | 자기개발 |
| `ETC` | 기타 |

**기존 크루 처리 (마이그레이션):**
- `category = null` (nullable — 기존 크루는 카테고리 미지정)
- `visibility = PRIVATE` (기존 크루는 검색에 노출되지 않음)

### 1.14 회원탈퇴

| 항목 | 내용 |
|------|------|
| 리더 + 다른 멤버 있음 | **자동 위임** — `joined_at` 기준 가장 오래된 멤버에게 LEADER 역할 + `crews.creator_id`를 이관한 뒤 탈퇴자는 크루에서 제거. 위임 대상은 탈퇴자를 제외한 멤버 중에서 선정 |
| 리더 + 혼자 | 크루 + 연관 데이터(crew_members, challenges, verifications) 하드 삭제 후 탈퇴 |
| MEMBER | 크루에서 제거 후 탈퇴 |
| 개인정보 초기화 | 닉네임 → "탈퇴한 사용자", email/profileImageUrl/fcmToken → null, apple_refresh_token → null |
| 토큰 무효화 | tokenVersion 증가로 기존 accessToken/refreshToken 즉시 무효화 |
| **Apple 연결 해제** | provider=APPLE이고 apple_refresh_token이 있으면 Apple `/auth/revoke` 호출. 실패해도 탈퇴는 graceful 진행 (App Store 5.1.1(v) 요건) |
| 재가입 | 동일 소셜 계정으로 재가입 가능 (탈퇴 계정 재활성화, deleted_at → null) |
| 탈퇴 기록 | deleted_at에 탈퇴 일시 기록 (null이면 활성 사용자) |

**Apple revoke 처리 흐름**

1. 검증 단계 통과 (USER_NOT_FOUND, USER_WITHDRAWN)
2. provider == APPLE && apple_refresh_token != null → 트랜잭션 **밖에서** Apple `/auth/revoke` 호출 (실패 시 WARN 로그만, 예외 던지지 않음)
3. 트랜잭션 안에서 크루 정리 + 개인정보 초기화 + tokenVersion++ + apple_refresh_token=null
4. 외부 API 호출과 DB 트랜잭션은 분리한다 (프로젝트 컨벤션)

**Apple revoke 실패 정책**

| 상황 | 처리 |
|------|------|
| Apple `/auth/revoke` HTTP 200 | 정상 — 진행 |
| Apple `/auth/revoke` 실패 (네트워크/4xx/5xx) | WARN 로그 + 탈퇴 계속 진행. App Store는 "성실한 시도"를 요구하므로 시도 자체가 핵심 |
| apple_refresh_token == null (기존 사용자) | revoke 미호출 — 다음 로그인 시 backfill되지만 그 전 탈퇴는 어쩔 수 없음 |
| provider == KAKAO | revoke 미호출 |

### 1.15 프로필 이미지 관리

**카카오 프로필 이미지 동기화 정책**

| 시점 | 동작 |
|------|------|
| 회원가입 (POST /auth/signup) | 카카오 프로필 이미지 URL 저장 (최초 1회) |
| 재가입 (reactivate) | 카카오 → 카카오 프로필 이미지 저장, Apple → null |
| 로그인 (POST /auth/kakao) | 프로필 이미지·email 모두 건드리지 않음 (최초 가입 시에만 저장) |

**프로필 이미지 변경**

| 항목 | 규칙 |
|------|------|
| 권한 | 인증된 사용자 (본인만) |
| 업로드 방식 | Presigned URL → S3 직접 업로드 (기존 StoragePort 재사용) |
| 이미지 제약 | 최대 5MB, jpg/jpeg/png/webp |
| S3 경로 | profiles/{userId}/{uuid}.{ext} |
| 기존 이미지 | 새 이미지 업로드 시 기존 이미지 유지 (삭제는 Phase 2) |
| 기본 이미지 | profileImageUrl이 null이면 FE에서 기본 아바타 표시 |
| URL 검증 | imageUrl이 값이 있으면 S3 버킷 도메인 + profiles/{userId}/ 경로 검증 (외부 URL/타인 경로 차단) |

**업로드 흐름**
1. POST /users/me/profile-image/upload-session → presignedUrl 수신
2. S3에 직접 업로드 (PUT presignedUrl)
3. PATCH /users/me/profile-image → imageUrl 확정 (DB 업데이트)

> SSE/Lambda 콜백 불필요. 클라이언트가 S3 업로드 성공 후 바로 PATCH 호출.

---

## 2. 인증 방식 및 상태 정의

### 2.1 인증 방식

크루 생성 시 크루장이 선택한다.

| 모드 | 필수 | 선택 |
|------|------|------|
| 텍스트 인증 (TEXT) | 텍스트 | - |
| 사진 인증 (PHOTO) | 사진 | 텍스트 |

### 2.2 인증 업로드 흐름

**텍스트 인증:**
```
인증 버튼 클릭 → POST /verifications (텍스트 포함) → 인증 완료
```

**사진 인증:**
```
인증 버튼 클릭 → POST /upload-sessions (URL 발급)
→ S3 직접 업로드
→ POST /verifications (이미지 key + 텍스트 선택)
→ 인증 완료
```

### 2.3 상태 정의

**upload_session 상태:**

| 상태 | 의미 |
|------|------|
| PENDING | presignedUrl 발급, S3 업로드 대기 |
| COMPLETED | S3 업로드 완료 (verification 생성 가능) |
| EXPIRED | 시간 초과 / 만료 |

**verification 상태:**

| 상태 | 의미 |
|------|------|
| APPROVED | 정상 인증 (기본값) |
| REPORTED | 신고 접수됨 (3건 이상) |
| HIDDEN | 검토 중 숨김 처리 |
| REJECTED | 검토 후 반려됨 |

**핵심 규칙:**
- S3 업로드 성공 후에만 verification 생성
- S3 업로드 완료 시 Lambda가 upload_session을 COMPLETED로 전환. verification 생성 시 session COMPLETED 확인만 수행, 중복 사용은 DB UNIQUE constraint(verification.upload_session_id)로 방지
- upload_session과 verification은 별도 API로 분리

### 2.4 마감 시간 기준

| 상황 | 처리 |
|------|------|
| 9:59 요청, 10:01 업로드 완료 | ✅ 인증 성공 (upload_session.requested_at 기준) |
| 9:59 요청, 업로드 안 함 | ⏰ EXPIRED 처리 |
| 10:01 요청 | ❌ 서버에서 마감 지남 → URL 발급 거부 |

- 인증 시간 기준: upload_session.requested_at (서버가 기록, 조작 불가)
- Grace Period: challenge.deadline + 5분 (텍스트/사진 인증 모두 동일 적용)

---

## 3. 비기능 요구사항 (NFR)

### 3.1 인증 업로드 성공률 99% 이상 (Reliability)

- 사용자의 노력(인증)이 시스템 문제로 무효화되면 안 된다
- S3 Direct Upload(Pre-signed URL) 방식으로 업로드 경로 단순화
- 클라이언트에서 이미지 압축 필수 (Phase 1 정책)
  - maxWidth: 960px
  - imageQuality: 70
  - 목표 크기: 300KB ~ 700KB
  - 최종 업로드 최대 크기: 1MB (클라이언트 기준)
  - 서버 허용 최대 크기: 5MB (안전마진, MAX_FILE_SIZE)
- Phase 1에서는 썸네일 생성하지 않음 — 압축된 원본 1장만 업로드
- 허용 확장자: jpg, jpeg, png, webp

### 3.2 피드 조회 응답 시간 300ms 이내 (Performance)

- DB 병목 방지를 위해 인덱스 최적화 + 페이지네이션 (20건)
- Phase 2에서 Redis 캐시 확장 가능하도록 사전 설계

### 3.3 인증 중복 0건 보장 (Consistency)

- 인증 데이터는 통계/랭킹/신뢰도 기반
- UNIQUE 제약 조건 + 멱등성 키 + 분산 락으로 보장

### 3.4 시스템 가용성 99% 이상 (Availability)

- 마감 시간대에 장애는 곧 인증 실패 → 서비스 신뢰도 하락
- Phase 1: 단일 서버, stateless 구조 유지

---

## 4. 엣지케이스

### 4.1 크루 정원 초과 참여

- **영향:** 공정성 붕괴
- **대응:** 낙관적 락(`@Version`) + 재시도(최대 3회) + 트랜잭션 처리
  - crews 테이블 `version` 컬럼으로 동시 수정 감지
  - UPDATE 시 `WHERE version = ?` 조건 — 버전 불일치 시 재시도
  - 3회 재시도 실패 시 `CREW_JOIN_CONFLICT(409, CR023)` 응답
  - Phase 1 적정 기술: 동시 가입 빈도 낮고, 재시도 비용 < 락 대기 비용

### 4.2 마감 직전 동시 인증 폭주

- **시나리오:** 마감 5초 전 인증 폭풍으로 DB Connection Pool 부족
- **대응:**
  - 마감시간 1시간 전 알람
  - Grace Period: 마감 넘어도 5분간 인증 처리
  - Phase 2: Write Queue 도입 검토

### 4.3 S3 업로드 성공, DB 저장 실패

- **시나리오:** 클라이언트는 S3 업로드 성공했으나 /verifications 요청이 실패
- **대응:**
  - /verifications는 upload_session이 COMPLETED인지 확인만 하고 인증 생성에 집중. 중복 사용은 DB UNIQUE constraint(verification.upload_session_id)로 방지
  - 실패 시 upload_session은 COMPLETED 유지, 클라이언트는 Idempotency-Key로 재시도 가능
  - UNIQUE 충돌 등 영구적 오류는 즉시 실패 처리

### 4.4 신고 시스템 악용

- **시나리오:** 악의적 신고 (3건 이상)로 정상 인증이 REPORTED 전환
- **대응:**
  - Phase 1: 신고 접수 + 크루장 검토
  - 신고자 중복 체크 (1인 1신고)
  - 신고 이력 추적

---

## 5. Fallback 등급 (S3 장애 시 대응)

Circuit OPEN 시 단계별 기능 축소 전략.
조건: 사진 필수 규칙 유지

### Level 1 (Best Effort) — "잠깐 흔들림, 금방 회복"

**상황:** S3 일시 오류/지연

**흐름:**
1. POST /upload-sessions → upload_session = PENDING, presignedUrl 반환
2. Client → S3 PUT 일시 실패
3. UX: "인증 접수 완료! 이미지를 업로드 중이에요. 잠시만 기다려주세요"
4. Client가 앱 레벨에서 자동 재시도 (Exponential Backoff + Jitter, 3~5회)
5. S3 업로드 성공 → POST /verifications → verification 생성 (APPROVED)

**상태 흐름:** PENDING → COMPLETED

### Level 2 (Reduced Function) — "유예시간 제공"

**상황:** S3 장애 지속

**흐름:**
1. POST /upload-sessions → upload_session = PENDING
2. S3 업로드 계속 실패, 클라이언트 재시도 모두 실패
3. UX: "⚠️ 업로드가 지연되고 있어요. 오후 11시까지 사진을 추가해주세요"
4. 유예시간 내 재업로드 (필요하면 새 presignedUrl 재발급)
5. S3 성공 → POST /verifications → verification 생성 (APPROVED)

**유예 기준:** challenge.deadline + 1시간

**상태 흐름:** PENDING → COMPLETED

### Level 3 (Minimal) — "최소 안내 + 재시도 버튼"

**상황:** Circuit OPEN 장기 지속, S3 심각한 장애

**흐름:**
1. POST /upload-sessions → upload_session = PENDING
2. S3 업로드 계속 실패 (장애 지속)
3. 마감 이후에도 유예시간까지 재시도 가능하지만 계속 실패
4. 유예시간까지도 실패 → upload_session = EXPIRED, verification 생성 안 됨

**UX:** "❌ 지금 서버 문제로 사진 업로드가 안 되고 있습니다. 유예 시간까지 재시도 가능합니다 [지금 다시 업로드]"

**상태 흐름:** PENDING → EXPIRED

### Fallback 상태 요약

| Level | 상황 | upload_session | verification |
|-------|------|---------------|-------------|
| Level 1 | S3 일시 오류 | PENDING → COMPLETED | ✅ 생성 |
| Level 2 | S3 장애 지속 | PENDING → COMPLETED | ✅ 유예시간 내 생성 |
| Level 3 | S3 심각한 장애 | PENDING → EXPIRED | ❌ 생성 안 됨 |

---

## 6. 스케줄러 복원력 정책

### 6.1 청크 처리 전략 (ChunkProcessor)

모든 배치 스케줄러는 `ChunkProcessor`를 사용하여 대량 건을 안전하게 처리한다.

| 항목 | 내용 |
|------|------|
| 청크 크기 | 50건 단위 |
| 트랜잭션 | 청크 단위로 별도 트랜잭션 (TransactionTemplate) |
| 실패 격리 | 청크 실패 시 해당 청크만 건별 분리 재시도 |
| Dead Letter | 건별 재시도에서도 실패한 건은 dead_letters 테이블에 기록 |

**처리 흐름:**
```
1. 전체 대상 조회 (트랜잭션 밖)
2. 50건씩 청크로 분할
3. 청크 단위 트랜잭션 실행
   → 성공: 다음 청크로
   → 실패: 해당 청크 건별 분리 재시도
      → 건별 성공: 카운트
      → 건별 실패: Dead Letter 기록
```

### 6.2 Dead Letter 정책

배치 처리에서 최종 실패한 건을 영구 기록하여 운영 모니터링 및 수동 복구에 사용한다.

| 항목 | 내용 |
|------|------|
| 기록 시점 | ChunkProcessor 건별 재시도에서도 실패한 건 |
| 초기 상태 | PENDING |
| retry_count | 0 (생성 시) |
| max_retries | 3 (기본값) |
| next_retry_at | 생성 시각 + 10분 |
| task_type | CHALLENGE_FAIL, CREW_ACTIVATE, CREW_COMPLETE, SESSION_EXPIRE, CREW_START_NOTIFICATION, REMINDER, CHALLENGE_NOTIFICATION |

#### 상태 전이 규칙

```
PENDING → retry() → PENDING  (retryCount < maxRetries)
PENDING → retry() → ABANDONED (retryCount ≥ maxRetries)
PENDING → resolve() → RESOLVED (수동 해결)
```

- `retry()`: retryCount 증가 + nextRetryAt 지수 백오프 (`10 × 2^retryCount` 분)
  - retryCount가 maxRetries에 도달하면 ABANDONED으로 전이
- `resolve()`: 운영자 수동 해결 시 RESOLVED로 전이
- PENDING이 아닌 상태에서 retry()/resolve() 호출 시 IllegalStateException

### 6.3 스케줄러별 실행 전략

| 스케줄러 | 주기 | 조회 방식 | 설명 |
|----------|------|-----------|------|
| ActivateRecruitingCrewsScheduler | 매일 00:00 | start_date ≤ 오늘 | RECRUITING → ACTIVE |
| CompleteExpiredCrewsScheduler | 매일 00:05 | end_date < 오늘 | ACTIVE → COMPLETED + IN_PROGRESS 챌린지 ENDED |
| FailExpiredChallengesScheduler | 매 5분 | 전량 스캔 (Phase 1) | deadline 초과 + 미인증 챌린지 FAILED |
| ExpireUploadSessionScheduler | 매 5분 | 전량 스캔 (Phase 1) | 15분 경과 PENDING 세션 EXPIRED |

**전량 스캔 (Phase 1, 500명 규모):**
- FailExpiredChallenges: 마감 + grace 5분 초과한 IN_PROGRESS 챌린지 모두 조회
- ExpireUploadSession: 15분 이전에 생성된 PENDING 세션 모두 조회
- 이유: 윈도우 조회는 한 틱 누락 시(GC pause/배포/지연) 영구 미판정 위험 → 전량 스캔으로 회귀 (DOM-C2, 2026-04-09 PR review)
- 부하 분산용 윈도우+보정 이중 구조는 후속 과제 (`/docs/log/future-considerations.md` 2026-04-09 참조)

### 6.4 서버 시작 보정 (StartupCompensationRunner)

서버 재시작/배포로 스케줄러가 누락한 작업을 보정한다. `ApplicationReadyEvent`에서 실행.
BC별로 Runner를 분리하여 Bounded Context 경계를 유지한다.

**Crew StartupCompensationRunner (`@Order(1)`):**
1. 크루 활성화 보정 (RECRUITING → ACTIVE)
2. 챌린지 실패 보정 (미인증 → FAILED)
3. 크루 종료 보정 (ACTIVE → COMPLETED)

**Verification StartupCompensationRunner (`@Order(2)`):**
1. 업로드 세션 만료 보정 (PENDING → EXPIRED)

**보정 조회 방식:** 윈도우 제한 없이 전체 미처리 건 조회 (서버 다운 기간이 길 수 있으므로).
**장애 격리:** 한 단계 실패해도 다음 단계 계속 진행. Runner 간에도 독립 실행.

---

## 7. Phase 로드맵

### Phase 1 (MVP) — 현재

핵심 Happy Path: **크루 생성 → 참여 → 챌린지 → 인증 → 피드 조회**

| 기능 | 포함 |
|------|------|
| 크루 생성 | 이름, 목표, 인원, 기간, 인증방식, 중간가입 설정, 초대코드 |
| 크루 참여 | 초대코드 입력, 중간 가입 허용/불가 |
| 챌린지 | 개인별 3일 사이클, 실패 시 자동 재시작 |
| 인증 | 텍스트/사진, 하루 1회 |
| 피드 조회 | 나의 현황 + 크루원 달성률 + 인증 피드 |
| 좋아요 | 크루 내 상호 응원 |
| 로그인 | 카카오 소셜 로그인 |
| 알림 시스템 | 크루 시작 알림, 인증 마감 리마인더, FCM 푸시, 인앱 알림 |

### Phase 2 — 확장

| 기능 | 설명 |
|------|------|
| 전체 크루 탐색 | 공개 크루 생성/검색/조회 |
| 신고 / 검토 | Moderation Context (신고 접수 → 크루장 검토 → 숨김/반려) |
| 알림 확장 | 추가 알림 타입 (인증 승인/반려, 크루 활동 등) |
| 인증 이모지 | 좋아요 → 이모지 확장 (🔥👏💪 등) |
| 캐시 | Redis 캐시 (피드 조회, 크루 목록) |
| 중간 가입 세부 설정 | 크루장이 참여 조건 커스터마이징 |

### Phase 3 — 고도화

| 기능 | 설명 |
|------|------|
| AI 인증 검증 | OpenAI Vision으로 사진 자동 검증 |
| 통계 대시보드 | 개인/크루 달성률 통계 |
| 랭킹 시스템 | 크루 간 랭킹 |
| 동시성 고도화 | 크루 가입 동시 1,000+ 시 Redis INCR 선착순 전환 |
