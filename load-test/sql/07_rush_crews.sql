-- ============================================================
-- 시나리오 D (크루 참가 러시) 전용 크루 생성
-- k6 crew-rush.js에서 50 VU가 정원 10명 크루에 동시 참가 시도
--
-- 기존 크루와의 차이: allow_late_join=TRUE, current_members=0
-- 선행: 01_users.sql 실행 완료
-- ============================================================

DO $$
DECLARE
    v_rush_count INT := 10;
    i INT;
    v_crew_id TEXT;
    v_categories TEXT[] := ARRAY['EXERCISE', 'STUDY', 'LIFESTYLE', 'SELF_DEV', 'ETC'];
    v_created INT := 0;
BEGIN
    RAISE NOTICE '[07_rush_crews] creating % rush crews', v_rush_count;

    FOR i IN 1..v_rush_count LOOP
        v_crew_id := 'loadtest-rush-crew-' || i;

        INSERT INTO crews (
            id, creator_id, name, goal, verification_content,
            verification_type, min_members, max_members, current_members,
            status, start_date, end_date, allow_late_join,
            invite_code, created_at, deadline_time, category, visibility
        ) VALUES (
            v_crew_id,
            'loadtest-user-1',
            '러시크루' || i,
            '크루 참가 러시 테스트용',
            '러시 테스트 인증내용',
            'TEXT',
            1,
            10,
            0,
            'ACTIVE',
            CURRENT_DATE - 1,
            CURRENT_DATE + 5,
            TRUE,
            'RS' || lpad(i::text, 4, '0'),
            NOW(),
            '23:59:59',
            v_categories[1 + (i % 5)],
            'PUBLIC'
        )
        ON CONFLICT (id) DO UPDATE SET
            current_members = 0,
            allow_late_join = TRUE,
            start_date = CURRENT_DATE - 1,
            end_date = CURRENT_DATE + 5;

        v_created := v_created + 1;
    END LOOP;

    RAISE NOTICE '[07_rush_crews] done. % rush crews created', v_created;
END $$;

-- 결과 확인
SELECT id, current_members, max_members, allow_late_join
FROM crews WHERE id LIKE 'loadtest-rush-crew-%'
ORDER BY id;
