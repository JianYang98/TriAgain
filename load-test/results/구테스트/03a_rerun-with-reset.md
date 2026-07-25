# 재측정 — 리셋 스크립트 도입 + SQL 수정 + Warm-up (2026-04-16)

- 일시: 2026-04-16 (Day 2)
- 서버: EC2 t2.micro (1 vCPU, 1GB RAM) — 어제와 동일
- 프로필: prod,loadtest
- 데이터: S단계 (유저 50, 크루 10)
- 스크립트: `load-normal.js`, `load-peak.js` (TARGET_VUS=50, DURATION=2m)
- 원본 로그: `results/raw/03a_normal-50vu-warmed.log`, `03a_peak-50vu-warmed.log`

---

## 1. 배경 — 왜 재측정했나

어제 `03_load-peak.md`는 피크 시나리오에서 에러율 2.14% / 6.82% 로 **FAIL** 판정을 냈다. 그런데 세부를 보면 `verify_duplicate` (HTTP 409) 카운트가 압도적이었다.

### 근본 문제 3가지

1. **비즈니스 거절을 서버 에러로 오인**
   - `verifications(user_id, crew_id, target_date)` UNIQUE 제약 — `VerificationJpaEntity:18`
   - 거절 지점 — `CreateVerificationService:70` → `VERIFICATION_ALREADY_EXISTS` (409)
   - k6의 기본 threshold `http_req_failed < 0.01` 는 **모든 non-2xx**를 실패로 간주 → 서버가 UNIQUE 제약대로 정상 거절한 409까지 "에러"에 포함시킴
   - 실제 판정은 `checks_failed` (201/409 외 응답)를 기준으로 해야 서버 건강성을 올바르게 본다

2. **반복 불가능**
   - UNIQUE 제약 때문에 동일 유저가 하루에 한 번만 인증 가능
   - 한 번 돌리면 S 스케일 50유저 전원 소진 → 이후 전부 409
   - 테스트마다 수치가 달라져 재현성 없음

3. **PHOTO 20% 오염 (재측정 준비 중 발견)**
   - `02_crews.sql:35`: `v_vtype := CASE WHEN i % 5 = 0 THEN 'PHOTO' ELSE 'TEXT' END;`
   - 모든 스케일(S/M/L/XL)에서 20%의 크루가 PHOTO 인증 요구
   - k6 `writeScenario`는 TEXT(`textContent`만 송신) 전용 → PHOTO 크루는 400 `PHOTO_REQUIRED` 반환
   - 500ms 같은 서버 에러는 아니지만 `http_req_failed`·check 실패에 집계되어 수치 왜곡

---

## 2. 세 가지 준비 작업

### (A) `sql/08_reset_api_verifications.sql` 신설

테스트 사이마다 실행 → 오늘자 인증 삭제 + 챌린지 IN_PROGRESS 복구 (idempotent).

```sql
DELETE FROM verifications
 WHERE user_id LIKE 'loadtest-user-%'
   AND crew_id LIKE 'loadtest-crew-%'
   AND target_date = CURRENT_DATE;

UPDATE challenges
   SET completed_days = 0,
       status = 'IN_PROGRESS',
       deadline = CURRENT_DATE + TIME '23:59:59'
 WHERE user_id LIKE 'loadtest-user-%'
   AND crew_id LIKE 'loadtest-crew-%';
```

`LIKE 'loadtest-crew-%'` 접두사 필터는 스케줄러(`loadtest-sched-crew-*`), 러시(`loadtest-rush-crew-*`) 데이터를 건드리지 않음 — prefix 매트릭스 격리.

### (B) `02_crews.sql` 수정 — PHOTO 20% → 전부 TEXT

```diff
-         v_vtype := CASE WHEN i % 5 = 0 THEN 'PHOTO' ELSE 'TEXT' END;
+         v_vtype := 'TEXT';
```

PHOTO 인증은 S3 Pre-signed URL + UploadSession 파이프라인이 별개 부하 특성이므로, 동일 writeScenario 내에서 섞어 측정하면 의미가 없음. PHOTO는 별도 시나리오로 분리하는 게 맞음(future work).

### (C) 재측정 전 10 VU 1분 Warm-up

JVM 재기동 직후 첫 테스트는 JIT 컴파일 + HikariCP pool initialization 부담이 크다. 실제 첫 시도에서 Normal 50VU p95 A = **327ms** (!) 가 나왔고, 이어진 Peak는 71ms. 같은 부하량인데 순서만 다른데 4배 차이.

→ **본 테스트 전 `load-normal.js --env TARGET_VUS=10 --env DURATION=1m` 로 워밍업 필수**로 정착.

---

## 3. 재측정 결과 (warm-up + 08 리셋 + SQL 수정 적용)

### Normal (A:B = 90:10) 50 VU 2분

| 지표 | 결과 | 기준 | 판정 |
|------|------|------|------|
| p95 (A 읽기) | 102ms | <200ms | ✅ PASS |
| p95 (B 쓰기) | 121ms | <500ms | ✅ PASS |
| `verify_created` | **50** | 50 (전 유저-크루) | ✅ 100% |
| `verify_duplicate` | 309 | - | 정상 (50 소진 후 자연 발생) |
| `checks_failed` (서버 에러) | **0** | <1% | ✅ PASS |
| TPS | 892/s | - | - |

### Peak (A:B = 40:60) 50 VU 2분

| 지표 | 결과 | 기준 | 판정 |
|------|------|------|------|
| p95 (A 읽기) | 57ms | <200ms | ✅ PASS |
| p95 (B 쓰기) | 79ms | <500ms | ✅ PASS |
| `verify_created` | **50** | 50 | ✅ 100% |
| `verify_duplicate` | 1,929 | - | 정상 (write 60%라 더 많음) |
| `checks_failed` (서버 에러) | **0** | <1% | ✅ PASS |
| TPS | 816/s | - | - |

