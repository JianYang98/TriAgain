# 운영 배포 체크리스트

## Profile별 설정 비교

| 구분 | 로컬/테스트 (`!prod`) | 운영 (`prod`) |
|------|----------------------|---------------|
| **보안** | `DevSecurityConfig` — JWT + `X-User-Id` fallback | `SecurityConfig` — JWT 전용 + `AuthEntryPoint` |
| **스토리지** | `LocalStorageAdapter` — localhost URL 반환 | `S3StorageAdapter` — AWS S3 presigned URL |
| **S3 설정** | 미사용 | `S3Config` — `S3Presigner` 빈 등록 |
| **DB DDL** | `ddl-auto: update` | `ddl-auto: validate` |
| **DB 접속** | 로컬 PostgreSQL (`triagain`/`triagain`) | `${DB_URL}`, `${DB_USERNAME}`, `${DB_PASSWORD}` |
| **JWT Secret** | 하드코딩 기본값 (Base64) | `${JWT_SECRET}` 환경변수 필수 |
| **AWS S3** | - | `aws.s3.bucket`, `aws.s3.region` |
| **SQL 로깅** | DEBUG (SQL 출력 + 바인드 파라미터) | `show-sql: false` |
| **로깅** | Hibernate SQL/TRACE | `root: WARN`, `com.triagain: INFO` |

---

## 필수 환경변수

| 변수 | 설명 | 예시 |
|------|------|------|
| `DB_URL` | PostgreSQL 접속 URL | `jdbc:postgresql://rds-endpoint:5432/triagain` |
| `DB_USERNAME` | DB 사용자명 | - |
| `DB_PASSWORD` | DB 비밀번호 | - |
| `JWT_SECRET` | JWT 서명 키 (Base64 인코딩, 256bit 이상) | - |
| `APPLE_REFRESH_KEY` | Apple refresh_token AES-256-GCM 암호화 키 (Base64 인코딩 32바이트) | `openssl rand -base64 32` |

---

## 보안 확인 사항

### X-User-Id 헤더 인증 — 운영에서 비활성화됨 (안전)
- `XUserIdAuthenticationFilter`는 `DevSecurityConfig`(`@Profile("!prod")`)에서만 등록
- 운영 `SecurityConfig`에는 **미등록** → `X-User-Id` 헤더로 인증 우회 불가

### /internal/** 엔드포인트 접근 제어 필요
- 현재 `permitAll()`로 설정되어 있음 (로컬/운영 동일)
- **AWS VPC Security Group으로 Lambda → API 서버만 접근 허용** 필수
- 외부에서 `/internal/upload-sessions/{id}/complete` 호출 시 세션 상태 조작 가능

### JWT Secret 관리
- 운영에서는 반드시 `${JWT_SECRET}` 환경변수로 주입
- AWS Secrets Manager 또는 Parameter Store로 관리 권장
- 기본값(하드코딩) 사용 절대 금지

### Apple refresh_token 암호화 키 관리 (`APPLE_REFRESH_KEY`)
- AES-256-GCM 컬럼 암호화에 사용 (`AesGcmStringConverter`)
- 32바이트 키를 Base64 인코딩한 문자열 (`openssl rand -base64 32`)
- 운영은 GitHub Actions Secrets → EC2 환경변수로 주입 (deploy.yml 처리)
- **키 손실 시 기존 암호화 데이터 복호화 불가** → 백업/로테이션 정책 필요
- Phase 2에서 KMS / Secrets Manager로 승급 예정

---

## 운영 전 확인 사항

- [ ] `spring.profiles.active=prod` 설정 확인
- [ ] 필수 환경변수 5개 (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, `APPLE_REFRESH_KEY`) 설정
- [ ] `aws.s3.bucket`, `aws.s3.region` 설정 (application-prod.yml에 하드코딩됨, 필요시 환경변수로 변경)
- [ ] DB 스키마 직접 생성 (`ddl-auto: validate`이므로 자동 생성 안 됨)
- [ ] DB 마이그레이션 도구 도입 검토 (Flyway/Liquibase)
- [ ] `/internal/**` 엔드포인트 네트워크 수준 접근 제어 (VPC Security Group)
- [ ] Lambda → API 서버 `/internal/upload-sessions/{id}/complete` 연결 확인
- [ ] S3 버킷 생성 + CORS 정책 설정 (Flutter 클라이언트 → S3 직접 업로드용)
- [ ] CORS 설정 (현재 Spring 레벨 미구현 — 클라이언트 도메인 허용 필요)
- [ ] SSL/HTTPS 설정 (ALB 또는 EC2 레벨)
- [ ] Health check 엔드포인트(`/health`) 동작 확인
- [ ] `server.forward-headers-strategy: framework` 적용 확인 (OG 절대 URL을 https로 생성하려면 ALB/nginx가 `X-Forwarded-Proto`/`X-Forwarded-Host`를 전달해야 함)
- [ ] 초대 OG 카드 반영: 배포 후 카카오 디벨로퍼스 캐시 초기화 도구(`https://developers.kakao.com/tool/clear/og`)에 초대 URL 입력 → 초기화 후 카톡에서 다크 카드 확인 (이미지 `?v=2`만으론 재스크랩 안 됨 — 카카오 캐시 키는 페이지 URL 기준)

---

## 크루 첫 인증 알림(CREW_FIRST_VERIFICATION) — prod 활성화 전

> 기능/스키마/문서는 PR #82에서 통과. 아래 항목 완료 후 `CREW_FIRST_VERIFICATION_ENABLED=true` 전환.

- [x] AsyncConfig 미처리 예외 핸들러 동작 확인 — `AsyncConfigurer.getAsyncUncaughtExceptionHandler()` 구현 완료 (PR #82 추가 커밋)
- [x] T6 실 commit/rollback 검증 (Route A) — `TestTransaction.flagForCommit()` + `CountDownLatch` 재작성, 3회 반복 그린 확인 (PR #82 추가 커밋)
- [ ] **T2 `findCrewFirstVerificationTargets` 네이티브 SQL 실DB 검증** — 현재 Mockito 단위테스트라 SQL 미검증. 활성화 전 TestContainers로 ACTIVE 필터 + excludeUserId 결과 실측 필요 (lessons-learned "네이티브 SQL 실DB 검증" 정합)
- [x] 타임존 KST 확정 — Dockerfile + ClockConfig 앵커링 이미 충족
- [ ] 알림 on/off UI 출시 → `CREW_FIRST_VERIFICATION_ENABLED=true` 환경변수 주입
