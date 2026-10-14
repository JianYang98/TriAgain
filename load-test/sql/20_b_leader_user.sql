-- ============================================================
-- B 테스트 크루장 사용자 시드 (멱등)
-- 사용법: psql -X -v ON_ERROR_STOP=1 -f 20_b_leader_user.sql
-- 선행: 스키마. 사용자 풀(loadtest-user-1…10000)은 01_users.sql(app.scale='XXL')이 만든다.
-- 크루장은 풀 밖 이름이어야 한다 — 풀 안이면 그 사용자의 가입 시도가 CR004가 되어
-- "성공 999 · CR004=0"이 깨진다 (step0 §3-8).
-- ============================================================
\set ON_ERROR_STOP on

INSERT INTO users (id, provider, nickname, profile_image_url, fcm_token, token_version, created_at)
VALUES ('loadtest-leader', 'KAKAO', '부하크루장', NULL, NULL, 0, NOW())
ON CONFLICT (id) DO NOTHING;

SELECT id, provider, nickname, token_version, deleted_at IS NULL AS active
FROM users WHERE id = 'loadtest-leader';
