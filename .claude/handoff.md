# Handoff: 크루 검색 기능 — 스펙 완료, 백엔드 구현 대기

> 브랜치: `feat/cicd-docker` (크루 검색 작업은 아직 별도 브랜치 미생성)

---

## 이번 세션 완료 작업

### 크루 검색 스펙 + 문서 (100% 완료)

1. **api-spec.md** — GET /crews/search, POST /crews/{crewId}/join 스펙 추가 + 기존 API에 category/visibility 필드 추가
2. **biz-logic.md** — 섹션 1.13 크루 검색 비즈니스 규칙 추가
3. **schema.md** — crews 테이블에 category/visibility 컬럼, enum 정의, 검색 인덱스 추가
4. **V10 마이그레이션** — `V10__add_category_visibility_to_crews.sql` 생성
5. **프론트 지시서** — `docs/guide/frontend-crew-search-handoff.md` 생성

### 미커밋 변경 파일

| 파일 | 상태 |
|------|------|
| docs/spec/api-spec.md | Modified |
| docs/spec/biz-logic.md | Modified |
| docs/spec/schema.md | Modified |
| src/main/resources/db/migration/V10__*.sql | New |
| docs/guide/crew-search-system-design .md | New (설계 문서) |
| CLAUDE.md | Modified (크루 검색 API 추가) |

---

## 다음 단계: 백엔드 구현

### 1. 도메인 모델 변경

- `CrewCategory` enum 생성 (EXERCISE, STUDY, LIFESTYLE, SELF_DEV, ETC)
- `CrewVisibility` enum 생성 (PUBLIC, PRIVATE)
- `Crew` 도메인 모델에 category, visibility 필드 추가
- `CrewJpaEntity`에 컬럼 매핑 추가

### 2. 크루 생성/수정 반영

- `CreateCrewRequest`에 category(필수), visibility(선택) 추가
- `UpdateCrewRequest`에 category, visibility 추가
- 기존 응답 DTO에 category, visibility 필드 추가

### 3. 크루 검색 API 구현

- `GET /crews/search` — permitAll, 키워드+카테고리+상태 필터, cursor 페이지네이션
- `POST /crews/{crewId}/join` — 공개 크루 직접 가입 (초대코드 없이)
- MyBatis로 검색 쿼리 구현 (복잡한 조회)
- ErrorCode CR022 (CREW_NOT_PUBLIC) 추가

### 4. SecurityConfig 변경

- `/crews/search` permitAll 추가
- `/crews/{crewId}/join` 인증 필요

---

## 기존 미해결 항목

### 크루 최소 기간 미검증 버그 (이전 핸드오프에서 이관)

- **파일**: `crew/domain/model/Crew.java:171-178`
- **현재**: `endDate > startDate`만 체크
- **정본(biz-logic.md)**: "최소 시작일+6일 (작심삼일 2회 보장)"
- **수정**: `endDate >= startDate + 6` 검증 추가