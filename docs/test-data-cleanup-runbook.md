# 테스트 데이터 정리 런북 — 크루 생성일(createdAt) 기준

> 운영 배포 전, **2026-05-01 이전에 생성된**(`crews.created_at` 기준) 내부 테스트 크루와 하위 데이터 삭제.
> 보존: `users`(회원). 알림은 **`created_at < 2026-06-01`(KST) 삭제**(날짜 기준 독립 정리, 이후 보존). 기준 필드: `crews.created_at`(시작일/종료일 아님).
>
> **이 문서는 STEP 2(영향 범위 진단)용 — 전부 SELECT, 삭제 없음.**
> STEP 3(백업)·STEP 4(삭제)는 별도. **백업 확인 전 DELETE 절대 금지.**

---

## 0. 사전 사실 (왜 이렇게 짰나)

- **DB 레벨 FK 제약 없음** (V1~V20 마이그레이션에 `FOREIGN KEY ... ON DELETE` 전무). cascade는 앱이 수동 관리 → 삭제 순서를 우리가 책임짐.
- **타임존**: 서버 KST(`Dockerfile` `/etc/localtime`→Asia/Seoul) + `hibernate.jdbc.time_zone` 미설정 + `created_at`은 `TIMESTAMP`(without tz). → 저장값이 곧 **KST 벽시계**.
  - 삭제 경계: `created_at < TIMESTAMP '2026-05-01 00:00:00'` (변환 불필요, 세션 tz 무관).
  - "오늘": 진단 쿼리의 `CURRENT_DATE`가 KST가 되도록 **세션 tz를 먼저 KST로 고정**한다.
- **인증사진 S3 키는 크루/날짜 스코프 아님** (`upload-sessions/{userId}/{uuid}.ext`) → prefix 일괄삭제 불가, DB에서 키를 뽑아야 함(STEP 3/4에서 처리). S3 삭제 코드는 백엔드에 없음 → 일회성 ops 스크립트로 처리 예정.

### 의존성 트리 (삭제 대상)
```
crews (created_at 기준)
 ├─ crew_members
 ├─ challenges
 │   └─ verifications (사진=S3)
 │       ├─ reactions
 │       └─ reports
 │           └─ reviews
 ├─ upload_session   (S3 image_key 출처 — 행까지 삭제)
 ├─ notifications    ← created_at<2026-06-01 삭제(날짜 독립), 이후 보존
 └─ dead_letters     ← 관련 행 삭제(철저 정리)
```

---

## STEP 2 — 진단 쿼리 (실행 순서대로)

### 0) 세션 타임존 고정 — **먼저 1회**
```sql
SET TIME ZONE 'Asia/Seoul';
```

### ① 삭제 규모 카운트
```sql
WITH target_crews AS (
    SELECT id FROM crews WHERE created_at < TIMESTAMP '2026-05-01 00:00:00'
)
SELECT
    (SELECT COUNT(*) FROM target_crews)                                                              AS crews,
    (SELECT COUNT(*) FROM crew_members  WHERE crew_id IN (SELECT id FROM target_crews))              AS crew_members,
    (SELECT COUNT(*) FROM challenges     WHERE crew_id IN (SELECT id FROM target_crews))             AS challenges,
    (SELECT COUNT(*) FROM verifications  WHERE crew_id IN (SELECT id FROM target_crews))             AS verifications,
    (SELECT COUNT(*) FROM verifications  WHERE crew_id IN (SELECT id FROM target_crews)
                                           AND upload_session_id IS NOT NULL)                        AS photo_verifications,
    (SELECT COUNT(*) FROM reactions WHERE verification_id IN
        (SELECT id FROM verifications WHERE crew_id IN (SELECT id FROM target_crews)))               AS reactions,
    (SELECT COUNT(*) FROM reports   WHERE verification_id IN
        (SELECT id FROM verifications WHERE crew_id IN (SELECT id FROM target_crews)))               AS reports,
    (SELECT COUNT(*) FROM reviews   WHERE report_id IN
        (SELECT id FROM reports WHERE verification_id IN
            (SELECT id FROM verifications WHERE crew_id IN (SELECT id FROM target_crews))))          AS reviews,
    (SELECT COUNT(*) FROM upload_session WHERE crew_id IN (SELECT id FROM target_crews)
         OR id IN (SELECT upload_session_id FROM verifications
                   WHERE crew_id IN (SELECT id FROM target_crews) AND upload_session_id IS NOT NULL)) AS upload_sessions;
```

