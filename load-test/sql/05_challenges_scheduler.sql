-- ============================================================
-- 스케줄러 부하테스트용 챌린지 생성
-- deadline이 과거인 IN_PROGRESS 챌린지 → FailExpiredChallengesScheduler 대상
-- 별도 크루/유저 사용 (API 테스트 데이터와 충돌 방지)
-- 사용법: psql -v scale=S -f 05_challenges_scheduler.sql
-- 선행: 01_users.sql 실행 완료
-- ============================================================

DO $$
DECLARE
    v_scale TEXT := coalesce(current_setting('app.scale', true), 'S');
    v_challenge_count INT;
    v_sched_crew_count INT;
    v_user_count INT;
    i INT;
    j INT;
    v_crew_id TEXT;
    v_user_id TEXT;
    v_members_per_crew INT := 5;
    v_created INT := 0;
BEGIN
    -- 스케줄러 테스트용 챌린지 수 (지시서 기준)
    CASE v_scale
        WHEN 'S'  THEN v_challenge_count := 50;    v_user_count := 50;
        WHEN 'M'  THEN v_challenge_count := 250;   v_user_count := 250;
        WHEN 'L'  THEN v_challenge_count := 1000;  v_user_count := 1000;
        WHEN 'XL' THEN v_challenge_count := 2500;  v_user_count := 2500;
        ELSE RAISE EXCEPTION 'Unknown scale: %. Use S/M/L/XL', v_scale;
    END CASE;

    v_sched_crew_count := v_challenge_count / v_members_per_crew;

    RAISE NOTICE '[05_challenges_scheduler] scale=%, creating % scheduler crews + % challenges',
        v_scale, v_sched_crew_count, v_challenge_count;

    -- 스케줄러 전용 크루 생성 (loadtest-sched-crew-N)
    FOR i IN 1..v_sched_crew_count LOOP
        v_crew_id := 'loadtest-sched-crew-' || i;
        v_user_id := 'loadtest-user-' || ((i - 1) % v_user_count + 1);

        INSERT INTO crews (
            id, creator_id, name, goal, verification_content,
            verification_type, min_members, max_members, current_members,
            status, start_date, end_date, allow_late_join,
            invite_code, created_at, deadline_time, category, visibility
        ) VALUES (
            v_crew_id,
            v_user_id,
            '스케줄러크루' || i,
            '스케줄러 테스트',
            '스케줄러 인증내용',
            'TEXT',
            1,
            10,
            v_members_per_crew,
            'ACTIVE',
            CURRENT_DATE - 3,
            CURRENT_DATE + 3,
            FALSE,
            'SC' || lpad(i::text, 4, '0'),
            NOW(),
            '23:59:59',
            'ETC',
            'PUBLIC'
        )
        ON CONFLICT (id) DO NOTHING;

        -- 멤버 매핑 + 마감 지난 챌린지 생성
        FOR j IN 1..v_members_per_crew LOOP
            v_user_id := 'loadtest-user-' || (((i - 1) * v_members_per_crew + j - 1) % v_user_count + 1);

            -- 멤버 등록
            INSERT INTO crew_members (id, user_id, crew_id, role, joined_at)
            VALUES (
                gen_random_uuid()::text,
                v_user_id,
                v_crew_id,
                CASE WHEN j = 1 THEN 'LEADER' ELSE 'MEMBER' END,
                NOW()
            )
            ON CONFLICT DO NOTHING;

            -- 마감 지난 IN_PROGRESS 챌린지 (어제 23:59:59)
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
                CURRENT_DATE - 2,
                -- 어제 23:59:59 → 이미 마감 → 스케줄러가 FAIL 처리할 대상
                (CURRENT_DATE - INTERVAL '1 day') + TIME '23:59:59',
                NOW()
            )
            ON CONFLICT DO NOTHING;

            v_created := v_created + 1;
        END LOOP;
    END LOOP;

    RAISE NOTICE '[05_challenges_scheduler] done. % challenges created', v_created;
END $$;

-- 결과 확인
SELECT count(*) AS scheduler_challenge_count
FROM challenges c
WHERE c.crew_id LIKE 'loadtest-sched-%'
  AND c.status = 'IN_PROGRESS'
  AND c.deadline < NOW();
