-- ============================================================
-- API 부하테스트용 챌린지 생성
-- 각 유저-크루 조합마다 IN_PROGRESS 챌린지 1개 (오늘 마감, 미인증)
-- → POST /verifications 성공 가능
-- 사용법: psql -v scale=S -f 04_challenges_api.sql
-- 선행: 01~03 실행 완료
-- ============================================================

DO $$
DECLARE
    v_scale TEXT := coalesce(current_setting('app.scale', true), 'S');
    v_crew_count INT;
    v_user_count INT;
    v_members_per_crew INT := 5;
    i INT;
    j INT;
    v_crew_id TEXT;
    v_user_id TEXT;
    v_user_idx INT;
    v_created INT := 0;
BEGIN
    CASE v_scale
        WHEN 'S'  THEN v_crew_count := 10;   v_user_count := 50;
        WHEN 'M'  THEN v_crew_count := 50;   v_user_count := 250;
        WHEN 'L'  THEN v_crew_count := 200;  v_user_count := 1000;
        WHEN 'XL' THEN v_crew_count := 500;  v_user_count := 2500;
        ELSE RAISE EXCEPTION 'Unknown scale: %. Use S/M/L/XL', v_scale;
    END CASE;

    RAISE NOTICE '[04_challenges_api] scale=%, creating challenges for API test', v_scale;

    FOR i IN 1..v_crew_count LOOP
        v_crew_id := 'loadtest-crew-' || i;

        FOR j IN 1..v_members_per_crew LOOP
            v_user_idx := ((i - 1) * v_members_per_crew + j - 1) % v_user_count + 1;
            v_user_id := 'loadtest-user-' || v_user_idx;

            INSERT INTO challenges (
                id, user_id, crew_id, cycle_number, target_days,
                completed_days, status, start_date, deadline, created_at
            ) VALUES (
                gen_random_uuid()::text,
                v_user_id,
                v_crew_id,
                1,
                3,
                0,
                'IN_PROGRESS',
                CURRENT_DATE,
                -- 오늘 23:59:59 → 아직 마감 안 됨 → API 인증 가능
                CURRENT_DATE + TIME '23:59:59',
                NOW()
            )
            ON CONFLICT DO NOTHING;

            v_created := v_created + 1;
        END LOOP;
    END LOOP;

    RAISE NOTICE '[04_challenges_api] done. % challenges created', v_created;
END $$;

-- 결과 확인
SELECT count(*) AS api_challenge_count
FROM challenges c
JOIN users u ON u.id = c.user_id
WHERE u.id LIKE 'loadtest-%'
  AND c.status = 'IN_PROGRESS'
  AND c.deadline >= NOW();
