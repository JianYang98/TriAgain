-- ============================================================
-- 부하테스트 유저 생성
-- 사용법: psql -v scale=S -f 01_users.sql
--   scale: S(50명) / M(250명) / L(1000명) / XL(2500명) / XXL(10000명)
-- ============================================================

DO $$
DECLARE
    v_scale TEXT := coalesce(current_setting('app.scale', true), 'S');
    v_count INT;
    i INT;
BEGIN
    -- 단계별 유저 수 결정
    CASE v_scale
        WHEN 'S'   THEN v_count := 50;
        WHEN 'M'   THEN v_count := 250;
        WHEN 'L'   THEN v_count := 1000;
        WHEN 'XL'  THEN v_count := 2500;
        WHEN 'XXL' THEN v_count := 10000;
        ELSE RAISE EXCEPTION 'Unknown scale: %. Use S/M/L/XL/XXL', v_scale;
    END CASE;

    RAISE NOTICE '[01_users] scale=%, creating % users', v_scale, v_count;

    FOR i IN 1..v_count LOOP
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

    RAISE NOTICE '[01_users] done. % users created', v_count;
END $$;

-- 결과 확인
SELECT count(*) AS user_count FROM users WHERE id LIKE 'loadtest-%';