### ② 경계 걸친 크루 눈검 (2026-04-28 ~ 2026-05-03)
> 경계 근처에 "분명히 오래된 크루"가 보존 쪽으로 잡히면 레거시 9시간 skew(UTC로 들어간 행) 의심 → 보고.
```sql
SELECT
    id, name, status, created_at, start_date, end_date,
    CASE WHEN created_at < TIMESTAMP '2026-05-01 00:00:00' THEN '삭제대상' ELSE '보존' END AS bucket
FROM crews
WHERE created_at >= TIMESTAMP '2026-04-28 00:00:00'
  AND created_at <  TIMESTAMP '2026-05-04 00:00:00'
ORDER BY created_at;
```

### ③ 보존 대상 교차 확인
> users는 무영향(보존). 알림은 **날짜 기준 정리로 변경**(`created_at < 2026-06-01` 삭제) — 규모는 ③-b 참조. (`notif_dangling_after_delete`는 옛 정책 참고용.)
```sql
SELECT
    (SELECT COUNT(*) FROM users)         AS users_total_unchanged,
    (SELECT COUNT(*) FROM notifications) AS notifications_total_unchanged,
    (SELECT COUNT(*) FROM notifications
        WHERE target_type = 'CREW'
          AND target_id IN (SELECT id FROM crews WHERE created_at < TIMESTAMP '2026-05-01 00:00:00')) AS notif_dangling_after_delete;
```

### ③-b 알림 정리 규모 (날짜 기준 — 크루 삭제와 독립)
> 알림은 `created_at < 2026-06-01`(KST) 삭제, 이후 보존. `kept_but_dangling_crew` = 보존되는데도 삭제된 크루를 가리켜 댕글링 남는 수(보통 0~소수).
```sql
SELECT
    (SELECT COUNT(*) FROM notifications WHERE created_at <  TIMESTAMP '2026-06-01 00:00:00') AS notif_to_delete,
    (SELECT COUNT(*) FROM notifications WHERE created_at >= TIMESTAMP '2026-06-01 00:00:00') AS notif_kept,
    (SELECT COUNT(*) FROM notifications)                                                     AS notif_total,
    (SELECT COUNT(*) FROM notifications
        WHERE created_at >= TIMESTAMP '2026-06-01 00:00:00'
          AND target_type = 'CREW'
          AND target_id IN (SELECT id FROM crews WHERE created_at < TIMESTAMP '2026-05-01 00:00:00')) AS kept_but_dangling_crew;
```

### ④ WP1 데이터 정합성 진단 — 정본 `sdd/home-crew-tabs/step3-schema.md` §2 (4종)
```sql
-- (1) 날짜 역전: 종료일 < 시작일
SELECT id, name, status, start_date, end_date, created_at
FROM crews WHERE end_date < start_date ORDER BY created_at DESC;

-- (2) status-날짜 불일치 (스케줄러 미동작/누락 흔적)
SELECT id, name, status, start_date, end_date
FROM crews
WHERE (status = 'ACTIVE'     AND end_date   < CURRENT_DATE)
   OR (status = 'RECRUITING' AND start_date < CURRENT_DATE);

-- (3) 최소 기간(시작일+6일) 위반
SELECT id, name, status, start_date, end_date, (end_date - start_date) AS duration_days
FROM crews WHERE (end_date - start_date) < 6 ORDER BY created_at DESC;

-- (4) COMPLETED인데 IN_PROGRESS 챌린지 잔존
SELECT c.id, c.name, COUNT(ch.id) AS in_progress_challenges
FROM crews c
LEFT JOIN challenges ch
   ON ch.crew_id = c.id
  AND ch.status = 'IN_PROGRESS'
WHERE c.status = 'COMPLETED'
GROUP BY c.id, c.name
HAVING COUNT(ch.id) > 0;
```

