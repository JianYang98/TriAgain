-- ============================================================
-- 부하테스트 크루 생성
-- 사용법: psql -v scale=S -f 02_crews.sql
--   scale: S(10개) / M(50개) / L(200개) / XL(500개)
-- 선행: 01_users.sql 실행 완료
-- ============================================================

DO $$
DECLARE
    v_scale TEXT := coalesce(current_setting('app.scale', true), 'S');
    v_crew_count INT;
    v_user_count INT;
    i INT;
    v_crew_id TEXT;
    v_creator_id TEXT;
    v_vtype TEXT;
    v_category TEXT;
    v_categories TEXT[] := ARRAY['EXERCISE', 'STUDY', 'LIFESTYLE', 'SELF_DEV', 'ETC'];
BEGIN
    CASE v_scale
        WHEN 'S'  THEN v_crew_count := 10;   v_user_count := 50;
        WHEN 'M'  THEN v_crew_count := 50;   v_user_count := 250;
        WHEN 'L'  THEN v_crew_count := 200;  v_user_count := 1000;
        WHEN 'XL' THEN v_crew_count := 500;  v_user_count := 2500;
        ELSE RAISE EXCEPTION 'Unknown scale: %. Use S/M/L/XL', v_scale;
    END CASE;

    RAISE NOTICE '[02_crews] scale=%, creating % crews', v_scale, v_crew_count;

    FOR i IN 1..v_crew_count LOOP
        v_crew_id := 'loadtest-crew-' || i;
        -- 크루장은 유저를 순환 배정 (1번 유저 → 크루1, 2번 유저 → 크루2, ...)
        v_creator_id := 'loadtest-user-' || ((i - 1) % v_user_count + 1);
        -- 80% TEXT, 20% PHOTO
        v_vtype := CASE WHEN i % 5 = 0 THEN 'PHOTO' ELSE 'TEXT' END;
        -- 카테고리 랜덤 분배
        v_category := v_categories[1 + (i % 5)];

        INSERT INTO crews (
            id, creator_id, name, goal, verification_content,
            verification_type, min_members, max_members, current_members,
            status, start_date, end_date, allow_late_join,
            invite_code, created_at, deadline_time, category, visibility
        ) VALUES (
            v_crew_id,
            v_creator_id,
            '부하크루' || i,
            '부하테스트 목표 ' || i,
            '부하테스트 인증내용',
            v_vtype,
            1,
            10,
            0,  -- crew_members 삽입 후 UPDATE
            'ACTIVE',
            CURRENT_DATE - 1,
            CURRENT_DATE + 5,
            FALSE,
            'LT' || lpad(i::text, 4, '0'),  -- LT0001 ~ LT9999
            NOW(),
            '23:59:59',
            v_category,
            'PUBLIC'
        )
        ON CONFLICT (id) DO NOTHING;
    END LOOP;

    RAISE NOTICE '[02_crews] done. % crews created', v_crew_count;
END $$;

-- 결과 확인
SELECT count(*) AS crew_count FROM crews WHERE id LIKE 'loadtest-%';