**결과 요약**: 두 시나리오 모두 **서버 에러 0건**. 어제 FAIL 판정은 전부 해석 오류였음이 확정됨.

---

## 4. 트러블슈팅 사례 2건 (블로그 소재)

### 사례 1 — "verify_created가 왜 40에서 멈췄나"

**증상**: 리셋 직후에도 매번 `verify_created=40`. 10개 빠짐. 어느 유저가 왜 빠지는지 불명확.

**가설 3개**:
1. k6 매핑(`config.js`)에서 일부 유저가 빠짐
2. DB UNIQUE 제약에 걸려 10개만 409
3. 서버 측 어떤 검증이 10개를 400으로 거절

**진단 — DB 직접 조회**:
```sql
SELECT cm.user_id, cm.crew_id
  FROM crew_members cm
  LEFT JOIN verifications v
    ON v.user_id = cm.user_id
   AND v.crew_id = cm.crew_id
   AND v.target_date = CURRENT_DATE
 WHERE cm.user_id LIKE 'loadtest-user-%'
   AND cm.crew_id LIKE 'loadtest-crew-%'
   AND v.id IS NULL
 ORDER BY cm.user_id;
```

결과: 빠지는 10명이 `loadtest-crew-5` 멤버 5명 + `loadtest-crew-10` 멤버 5명 — 완전히 구조적 패턴. 우연이 아니라 두 크루에 공통된 속성이 있다는 뜻.

```sql
SELECT id, verification_type FROM crews WHERE id IN ('loadtest-crew-5','loadtest-crew-10');
-- loadtest-crew-5  | PHOTO
-- loadtest-crew-10 | PHOTO
```

**원인 확정**: `02_crews.sql:35`의 `i % 5 = 0 ? PHOTO : TEXT` 로직이 크루-5·10(모든 5의 배수)을 PHOTO로 지정. k6 `writeScenario`는 `textContent`만 보내므로 PHOTO 크루에서 `CreateVerificationService:76` 가드에 걸려 400 `PHOTO_REQUIRED` → check 실패 → `verify_created` 카운트 안 됨.

**의사결정 — 3가지 옵션**:
| 옵션 | 내용 | 판단 |
|------|------|------|
| A | SQL에서 모두 TEXT로 | ✅ **채택** — write path 측정 통일 |
| B | k6에서 PHOTO 크루 스킵 | 실효 write rate 감소 |
| C | 별도 `write-photo.js` 시나리오 신설 | 범위 초과 (future work) |

**검증**: 수정 후 50/50 성공. 모든 스케일(S/M/L/XL)에서 20%가 영향받던 것을 원천 차단.

**교훈**: TPS·에러율이 "애매하게 낮은" 수치로 나올 때, k6 메트릭만 보지 말고 **DB에서 유저-크루 × 실제 행 조합** 으로 직접 차이를 조사하는 게 훨씬 빠르다.

### 사례 2 — "Normal p95 327ms vs Peak p95 71ms" 반전

**증상**: 서버 재기동 직후 첫 테스트(Normal 50VU) p95 A = 327ms. 이어서 돌린 Peak 50VU p95 A = 71ms. **더 write 많은 Peak가 4배 빠름**.

**가설**: JVM cold start — JIT 컴파일 전/후 + HikariCP pool warm-up 전/후 차이.

**검증**: 10 VU 1분 warm-up 후 Normal 재측정 → p95 A = 102ms 로 회복. Peak와 유사한 수준.

**원칙 정착**: **모든 부하테스트 전 warm-up 1분 필수** — 측정 대상은 "정상 운영 상태의 서버"이지 "콜드 스타트 직후의 서버"가 아님. 실 운영에서도 배포 직후 트래픽을 점진적으로 올리는 것과 같은 맥락.

**교훈**: 첫 테스트 수치가 이상하면 서버나 설정 문제를 의심하기 전에 "JVM이 따뜻해졌는지" 부터 확인.

---

## 5. 결론

- S 스케일 50 VU 기준, 서버 에러율 **0%**, p95 A 102ms / B 121ms (Normal) — 판정 **PASS**
- `http_req_failed` 전체값은 **비즈니스 거절(409) + 서버 에러(5xx) 혼합**이므로 서버 건강성 지표로 부적합. `checks_failed` (201/409 외 응답) 기준으로 재판정해야 옳음.
- 어제 오염 상태로 측정된 `01_vuser-fixed-normal.md`, `02_saturation.md`는 재측정 예정 (`01-clean.md`, `02-clean.md` 신규 생성). 원본은 보존하고 경고 박스로 교차 참조.
- 다음 단계: VU 100~300 Breaking Point 실측 (`03c_stress-breaking-point.md`).

---

## 참고 파일

- **신설**: `sql/08_reset_api_verifications.sql`
- **수정**: `sql/02_crews.sql:35` (PHOTO 20% → TEXT 100%)
- **원본 로그**: `results/raw/03a_normal-50vu-warmed.{log,json}`, `results/raw/03a_peak-50vu-warmed.{log,json}`
- **참조 코드**:
  - `k6/lib/scenarios.js:34` — writeScenario (textContent 전송)
  - `src/main/java/com/triagain/verification/application/CreateVerificationService.java:70` — 409 거절 지점
  - `src/main/java/com/triagain/verification/application/CreateVerificationService.java:76` — PHOTO 가드
  - `src/main/java/com/triagain/verification/infra/VerificationJpaEntity.java:18` — UNIQUE 제약