### ⑤ 깨진 크루 × 5월 삭제 대상 겹침
> broken = ④의 (1)+(2)+(3)+(4) 합집합.
> **inside** = 5월 삭제로 자동 정리(별도 보정 불필요) / **outside** = 보존되므로 SDD §3 보정 + §4 CHECK 제약 대상(남는 WP1).
```sql
WITH target AS (SELECT id FROM crews WHERE created_at < TIMESTAMP '2026-05-01 00:00:00'),
broken_crews AS (
    SELECT c.id
    FROM crews c
    WHERE c.end_date < c.start_date
       OR (c.status = 'ACTIVE'     AND c.end_date   < CURRENT_DATE)
       OR (c.status = 'RECRUITING' AND c.start_date < CURRENT_DATE)
       OR (c.end_date - c.start_date) < 6
       OR (c.status = 'COMPLETED' AND EXISTS (
              SELECT 1 FROM challenges ch
              WHERE ch.crew_id = c.id AND ch.status = 'IN_PROGRESS'))
)
SELECT
    (SELECT COUNT(*) FROM broken_crews)                                          AS broken_crews_total,
    (SELECT COUNT(*) FROM broken_crews WHERE id IN (SELECT id FROM target))      AS broken_inside_delete_window,
    (SELECT COUNT(*) FROM broken_crews WHERE id NOT IN (SELECT id FROM target))  AS broken_outside_remaining_wp1;
```

---

## STEP 3 — 백업 (완료)

- ✅ RDS 수동 스냅샷 `triagain-pre-cleanup-20260616` (인스턴스 `triagain-db`, PG 17.9) — 상태 `Available` 확인.
- ✅ S3 image_key 12개 확보(`upload_sessions=12` 일치). 4-C 목록에 박음.

---

## STEP 4 — 삭제 (⚠️ 백업 완료 후에만)

> 삭제 순서는 검증된 `CrewJpaAdapter.deleteCrewWithAssociations`(crew-solo-delete, TestContainers 검증)를 다중 크루·날짜 스코프로 확장.
> 차이: ① upload_session은 NULL화 대신 **행 삭제** ② dead_letters **추가 삭제**(검증 매핑) ③ 알림은 CREW-타겟 대신 **날짜(`<2026-06-01`)** 삭제.
> **실행법: 먼저 끝의 `ROLLBACK;`으로 dry-run → 출력된 `DELETE n` 카운트가 기대치와 맞으면 `ROLLBACK;`→`COMMIT;` 바꿔 재실행 → DB commit 후 4-C(S3).**
> 기대 카운트: verifications=23, challenges=19, upload_session=12, crew_members=31, crews=19, reviews/reports/reactions=0, notifications≈248.

### 4-A. DB 삭제 (단일 트랜잭션, dry-run)
```sql
BEGIN;

-- 0) 대상 동결 (삭제 중 id 유실 방지)
CREATE TEMP TABLE tmp_crews ON COMMIT DROP AS
    SELECT id FROM crews WHERE created_at < TIMESTAMP '2026-05-01 00:00:00';
CREATE TEMP TABLE tmp_challenges ON COMMIT DROP AS
    SELECT id FROM challenges WHERE crew_id IN (SELECT id FROM tmp_crews);
CREATE TEMP TABLE tmp_verifications ON COMMIT DROP AS
    SELECT id, upload_session_id FROM verifications WHERE crew_id IN (SELECT id FROM tmp_crews);
CREATE TEMP TABLE tmp_sessions ON COMMIT DROP AS
    SELECT id FROM upload_session
    WHERE crew_id IN (SELECT id FROM tmp_crews)
       OR id IN (SELECT upload_session_id FROM tmp_verifications WHERE upload_session_id IS NOT NULL);

-- 동결 규모 확인 (기대: 19 / 19 / 23 / 12)
SELECT (SELECT COUNT(*) FROM tmp_crews)         AS crews,
       (SELECT COUNT(*) FROM tmp_challenges)    AS challenges,
       (SELECT COUNT(*) FROM tmp_verifications) AS verifications,
       (SELECT COUNT(*) FROM tmp_sessions)      AS sessions;

-- 1) reviews → 2) reports → 3) reactions
DELETE FROM reviews   WHERE report_id IN (SELECT id FROM reports WHERE verification_id IN (SELECT id FROM tmp_verifications));
DELETE FROM reports   WHERE verification_id IN (SELECT id FROM tmp_verifications);
DELETE FROM reactions WHERE verification_id IN (SELECT id FROM tmp_verifications);

-- 4) dead_letters (검증 매핑; CREW_START_NOTIFICATION/REMINDER는 userId라 제외)
DELETE FROM dead_letters
WHERE (task_type IN ('CREW_ACTIVATE','CREW_COMPLETE') AND target_id IN (SELECT id FROM tmp_crews))
   OR (task_type = 'CHALLENGE_FAIL'                   AND target_id IN (SELECT id FROM tmp_challenges))
   OR (task_type = 'SESSION_EXPIRE'                   AND target_id IN (SELECT CAST(id AS TEXT) FROM tmp_sessions));

-- 5) verifications → 6) challenges → 7) upload_session(행 삭제)
DELETE FROM verifications  WHERE id IN (SELECT id FROM tmp_verifications);
DELETE FROM challenges     WHERE id IN (SELECT id FROM tmp_challenges);
DELETE FROM upload_session WHERE id IN (SELECT id FROM tmp_sessions);

-- 8) crew_members → 9) crews
DELETE FROM crew_members WHERE crew_id IN (SELECT id FROM tmp_crews);
DELETE FROM crews        WHERE id IN (SELECT id FROM tmp_crews);

-- 10) notifications (날짜 독립, 크루 무관)
DELETE FROM notifications WHERE created_at < TIMESTAMP '2026-06-01 00:00:00';

-- 사후 정합성 (기대: 앞 5개 전부 0, users=16)
SELECT
  (SELECT COUNT(*) FROM crews WHERE created_at < TIMESTAMP '2026-05-01 00:00:00')                AS crews_left,
  (SELECT COUNT(*) FROM challenges    WHERE crew_id NOT IN (SELECT id FROM crews))               AS orphan_challenges,
  (SELECT COUNT(*) FROM verifications WHERE crew_id NOT IN (SELECT id FROM crews))               AS orphan_verifications,
  (SELECT COUNT(*) FROM reactions WHERE verification_id NOT IN (SELECT id FROM verifications))   AS orphan_reactions,
  (SELECT COUNT(*) FROM notifications WHERE created_at < TIMESTAMP '2026-06-01 00:00:00')        AS notif_left,
  (SELECT COUNT(*) FROM users)                                                                   AS users_unchanged;

-- dry-run: 되돌림. 카운트 OK면 이 줄을 COMMIT; 으로 바꿔 재실행.
ROLLBACK;
```

