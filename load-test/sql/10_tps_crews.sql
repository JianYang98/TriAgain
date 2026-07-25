-- ============================================================
-- 크루 참여 TPS 측정 전용 시드 (유저 3,000 + 크루 CASE A=6,000 / B=800)
-- 지시서: CREW-JOIN-TPS-락전략3종-작업지시서.md §4-1
--
-- 사용법:
--   psql "$TEST_DB" -v ON_ERROR_STOP=1 -c "SET app.tps_case='A';" -f sql/10_tps_crews.sql
--   psql "$TEST_DB" -v ON_ERROR_STOP=1 -c "SET app.tps_case='B';" -f sql/10_tps_crews.sql
--   (선택 오버라이드) -c "SET app.crew_count='1000';" / -c "SET app.user_count='3000';"
--   ⚠️ 파라미터명이 app.case가 아닌 이유: `case`는 SQL 예약어라 SET 구문에서 문법 오류
--      (로컬 postgres:16 실증에서 확인 — app.scale 같은 비예약어와 다름)
--
-- 파라미터:
--   app.tps_case   : A(기본, 크루 6,000) | B(크루 800)
--   app.crew_count : 케이스 기본값 오버라이드 (최대 9999 — invite_code TP#### 4자리 한계)
--   app.user_count : 기본 3000 (근거: W×K < 유저수 — CASE B의 20×100=2,000까지 만족)
--
-- 유저 3,000명 근거로 01_users.sql을 안 쓰는 이유: scale 매핑(S~XXL)에 3000이 없음.
--   동일 네임스페이스(loadtest-user-N)를 인라인 생성 — generate-tokens.sh와 정합.
-- 크루는 07_rush_crews.sql 패턴 준수. 🚨 joinable 조건 필수:
--   isJoinableStatus() = RECRUITING OR (ACTIVE && allow_late_join)
--   + visibility='PUBLIC' (OPTIMISTIC 경로가 isPublic() 검사)
--   ⚠️ 02_crews.sql 패턴(allow_late_join=FALSE) 사용 금지 — 전량 CR003으로 죽음
-- 멱등: ON CONFLICT (id) DO UPDATE — 재실행 안전, 리셋 겸용(단 런 간 정식 리셋은 10_tps_reset.sql)
-- ============================================================

DO $$
DECLARE
    v_case       TEXT := coalesce(nullif(current_setting('app.tps_case', true), ''), 'A');
    v_user_count INT  := coalesce(nullif(current_setting('app.user_count', true), '')::int, 3000);
    v_crew_count INT;
    i INT;
    v_crew_id TEXT;
    v_categories TEXT[] := ARRAY['EXERCISE', 'STUDY', 'LIFESTYLE', 'SELF_DEV', 'ETC'];
BEGIN
    IF v_case NOT IN ('A', 'B') THEN
        RAISE EXCEPTION 'Unknown app.tps_case: %. Use A or B', v_case;
    END IF;

    v_crew_count := coalesce(
        nullif(current_setting('app.crew_count', true), '')::int,
        CASE v_case WHEN 'B' THEN 800 ELSE 6000 END
    );

    IF v_crew_count > 9999 THEN
        RAISE EXCEPTION 'crew_count(%) > 9999 — invite_code TP#### 4자리 오버플로', v_crew_count;
    END IF;

    -- [1] 유저 (01_users.sql 패턴 인라인, 기존 loadtest-user-N 네임스페이스 재사용)
    RAISE NOTICE '[10_tps_crews] users: ensuring % (ON CONFLICT DO NOTHING — 기존 시드 보존)', v_user_count;
    FOR i IN 1..v_user_count LOOP
        INSERT INTO users (id, provider, nickname, profile_image_url, fcm_token, token_version, created_at)
        VALUES (
            'loadtest-user-' || i,
            'KAKAO',
            '부하유저' || i,
            NULL,
            'fake-fcm-token-' || i,
            0,
            NOW()
        )
        ON CONFLICT (id) DO NOTHING;
    END LOOP;

    -- [2] 크루 (07_rush_crews.sql 패턴 — 신규 네임스페이스 loadtest-tps-crew-N)
    RAISE NOTICE '[10_tps_crews] case=%, creating % crews (loadtest-tps-crew-1..%)', v_case, v_crew_count, v_crew_count;
    FOR i IN 1..v_crew_count LOOP
        v_crew_id := 'loadtest-tps-crew-' || i;

        INSERT INTO crews (
            id, creator_id, name, goal, verification_content,
            verification_type, min_members, max_members, current_members,
            status, start_date, end_date, allow_late_join,
            invite_code, created_at, deadline_time, category, visibility
        ) VALUES (
            v_crew_id,
            'loadtest-user-1',                 -- 07_rush_crews.sql 패턴대로 고정
            'TPS크루' || i,
            '크루 참여 TPS 측정용',
            'TPS 테스트 인증내용',
            'TEXT',
            1,
            10,                                -- 정원: 실서비스와 동일 max_members=10 고정(§4-1)
            0,
            'ACTIVE',
            CURRENT_DATE - 1,
            CURRENT_DATE + 5,
            TRUE,                              -- 🚨 이게 빠지면 전량 CR003 (07_rush_reset.sql 함정 주석 참조)
            'TP' || lpad(i::text, 4, '0'),     -- TP0001~TP9999 — 기존 LT####/RS####와 충돌 없음
            NOW(),
            '23:59:59',
            v_categories[1 + (i % 5)],
            'PUBLIC'                           -- 🚨 OPTIMISTIC 경로 isPublic() 검사
        )
        -- 07_rush 패턴 대비 status·visibility 리셋 추가: ENDED 등으로 꼬인 크루가
        -- 재시드로 안 풀리는 CR003 함정 방어 (조인 가능 조건 3요소 전부 복구)
        ON CONFLICT (id) DO UPDATE SET
            status          = 'ACTIVE',
            allow_late_join = TRUE,
            current_members = 0,
            start_date      = CURRENT_DATE - 1,
            end_date        = CURRENT_DATE + 5,
            visibility      = 'PUBLIC';
    END LOOP;

    RAISE NOTICE '[10_tps_crews] done. case=% crews=%', v_case, v_crew_count;
END $$;

-- ===== 결과 확인 (DoD 검증 쿼리 — 지시서 §7 요구 형식 그대로) =====
SELECT status, allow_late_join, current_members, max_members
FROM crews WHERE id LIKE 'loadtest-tps-crew-%' LIMIT 5;

SELECT count(*) AS tps_crew_count FROM crews WHERE id LIKE 'loadtest-tps-crew-%';
SELECT count(*) AS joinable_count FROM crews
WHERE id LIKE 'loadtest-tps-crew-%' AND status = 'ACTIVE' AND allow_late_join = TRUE;
SELECT count(*) AS loadtest_user_count FROM users WHERE id LIKE 'loadtest-user-%';
