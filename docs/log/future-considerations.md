# 추후 고려 사항

> 개발 중 나온 개선 아이디어나 스케일업 시 필요한 작업을 기록한다.
> 지금은 불필요하지만, 나중에 참고할 내용들.
> **최신 항목이 위에 오도록 추가한다.**

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