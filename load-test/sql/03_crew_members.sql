-- ============================================================
-- 부하테스트 크루 멤버 매핑 (유저 5명씩 크루에 배정)
-- 사용법: psql -v scale=S -f 03_crew_members.sql
--   scale: S(10크루×5명) / M(50×5) / L(200×5) / XL(500×5)
-- 선행: 01_users.sql, 02_crews.sql 실행 완료
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
    v_role TEXT;
BEGIN
    CASE v_scale
        WHEN 'S'  THEN v_crew_count := 10;   v_user_count := 50;
        WHEN 'M'  THEN v_crew_count := 50;   v_user_count := 250;
        WHEN 'L'  THEN v_crew_count := 200;  v_user_count := 1000;
        WHEN 'XL' THEN v_crew_count := 500;  v_user_count := 2500;
        ELSE RAISE EXCEPTION 'Unknown scale: %. Use S/M/L/XL', v_scale;
    END CASE;

    RAISE NOTICE '[03_crew_members] scale=%, % crews x % members', v_scale, v_crew_count, v_members_per_crew;

    FOR i IN 1..v_crew_count LOOP
        v_crew_id := 'loadtest-crew-' || i;

        FOR j IN 1..v_members_per_crew LOOP
            -- 유저를 순환 배정: 크루1 → 유저1~5, 크루2 → 유저6~10, ...
            v_user_idx := ((i - 1) * v_members_per_crew + j - 1) % v_user_count + 1;
            v_user_id := 'loadtest-user-' || v_user_idx;
            -- 첫 번째 멤버(=크루장)
            v_role := CASE WHEN j = 1 THEN 'LEADER' ELSE 'MEMBER' END;

            INSERT INTO crew_members (id, user_id, crew_id, role, joined_at)
            VALUES (
                gen_random_uuid()::text,
                v_user_id,
                v_crew_id,
                v_role,
                NOW()
            )
            ON CONFLICT DO NOTHING;
        END LOOP;

        -- current_members 갱신
        UPDATE crews
        SET current_members = v_members_per_crew
        WHERE id = v_crew_id;
    END LOOP;

    RAISE NOTICE '[03_crew_members] done.';
END $$;

-- 결과 확인
SELECT count(*) AS member_count FROM crew_members WHERE user_id LIKE 'loadtest-%';