### 4-B. S3 객체 삭제 (DB COMMIT 후, 트랜잭션 밖)
```bash
aws s3api delete-objects --bucket triagain-verifications --region ap-northeast-2 --delete '{
  "Objects": [
    {"Key": "upload-sessions/4777253873/6160f4b3-c922-4600-afbc-cc42a7a834a9.jpg"},
    {"Key": "upload-sessions/4777253873/10dfe08a-70c5-4f86-8ac2-955598a51d53.jpg"},
    {"Key": "upload-sessions/4777253873/3dac3b12-192c-436e-a850-4c0e940f08f4.jpg"},
    {"Key": "upload-sessions/4777253873/c4819cdc-11dd-43fc-917f-717489c4a470.jpg"},
    {"Key": "upload-sessions/4799603838/706d3059-5b89-4d53-9831-16d66e8fa2b1.jpg"},
    {"Key": "upload-sessions/4777253873/9f78886b-e2aa-432a-9669-7d6c07b2f235.jpg"},
    {"Key": "upload-sessions/4777253873/02021494-589a-4f44-8e05-57c2b3186b27.jpg"},
    {"Key": "upload-sessions/4777253873/a0c431b1-0ecd-4756-bcb6-0d99353b1f7e.jpg"},
    {"Key": "upload-sessions/4813486721/fe756be0-5147-4f40-a2a6-f69fe96b70de.jpg"},
    {"Key": "upload-sessions/4777253873/ca07bde1-2b2c-4278-99a5-0024fb41beb2.jpg"},
    {"Key": "upload-sessions/001784.877c18f0c38f4bb5bfee327e404f1bd3.0735/29066149-048f-4e25-98ab-74ec7d4c05c7.jpg"},
    {"Key": "upload-sessions/4777253873/93100e2d-af06-459f-97cf-a301e19c9a46.jpg"}
  ],
  "Quiet": false
}'
```
> `delete-objects`는 멱등 — 이미 없는 키도 에러 없음. 응답 `Deleted` 12개 확인.

### 4-C. 사후 확인
```bash
# 샘플 1개 → "Not Found"(404)면 삭제 정상
aws s3api head-object --bucket triagain-verifications --region ap-northeast-2 \
  --key "upload-sessions/4777253873/6160f4b3-c922-4600-afbc-cc42a7a834a9.jpg"
```
- DB: 4-A 사후 정합성 쿼리에서 `crews_left=0 / orphan_*=0 / notif_left=0 / users_unchanged=16`.

> 산출물 메모: 위 ④/⑤ 진단 결과는 home-crew-tabs SDD step3 §5 "진단 결과 요약 → 사용자 보고"의 입력으로도 사용. 깨진 크루 `사부작`은 이 삭제로 제거되므로 SDD §3 보정 불필요 → §4 CHECK 제약만 남음.
