-- ============================================================
-- 부하테스트 알림 데이터 생성
-- GET /notifications 테스트용
-- 사용법: psql -v scale=S -f 06_notifications.sql
-- 선행: 01_users.sql 실행 완료
-- ============================================================

DO $$
DECLARE
    v_scale TEXT := coalesce(current_setting('app.scale', true), 'S');
    v_user_count INT;
    v_notif_per_user INT := 10;
    i INT;
    j INT;
    v_user_id TEXT;
    v_types TEXT[] := ARRAY[
        'REMINDER', 'CREW_STARTED', 'CHALLENGE_SUCCESS',
        'CHALLENGE_FAILED', 'VERIFICATION_APPROVED'
    ];
    v_created INT := 0;
BEGIN
    CASE v_scale
        WHEN 'S'  THEN v_user_count := 50;
        WHEN 'M'  THEN v_user_count := 250;
        WHEN 'L'  THEN v_user_count := 1000;
        WHEN 'XL' THEN v_user_count := 2500;
        ELSE RAISE EXCEPTION 'Unknown scale: %. Use S/M/L/XL', v_scale;
    END CASE;

    RAISE NOTICE '[06_notifications] scale=%, % users x % notifications', v_scale, v_user_count, v_notif_per_user;

    FOR i IN 1..v_user_count LOOP
        v_user_id := 'loadtest-user-' || i;

        FOR j IN 1..v_notif_per_user LOOP
            INSERT INTO notifications (
                id, user_id, type, title, content,
                is_read, target_type, target_id, created_at
            ) VALUES (
                gen_random_uuid()::text,
                v_user_id,
                v_types[1 + (j % 5)],
                '부하테스트 알림 ' || j,
                '부하테스트 알림 내용입니다. #' || j,
                FALSE,
                'CREW',
                'loadtest-crew-' || (1 + (i % 10)),
                NOW() - (j || ' minutes')::interval
            );

            v_created := v_created + 1;
        END LOOP;
    END LOOP;

    RAISE NOTICE '[06_notifications] done. % notifications created', v_created;
END $$;

-- 결과 확인
SELECT count(*) AS notification_count
FROM notifications WHERE user_id LIKE 'loadtest-%';
