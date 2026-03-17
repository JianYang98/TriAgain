# 테스트 크루 삭제 쿼리

대상 crew_id: `CREW-0778891ff7674cf3`, `CREW-90f3e95011c5482f`

---

## 1. SELECT — 삭제 대상 데이터 확인

```sql
-- 대상 크루
SELECT id, name, status, current_members, created_at
FROM crews
WHERE id IN ('CREW-0778891ff7674cf3', 'CREW-90f3e95011c5482f');

-- 크루 멤버
SELECT * FROM crew_members
WHERE crew_id IN ('CREW-0778891ff7674cf3', 'CREW-90f3e95011c5482f');

-- 챌린지
SELECT * FROM challenges
WHERE crew_id IN ('CREW-0778891ff7674cf3', 'CREW-90f3e95011c5482f');

-- 인증 (verifications에 crew_id 직접 존재)
SELECT * FROM verifications
WHERE crew_id IN ('CREW-0778891ff7674cf3', 'CREW-90f3e95011c5482f');

-- 업로드 세션 (upload_session에 crew_id 직접 존재)
SELECT * FROM upload_session
WHERE crew_id IN ('CREW-0778891ff7674cf3', 'CREW-90f3e95011c5482f');

-- 신고 (verifications → reports)
SELECT r.* FROM reports r
JOIN verifications v ON r.verification_id = v.id
WHERE v.crew_id IN ('CREW-0778891ff7674cf3', 'CREW-90f3e95011c5482f');

-- 검토 (reports → reviews)
SELECT rv.* FROM reviews rv
JOIN reports r ON rv.report_id = r.id
JOIN verifications v ON r.verification_id = v.id
WHERE v.crew_id IN ('CREW-0778891ff7674cf3', 'CREW-90f3e95011c5482f');

-- 반응 (verifications → reactions)
SELECT rc.* FROM reactions rc
JOIN verifications v ON rc.verification_id = v.id
WHERE v.crew_id IN ('CREW-0778891ff7674cf3', 'CREW-90f3e95011c5482f');
```

---

## 2. DELETE — 트랜잭션 (leaf → root 순서)

```sql
BEGIN;

-- 1. reviews (report → verification 체인 끝)
DELETE FROM reviews
WHERE report_id IN (
    SELECT r.id FROM reports r
    JOIN verifications v ON r.verification_id = v.id
    WHERE v.crew_id IN ('CREW-0778891ff7674cf3', 'CREW-90f3e95011c5482f')
);

-- 2. reactions
DELETE FROM reactions
WHERE verification_id IN (
    SELECT id FROM verifications
    WHERE crew_id IN ('CREW-0778891ff7674cf3', 'CREW-90f3e95011c5482f')
);

-- 3. reports
DELETE FROM reports
WHERE verification_id IN (
    SELECT id FROM verifications
    WHERE crew_id IN ('CREW-0778891ff7674cf3', 'CREW-90f3e95011c5482f')
);

-- 4. verifications (upload_session_id FK 해제 후 upload_session 삭제 가능)
DELETE FROM verifications
WHERE crew_id IN ('CREW-0778891ff7674cf3', 'CREW-90f3e95011c5482f');

-- 5. upload_session (verifications 삭제 후)
DELETE FROM upload_session
WHERE crew_id IN ('CREW-0778891ff7674cf3', 'CREW-90f3e95011c5482f');

-- 6. challenges
DELETE FROM challenges
WHERE crew_id IN ('CREW-0778891ff7674cf3', 'CREW-90f3e95011c5482f');

-- 7. crew_members
DELETE FROM crew_members
WHERE crew_id IN ('CREW-0778891ff7674cf3', 'CREW-90f3e95011c5482f');

-- 8. crews (root)
DELETE FROM crews
WHERE id IN ('CREW-0778891ff7674cf3', 'CREW-90f3e95011c5482f');

-- 이상 없으면 COMMIT, 의심스러우면 ROLLBACK
COMMIT;
-- ROLLBACK;
```

---

## 주의사항

- `notifications` 테이블은 `crew_id` FK가 없고 `user_id`만 있어서 이번 삭제 범위에 포함되지 않음. 크루 관련 알림을 정리하려면 `type`과 `content`로 별도 확인 후 수동 삭제.
- DELETE 전에 SELECT로 건수 먼저 확인하고, 특히 `verifications`와 `challenges` 건수가 예상과 다르면 `ROLLBACK`.